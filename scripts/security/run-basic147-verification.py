#!/usr/bin/env python3
"""Verifier-owned full regression entry point. Implementation agents must not execute it.

Only --execute runs suites/build/browser. Credentials stay in inherited environment;
raw test output is never written, printed, or copied to the scalar validation report.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "docs/security/basic147-validation-report.md"


def git(*args: str) -> str:
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=True)
    return result.stdout.strip()


def json_output(text: str) -> dict:
    decoder = json.JSONDecoder()
    for match in re.finditer(r"\{", text):
        try:
            value, _ = decoder.raw_decode(text[match.start():])
            if isinstance(value, dict) and ("stats" in value or "numTotalTests" in value):
                return value
        except ValueError:
            continue
    raise ValueError("No machine-readable test totals were returned")


def junit_totals(start: float) -> dict:
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "suites": 0}
    for path in (ROOT / "backend/target/surefire-reports").glob("TEST-*.xml"):
        if path.stat().st_mtime < start:
            continue  # Never reuse a previous compilation/verification's stale results.
        suite = ET.parse(path).getroot()
        if suite.tag != "testsuite":
            raise ValueError("Unexpected JUnit report shape")
        for key in ("tests", "failures", "errors", "skipped"):
            totals[key] += int(suite.attrib.get(key, "0"))
        totals["suites"] += 1
    if not totals["suites"] or not totals["tests"]:
        raise ValueError("No fresh executed backend suites were found")
    return totals


def static_boundaries() -> dict:
    migrations = git("diff", "--name-only", "HEAD", "--", "backend/src/main/resources/db/migration").splitlines()
    migrations += git("ls-files", "--others", "--exclude-standard", "--", "backend/src/main/resources/db/migration").splitlines()
    changed = git("diff", "--name-only", "HEAD", "--", "backend/src/main").splitlines()
    changed += git("ls-files", "--others", "--exclude-standard", "--", "backend/src/main").splitlines()
    findings: list[dict] = []
    for name in sorted(set(changed)):
        path = ROOT / name
        if not path.is_file() or path.suffix not in {".java", ".xml", ".sql"}:
            continue
        # Scan additions only for tracked source; the entire content is new for untracked source.
        tracked = subprocess.run(["git", "ls-files", "--error-unmatch", "--", name], cwd=ROOT, capture_output=True).returncode == 0
        if tracked:
            diff = git("diff", "--unified=0", "HEAD", "--", name)
            lines = [line[1:] for line in diff.splitlines() if line.startswith("+") and not line.startswith("+++")]
        else:
            lines = path.read_text().splitlines()
        for index, line in enumerate(lines, 1):
            if re.search(r"\bCREATE\s+TABLE\b", line, re.I):
                findings.append({"path": name, "addition": index, "kind": "new_table_review"})
            if re.search(r"\bDELETE\s+FROM\s+tb_sy_(accs_log|chg_hist)\b", line, re.I):
                findings.append({"path": name, "addition": index, "kind": "audit_deletion_review"})
            if re.search(r"UPDATE\s+tb_sy_user\s+SET.*pwd_hash", line, re.I) and not re.search(r"WHERE\s+user_id\s*=", line, re.I):
                findings.append({"path": name, "addition": index, "kind": "password_update_scope_review"})
    return {"migration_changes": sorted(set(migrations)), "added_source_findings": findings,
            "limits": "Static additions scan only. Fixture DB rereads verify preserved settings, ciphertext and sequences; absence of a pattern is not a runtime proof."}


def backend_command() -> list[str]:
    if shutil.which("mvn"):
        return ["mvn", "-B", "-ntp", "verify"]
    if not shutil.which("docker"):
        raise RuntimeError("Maven or Docker is required")
    command = ["docker", "run", "--rm"]
    network = os.environ.get("BASIC147_DOCKER_NETWORK")
    if network:
        command += ["--network", network]
    for name in sorted(os.environ):
        if name.startswith(("SPRING_", "MAIL_", "BASIC147_", "SICOST_")):
            command += ["-e", name]  # Environment variable NAMES only, never values.
    return command + ["-v", f"{ROOT / 'backend'}:/app", "-v", "sicost-basic147-maven:/root/.m2", "-w", "/app", "maven:3.9-eclipse-temurin-17", "mvn", "-B", "-ntp", "verify"]


def run_phase(name: str, command: list[str], directory: str, kind: str | None = None) -> dict:
    start = time.time()
    try:
        result = subprocess.run(command, cwd=ROOT / directory, env=os.environ.copy(), capture_output=True, text=True, timeout=2400)
    except (OSError, subprocess.TimeoutExpired):
        return {"phase": name, "command": command, "exit": None, "status": "BLOCKED", "reason": "Process could not complete; no result was substituted"}
    row = {"phase": name, "command": command, "exit": result.returncode, "status": "PASS" if result.returncode == 0 else "FAIL"}
    try:
        if kind == "junit":
            row["totals"] = junit_totals(start)
        elif kind == "vitest":
            data = json_output(result.stdout)
            row["totals"] = {"tests": data["numTotalTests"], "passed": data["numPassedTests"], "failures": data["numFailedTests"], "skipped": data["numPendingTests"]}
            if sum(row["totals"][key] for key in ("passed", "failures", "skipped")) != row["totals"]["tests"]:
                raise ValueError("Inconsistent frontend totals")
        elif kind == "playwright":
            stats = json_output(result.stdout)["stats"]
            row["totals"] = {"tests": sum(stats.get(key, 0) for key in ("expected", "unexpected", "flaky", "skipped")), "passed": stats.get("expected", 0), "failures": stats.get("unexpected", 0), "flaky": stats.get("flaky", 0), "skipped": stats.get("skipped", 0)}
        if kind and row.get("totals", {}).get("tests", 0) == 0:
            raise ValueError("No executed tests")
        totals = row.get("totals", {})
        if any(totals.get(key, 0) for key in ("failures", "errors", "flaky")):
            row["status"] = "FAIL"
        if totals.get("skipped", 0) and row["status"] == "PASS":
            row["status"] = "PARTIAL"
    except (ValueError, KeyError, ET.ParseError):
        row["status"] = "UNVERIFIED"
        row["reason"] = "Fresh trustworthy totals could not be parsed; raw output was withheld to avoid retaining secrets"
    print(f"{name}: {row['status']} (exit={row['exit']})", flush=True)
    return row


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", action="store_true", help="Verifier-only explicit permission to run builds, suites and real browser scenarios")
    args = parser.parse_args()
    if not args.execute:
        parser.error("Nothing was executed. Only the verifier may opt in with --execute.")
    required = ("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD", "BASIC147_MAIL_COLLECTOR", "SICOST_E2E_BASE_URL", "BASIC147_E2E_ACCOUNTS_JSON", "BASIC147_E2E_TARGETS_JSON", "BASIC147_E2E_B1_FILE")
    if os.environ.get("BASIC147_INTEGRATION") != "true" or os.environ.get("BASIC147_E2E_DISPOSABLE") != "true" or any(not os.environ.get(name) for name in required):
        raise SystemExit("Disposable app, database, SMTP collector and synthetic fixture prerequisites are required. No credentials or values were printed.")
    try:
        head = git("rev-parse", "HEAD")
        boundaries = static_boundaries()
        provenance = "local Git HEAD; additions scanned, not a runtime preservation proof"
        git_verified = True
    except (subprocess.CalledProcessError, OSError):
        supplied = os.environ.get("BASIC147_SOURCE_HEAD", "")
        head = supplied if re.fullmatch(r"[0-9a-fA-F]{40}", supplied) else None
        provenance = "Frozen copy without Git metadata; supplied HEAD is not independently resolved"
        boundaries = {"status": "UNVERIFIED_NO_GIT_METADATA", "migration_changes": None, "added_source_findings": None}
        git_verified = False
    phases = [run_phase("backend-verify", backend_command(), "backend", "junit"),
              run_phase("frontend-typecheck", ["npm", "run", "typecheck"], "frontend"),
              run_phase("frontend-unit", ["npm", "run", "test", "--", "--run", "--reporter=json"], "frontend", "vitest"),
              run_phase("frontend-build", ["npm", "run", "build"], "frontend"),
              run_phase("real-browser-security-and-legacy", ["npm", "run", "test", "--", "--reporter=json"], "e2e", "playwright")]
    unverified = []
    if not git_verified:
        unverified.append("Source provenance and added-source preservation scan require external frozen-snapshot evidence")
    if os.environ.get("BASIC147_E2E_ASYNC_GATE_READY") != "true":
        unverified.append("Actual delayed AI source/revocation integration unavailable; skipped is not passed")
    if os.environ.get("BASIC147_LAST_ADMIN_ISOLATED") != "true":
        unverified.append("Sole ADMIN lock was not opted into on an isolated sole-admin DB")
    complete = all(row["status"] == "PASS" for row in phases) and git_verified and not boundaries["migration_changes"] and not boundaries["added_source_findings"] and not unverified
    payload = {"head": head, "head_provenance": provenance, "completed_at_utc": datetime.now(timezone.utc).isoformat(), "overall": "SUITES_PASS" if complete else "INCOMPLETE", "phases": phases, "boundaries": boundaries,
               "unverified": unverified, "limits": "Suite totals do not replace independent per-feature browser/DB evidence. Unavailable predecessor integrations must be reported separately."}
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("# 로그인·실제 권한 최종 검증 보고\n\n검증자가 실제 실행한 종료코드/새 시험 보고서만 집계합니다. 비밀번호·OTP·쿠키·본문·원시 로그는 보관하지 않습니다.\n\n```json\n" + json.dumps(payload, ensure_ascii=False, indent=2) + "\n```\n", encoding="utf-8")
    return 0 if complete else 1


if __name__ == "__main__":
    raise SystemExit(main())
