import { test as base } from "@playwright/test";
import { account, csrf, loginUi } from "../basic147/authSupport";
export { expect } from "@playwright/test";
export type { Page, APIRequestContext, APIResponse } from "@playwright/test";

/** Legacy business assertions keep their data/semantics; setup now uses real login + ADMIN MFA. */
export const test = base.extend({
  page: async ({ page }, use) => {
    await loginUi(page, account("ADMIN"));
    // Old business tests predate cookie authentication. Add only the actual server's
    // current CSRF token to their API writes; never replace a caller-specified token.
    for (const method of ["post", "put", "patch", "delete"] as const) {
      const send = page.request[method].bind(page.request);
      page.request[method] = async (url, options) => {
        const token = await csrf(page.request),
          headers = options?.headers ?? {};
        if (
          Object.keys(headers).some(
            (name) => name.toLowerCase() === token.headerName.toLowerCase(),
          )
        )
          return send(url, options);
        return send(url, {
          ...options,
          headers: { ...headers, [token.headerName]: token.token },
        });
      };
    }
    const fetch = page.request.fetch.bind(page.request);
    page.request.fetch = async (url, options) => {
      const method = (
        options?.method ?? (typeof url === "string" ? "GET" : url.method())
      ).toUpperCase();
      if (["GET", "HEAD", "OPTIONS"].includes(method))
        return fetch(url, options);
      const token = await csrf(page.request),
        headers = options?.headers ?? {};
      if (
        Object.keys(headers).some(
          (name) => name.toLowerCase() === token.headerName.toLowerCase(),
        )
      )
        return fetch(url, options);
      return fetch(url, {
        ...options,
        headers: { ...headers, [token.headerName]: token.token },
      });
    };
    await use(page);
  },
  request: async ({ page }, use) => {
    await use(page.request);
  },
});
test.use({ trace: "off", screenshot: "off", video: "off" });
