"""Compose regression: parallel QA/preview projects must not share a fixed subnet."""
import ipaddress
import json
import os
from pathlib import Path
import shutil
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which("docker"), "Docker Compose is required")
class ComposeNetworkContractTest(unittest.TestCase):
    def config(self, subnet=None):
        environment = os.environ.copy()
        environment.pop("COMPOSE_SUBNET", None)
        if subnet is not None:
            environment["COMPOSE_SUBNET"] = subnet
        command = ["docker", "compose", "--env-file", os.devnull,
                   "-f", str(ROOT / "infra/docker-compose.yml")]
        if subnet is not None:
            command += ["-f", str(ROOT / "infra/docker-compose.subnet.yml")]
        result = subprocess.run(
            command + ["config", "--format", "json"],
            cwd=ROOT, env=environment, capture_output=True, text=True, check=False,
        )
        # Config can contain injected secrets. Never include raw output in failures.
        self.assertEqual(result.returncode, 0, "Compose configuration failed")
        return json.loads(result.stdout)

    def test_default_network_uses_project_scoped_automatic_allocation(self):
        network = self.config()["networks"]["default"]
        self.assertFalse(network.get("ipam", {}).get("config"),
                         "A fixed subnet collides across QA/preview projects")
        self.assertEqual(network["driver"], "bridge")
        self.assertFalse(network.get("external", False))
        self.assertTrue(network["name"].endswith("_default"))

    def test_parallel_deployment_can_override_subnet(self):
        config = self.config("10.203.1.0/24")
        self.assertEqual(config["networks"]["default"].get("ipam", {}).get("config"),
                         [{"subnet": "10.203.1.0/24"}])
        subnet = ipaddress.ip_network(config["networks"]["default"]["ipam"]["config"][0]["subnet"])
        self.assertTrue(subnet.is_private)
        self.assertEqual(subnet.prefixlen, 24)

    def test_app_build_health_and_database_isolation_are_preserved(self):
        config = self.config()
        self.assertNotIn("ports", config["services"]["database"])
        for name in ("backend", "frontend"):
            self.assertIn("default", config["services"][name]["networks"])
            self.assertIn("build", config["services"][name])
            self.assertIn("healthcheck", config["services"][name])


if __name__ == "__main__":
    unittest.main()
