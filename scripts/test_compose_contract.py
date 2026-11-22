"""Compose network regression: parallel projects must not share a fixed subnet."""
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

    def test_default_network_uses_docker_non_overlapping_allocation(self):
        network = self.config()["networks"]["default"]
        for allocation in network.get("ipam", {}).get("config", []):
            self.assertFalse(allocation.get("subnet"),
                             "A fixed default subnet collides with another QA project")
        self.assertFalse(network.get("external", False))

    def test_empty_subnet_also_uses_automatic_allocation(self):
        self.assertEqual(self.config("")["networks"], self.config()["networks"])

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
