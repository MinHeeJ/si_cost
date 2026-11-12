"""Losslessly compact the durable OpenAPI fixture for the runtime reader's 1 MiB budget.

Only equal YAML values share anchors; paths, operations and provenance remain intact.
Run from the repository root. Never reads reference-only specification directories.
"""
import json
from pathlib import Path
import sys

import yaml

FIXTURE = Path("backend/src/test/resources/contracts/openapi.yaml")
LIMIT = 1024 * 1024


def compact(document):
    shared = {}

    def deduplicate(value):
        if isinstance(value, str):
            return sys.intern(value)
        if isinstance(value, (list, dict)):
            key = (type(value).__name__, json.dumps(value, ensure_ascii=False, sort_keys=True))
            if key not in shared:
                shared[key] = ([deduplicate(item) for item in value] if isinstance(value, list)
                               else {name: deduplicate(item) for name, item in value.items()})
            return shared[key]
        return value

    class ContractDumper(yaml.SafeDumper):
        def ignore_aliases(self, data):
            return not (isinstance(data, (dict, list)) or isinstance(data, str) and len(data) > 40)

    result = yaml.dump(deduplicate(document), Dumper=ContractDumper,
                       allow_unicode=True, sort_keys=False, width=120).encode("utf-8")
    if len(result) > LIMIT or yaml.safe_load(result) != document:
        raise ValueError("Durable contract compaction failed validation")
    return result


if __name__ == "__main__":
    document = yaml.safe_load(FIXTURE.read_bytes())
    result = compact(document)
    FIXTURE.write_bytes(result)
    print("PASS durable OpenAPI: lossless compaction; within runtime reader budget")
