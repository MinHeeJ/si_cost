"""Regression checks for the durable contract's bounded runtime reader."""
import json
from pathlib import Path
import unittest
from unittest.mock import patch

import yaml

from compact_openapi_fixture import FIXTURE, LIMIT, compact


class CompactOpenApiFixtureTest(unittest.TestCase):
    def test_repeated_values_preserve_operations_and_security(self):
        response = {"200": {"content": {"application/json": {"schema": {"type": "object"}}}}}
        document = {
            "openapi": "3.0.3",
            "security": [],
            "paths": {
                "/api/health": {"get": {"operationId": "getHealth", "responses": response}},
                "/api/items": {"get": {"operationId": "getItems", "responses": response}},
            },
            "components": {"securitySchemes": {}},
        }
        encoded = compact(document)
        self.assertEqual(yaml.safe_load(encoded), document)
        self.assertEqual(compact(yaml.safe_load(encoded)), encoded)
        self.assertLessEqual(len(encoded), LIMIT)

    def test_reader_budget_counts_utf8_bytes_not_characters(self):
        document = {"description": "한글" * 100}
        with patch("compact_openapi_fixture.LIMIT", 300):
            with self.assertRaises(ValueError):
                compact(document)

    def test_different_values_are_not_merged(self):
        document = {"first": [True, 1, "1"], "second": [False, 2, "2"]}
        self.assertEqual(yaml.safe_load(compact(document)), document)

    def test_oversized_contract_is_compacted_without_dropping_operations(self):
        document = {"paths": {
            f"/api/items-{index}": {"get": {
                "operationId": f"getItems{index}",
                "responses": {"200": {"description": "contract description " * 150}},
            }} for index in range(400)
        }}
        self.assertGreater(len(yaml.safe_dump(document).encode("utf-8")), LIMIT)
        encoded = compact(document)
        self.assertLessEqual(len(encoded), LIMIT)
        self.assertEqual(yaml.safe_load(encoded), document)

    def test_durable_fixture_is_complete_within_runtime_read_window(self):
        with FIXTURE.open("rb") as stream:
            encoded = stream.read(LIMIT + 1)
            self.assertLessEqual(len(encoded), LIMIT)
            self.assertEqual(stream.read(1), b"")
        document = yaml.safe_load(encoded)
        self.assertEqual(document["paths"]["/api/health"]["get"]["operationId"], "getHealth")
        self.assertEqual(document["paths"]["/api/code-groups"]["get"]["operationId"], "getCodeGroups")
        self.assertEqual(yaml.safe_load(compact(document)), document)

    def test_runtime_documents_match_backend_only_test_resources(self):
        for name, limit in (("aiops.runtime-check.json", 65536),
                            ("aiops.initial-login.json", 16384)):
            with self.subTest(resource=name):
                root_bytes = Path(name).read_bytes()
                fixture_bytes = (FIXTURE.parent / name).read_bytes()
                self.assertLessEqual(len(root_bytes), limit)
                self.assertLessEqual(len(fixture_bytes), limit)
                self.assertEqual(json.loads(root_bytes), json.loads(fixture_bytes))

    def test_bounded_reader_resolves_runtime_operations_without_authentication(self):
        with FIXTURE.open("rb") as stream:
            document = yaml.safe_load(stream.read(LIMIT + 1))
        runtime = json.loads(Path("aiops.runtime-check.json").read_bytes())
        self.assertFalse(runtime["auth"]["required"])
        self.assertFalse(document.get("security"))
        self.assertFalse(document.get("components", {}).get("securitySchemes"))
        for operation in (runtime["backend"]["health"], runtime["first_menu"]):
            with self.subTest(operation=operation["operation_id"]):
                definition = document["paths"][operation["path"]][operation["method"].lower()]
                self.assertEqual(definition["operationId"], operation["operation_id"])
                self.assertFalse(definition.get("security"))
                self.assertFalse(definition.get("x-roles"))
                self.assertFalse(definition.get("parameters"))


if __name__ == "__main__":
    unittest.main()
