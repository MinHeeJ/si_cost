"""Compose network regression: do not depend on Docker's exhausted default pools."""
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
        result = subprocess.run(
            ["docker", "compose", "-f", str(ROOT / "infra/docker-compose.yml"),
             "config", "--format", "json"],
            cwd=ROOT, env=environment, capture_output=True, text=True, check=False,
        )
        # Config can contain injected secrets. Never include raw output in failures.
        self.assertEqual(result.returncode, 0, "Compose configuration failed")
        return json.loads(result.stdout)

    def test_default_network_has_explicit_private_subnet(self):
        network = self.config()["networks"]["default"]
        self.assertIn("ipam", network, "Default-pool allocation would fail before app boot")
        subnet = ipaddress.ip_network(network["ipam"]["config"][0]["subnet"])
        self.assertTrue(subnet.is_private)
        self.assertEqual(subnet.prefixlen, 24)

    def test_parallel_deployment_can_override_subnet(self):
        config = self.config("10.203.1.0/24")
        self.assertEqual(config["networks"]["default"].get("ipam", {}).get("config"),
                         [{"subnet": "10.203.1.0/24"}])
        self.assertNotIn("ports", config["services"]["database"])
        for name in ("backend", "frontend"):
            self.assertIn("default", config["services"][name]["networks"])
            self.assertIn("build", config["services"][name])
            self.assertIn("healthcheck", config["services"][name])


if __name__ == "__main__":
    unittest.main()
