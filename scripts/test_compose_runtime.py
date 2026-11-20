"""Compose configuration regression checks; no daemon, containers or secrets output."""
import ipaddress
import json
import os
from pathlib import Path
import shutil
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which("docker"), "Docker Compose CLI is required")
class ComposeRuntimeTest(unittest.TestCase):
    def config(self, project="sicost-config-test", subnet=None):
        env = os.environ.copy()
        env.pop("SICOST_NETWORK_SUBNET", None)
        if subnet is not None:
            env["SICOST_NETWORK_SUBNET"] = subnet
        result = subprocess.run(
            ["docker", "compose", "--project-name", project, "--file",
             str(ROOT / "infra/docker-compose.yml"), "config", "--format", "json"],
            env=env, capture_output=True, text=True, check=False,
        )
        # Never include the resolved configuration: it contains DB credentials.
        self.assertEqual(result.returncode, 0, "Compose configuration failed")
        return json.loads(result.stdout)

    def subnet(self, config):
        network = config["networks"]["default"]
        self.assertIn("ipam", network, "Do not allocate from exhausted daemon pools")
        return network["ipam"]["config"][0]["subnet"]

    def test_default_network_has_explicit_private_subnet(self):
        subnet = ipaddress.ip_network(self.subnet(self.config()))
        self.assertEqual(str(subnet), "10.254.154.0/24")
        self.assertTrue(subnet.is_private)

    def test_runner_can_override_subnet_without_editing_compose(self):
        self.assertEqual(self.subnet(self.config(subnet="10.254.155.0/24")),
                         "10.254.155.0/24")

    def test_projects_keep_isolated_networks_with_distinct_runner_subnets(self):
        first = self.config(project="sicost-config-first", subnet="10.254.154.0/24")
        second = self.config(project="sicost-config-second", subnet="10.254.155.0/24")
        self.assertNotEqual(first["networks"]["default"]["name"],
                            second["networks"]["default"]["name"])
        self.assertNotEqual(self.subnet(first), self.subnet(second))
        for config in (first, second):
            self.assertFalse(config["networks"]["default"].get("external", False))
            self.assertNotIn("ports", config["services"]["database"])
            for service in ("backend", "frontend"):
                self.assertIn("build", config["services"][service])
                self.assertIn("healthcheck", config["services"][service])


if __name__ == "__main__":
    unittest.main()
