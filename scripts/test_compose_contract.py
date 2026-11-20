"""Compose network regression: parallel projects must not share a fixed subnet."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import unittest
import uuid


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

    def test_default_network_leaves_subnet_allocation_to_docker(self):
        network = self.config()["networks"]["default"]
        self.assertIn("ipam", network)
        self.assertIn(network["ipam"]["config"][0].get("subnet"), (None, ""),
                      "A fixed default subnet collides with another QA project before app boot")

    def test_parallel_deployment_can_override_subnet(self):
        config = self.config("10.203.1.0/24")
        self.assertEqual(config["networks"]["default"].get("ipam", {}).get("config"),
                         [{"subnet": "10.203.1.0/24"}])
        self.assertNotIn("ports", config["services"]["database"])
        for name in ("backend", "frontend"):
            self.assertIn("default", config["services"][name]["networks"])
            self.assertIn("build", config["services"][name])
            self.assertIn("healthcheck", config["services"][name])

    @unittest.skipUnless(os.environ.get("COMPOSE_NETWORK_INTEGRATION") == "true",
                         "Opt in to disposable Docker network checks")
    def test_parallel_default_networks_receive_non_overlapping_subnets(self):
        import ipaddress

        subnet = self.config()["networks"]["default"]["ipam"]["config"][0].get("subnet") or ""
        created = []
        try:
            allocations = []
            for _ in range(2):
                name = "sicost-network-test-" + uuid.uuid4().hex
                result = subprocess.run(["docker", "network", "create", "--subnet", subnet, name],
                                        capture_output=True, text=True, check=False)
                self.assertEqual(result.returncode, 0, "Disposable network allocation failed")
                created.append(name)
                result = subprocess.run(["docker", "network", "inspect", name],
                                        capture_output=True, text=True, check=False)
                self.assertEqual(result.returncode, 0, "Disposable network readback failed")
                allocations.append(ipaddress.ip_network(json.loads(result.stdout)[0]["IPAM"]["Config"][0]["Subnet"]))
            self.assertFalse(allocations[0].overlaps(allocations[1]))
        finally:
            for name in reversed(created):
                result = subprocess.run(["docker", "network", "rm", name], capture_output=True, check=False)
                self.assertEqual(result.returncode, 0, "Disposable network cleanup failed")


if __name__ == "__main__":
    unittest.main()
