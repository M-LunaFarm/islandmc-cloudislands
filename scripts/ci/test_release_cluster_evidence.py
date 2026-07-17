#!/usr/bin/env python3
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


sys.dont_write_bytecode = True
SCRIPT = Path(__file__).with_name("release_cluster_evidence.py")
SPEC = importlib.util.spec_from_file_location("release_cluster_evidence", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ReleaseClusterEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.paper_log = root / "paper.log"
        self.velocity_log = root / "velocity.log"
        self.paper_log.write_text(
            "CloudIslands Paper agent enabled\n"
            "CloudIslands agent role=LOBBY node=smoke-lobby\n"
            "Done (1.000s)!\n",
            encoding="utf-8",
        )
        self.velocity_log.write_text(
            "CloudIslands Velocity router enabled\nListening on /127.0.0.1:25577\n",
            encoding="utf-8",
        )
        self.core = {
            "components": ["core-1", "core-2", "postgres", "redis", "object-storage"],
            "evidence": {
                gate: ["observed"]
                for gate in (
                    "multi-core-e2e",
                    "multi-paper-failover",
                    "chaos-test",
                    "backup-restore-drill",
                    "rolling-upgrade",
                    "load-test",
                    "support-bundle",
                    "operator-runbook",
                )
            },
            "failureInjections": [],
            "failureInjectionEvidence": {},
            "assertions": [{"name": "core-smoke", "result": "passed"}],
            "artifacts": [],
        }

    def tearDown(self):
        self.temp.cleanup()

    def test_boot_logs_only_add_observed_lobby_and_velocity_components(self):
        evidence = MODULE.build_release_evidence(self.core, self.paper_log, self.velocity_log)

        self.assertIn("velocity", evidence["components"])
        self.assertIn("lobby-paper", evidence["components"])
        self.assertNotIn("island-paper-a", evidence["components"])
        self.assertNotIn("island-paper-b", evidence["components"])
        self.assertNotIn("virtual-player", evidence["components"])
        self.assertEqual([], evidence["failureInjections"])
        self.assertEqual({}, evidence["failureInjectionEvidence"])
        self.assertEqual("partial-release-cluster-smoke", evidence["certificationScope"])

    def test_lobby_component_requires_lobby_role_marker(self):
        self.paper_log.write_text(
            "CloudIslands Paper agent enabled\nDone (1.000s)!\n",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(RuntimeError, "CloudIslands agent role=LOBBY"):
            MODULE.build_release_evidence(self.core, self.paper_log, self.velocity_log)


if __name__ == "__main__":
    unittest.main()
