"""Check packaged physical entities, never Markdown headings or reference-only inputs.

Run from the repository root: python3 scripts/check_db_contract.py
The durable catalog was authored from the physical field contract. This check is
independent of the platform's Markdown extractor and does not claim to repair it.
"""
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "backend/src/main/resources"


def check(catalog, sql):
    tables = {
        name.upper(): body
        for name, body in re.findall(
            r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+(\w+)\s*\((.*?)\n\);",
            sql, re.IGNORECASE | re.DOTALL,
        )
    }
    violations = []
    for name, entity in catalog.items():
        if name not in tables:
            continue
        body = tables[name]
        for column in entity["columns"]:
            match = re.search(
                r"^\s*" + re.escape(column["name"]) + r"\s+"
                r"((?:VARCHAR|CHAR|NUMERIC)(?:\([\d, ]+\))?|TIMESTAMP|DATE|TEXT)(.*)$",
                body, re.IGNORECASE | re.MULTILINE,
            )
            if match is None:
                violations.append(name + "." + column["name"] + ":missing")
                continue
            actual_type = re.sub(r"\s+", "", match[1]).lower()
            if actual_type != column["type"]:
                violations.append(name + "." + column["name"] + ":type")
            if ("NOT NULL" not in match[2].upper()) != column["nullable"]:
                violations.append(name + "." + column["name"] + ":nullable")
        key = re.search(r"PRIMARY\s+KEY\s*\(([^)]+)\)", body, re.IGNORECASE)
        if key is None or [field.strip().lower() for field in key[1].split(",")] != entity["primaryKey"]:
            violations.append(name + ":primary_key")
    missing = sorted(set(catalog) - set(tables))
    return {
        "ok": not missing and not violations,
        "table_source": "packaged_physical_catalog",
        "expected_tables": len(catalog),
        "sql_tables": len(tables),
        "expected_columns": sum(len(entity["columns"]) for entity in catalog.values()),
        "missing_tables": missing,
        "violations": violations,
    }


if __name__ == "__main__":
    catalog = json.loads((RESOURCES / "db/catalog.json").read_text(encoding="utf-8"))
    reports = {
        str(path.relative_to(ROOT)): check(catalog, path.read_text(encoding="utf-8"))
        for path in (RESOURCES / "schema.sql", RESOURCES / "db/migration/V1__full_schema.sql")
    }
    print(json.dumps(reports, ensure_ascii=False, indent=2))
    sys.exit(0 if all(report["ok"] for report in reports.values()) else 1)
