import http from "node:http";
import https from "node:https";
import { readFile, stat } from "node:fs/promises";
import { resolve, extname, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";

// Finite test harness: serve this worktree's production build and proxy real APIs.
// No dev server, fabricated responses, credential files, or session artifacts.
const root = resolve(
  fileURLToPath(new URL("../../frontend/dist/", import.meta.url)),
);
const target = (() => {
  try {
    return new URL(process.env.SICOST_VERIFY_API ?? "");
  } catch {
    throw new Error("HTTP API target required; environment value withheld");
  }
})();
if (!["http:", "https:"].includes(target.protocol))
  throw new Error("HTTP API target required");
await stat(resolve(root, "index.html"));
const mime = {
  ".html": "text/html",
  ".js": "text/javascript",
  ".css": "text/css",
  ".woff2": "font/woff2",
  ".svg": "image/svg+xml",
};
const server = http.createServer(async (request, response) => {
  if (request.url.startsWith("/api/")) {
    const headers = { ...request.headers, host: target.host };
    if (headers.origin) headers.origin = target.origin;
    if (headers.referer)
      headers.referer = target.origin + new URL(headers.referer).pathname;
    const outgoing = (target.protocol === "https:" ? https : http).request(
      new URL(request.url, target),
      {
        method: request.method,
        headers,
        timeout: 45000,
        agent: false,
      },
      (upstream) => {
        const outputHeaders = { ...upstream.headers };
        if (outputHeaders["set-cookie"])
          outputHeaders["set-cookie"] = outputHeaders["set-cookie"].map(
            (cookie) => cookie.replace(/;\s*Domain=[^;]+/gi, ""),
          );
        response.writeHead(upstream.statusCode, outputHeaders);
        upstream.pipe(response);
      },
    );
    outgoing.on("timeout", () => outgoing.destroy());
    response.on("close", () => outgoing.destroy());
    outgoing.on("error", () => {
      if (!response.headersSent) response.writeHead(502);
      response.end();
    });
    request.pipe(outgoing);
    return;
  }
  try {
    let path = resolve(
      root,
      "." +
        decodeURIComponent(
          new URL(request.url, "http://test.invalid").pathname,
        ),
    );
    if (path !== root && !path.startsWith(root + sep)) {
      response.writeHead(403);
      response.end();
      return;
    }
    if (!extname(path)) path = resolve(root, "index.html");
    const content = await readFile(path);
    response.writeHead(200, {
      "Content-Type": mime[extname(path)] ?? "application/octet-stream",
      "Cache-Control": "no-store",
    });
    response.end(content);
  } catch {
    response.writeHead(404);
    response.end();
  }
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const child = spawn(
  "npm",
  ["test", "--", ...process.argv.slice(2), "--reporter=json"],
  {
    cwd: fileURLToPath(new URL("../", import.meta.url)),
    // Reporters can include response bodies, account fields and DOM on failure.
    // Keep their output in memory only and publish numeric totals, never raw output.
    stdio: ["ignore", "pipe", "pipe"],
    env: {
      ...process.env,
      PLAYWRIGHT_NO_COPY_PROMPT: "1",
      SICOST_E2E_BASE_URL: `http://127.0.0.1:${server.address().port}`,
    },
  },
);
let output = "";
child.stdout.setEncoding("utf8");
child.stdout.on("data", (chunk) => {
  output += chunk;
  if (output.length > 20 * 1024 * 1024) child.kill("SIGTERM");
});
child.stderr.resume();
process.on("SIGTERM", () => child.kill("SIGTERM"));
process.on("SIGINT", () => child.kill("SIGINT"));
const code = await new Promise((resolve) =>
  child.on("close", (code) => resolve(code ?? 1)).on("error", () => resolve(1)),
);
server.closeAllConnections();
await new Promise((resolve) => server.close(resolve));
try {
  const report = JSON.parse(output.slice(output.indexOf("{")));
  const { stats } = report;
  const keys = ["expected", "unexpected", "flaky", "skipped"];
  if (
    !stats ||
    keys.some((key) => !Number.isInteger(stats[key]) || stats[key] < 0)
  )
    throw new Error("Invalid test totals");
  const totals = Object.fromEntries(keys.map((key) => [key, stats[key]]));
  const failures = [];
  const collectFailures = (suites) => {
    for (const suite of suites ?? []) {
      for (const spec of suite.specs ?? []) {
        for (const test of spec.tests ?? []) {
          for (const result of test.results ?? []) {
            if (!["failed", "timedOut", "interrupted"].includes(result.status))
              continue;
            const messages = (result.errors ?? [])
              .map((error) => error.message ?? "")
              .join("\n");
            failures.push({
              line: spec.line,
              status: result.status,
              timeout: /timeout|timed out/i.test(messages),
              locations: [
                ...new Set(
                  messages.match(/dashboard-presentation\.spec\.ts:\d+:\d+/g) ??
                    [],
                ),
              ],
              matchers: [
                ...new Set(
                  messages.match(
                    /\bto(?:BeVisible|BeHidden|BeEnabled|BeDisabled|Be|Equal|HaveCount|HaveAttribute|HaveText|Contain|Match)\b/g,
                  ) ?? [],
                ),
              ],
            });
          }
        }
      }
      collectFailures(suite.suites);
    }
  };
  collectFailures(report.suites);
  console.log(
    JSON.stringify({
      phase: "real-browser",
      exit: code,
      ...totals,
      collectionErrors: report.errors?.length ?? 0,
      failures,
    }),
  );
  process.exitCode =
    code || (stats.unexpected || stats.flaky || !stats.expected ? 1 : 0);
} catch {
  console.error(
    "Browser verification failed: numeric totals unavailable; raw output withheld.",
  );
  process.exitCode = 1;
}
