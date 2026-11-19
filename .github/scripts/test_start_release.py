"""Offline tests for the release task; never call GitHub."""

import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("start_release", ROOT / ".github/scripts/start-release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class StartReleaseTests(unittest.TestCase):
    def test_dispatch_waits_until_no_ci_run_is_unfinished(self):
        order = []
        pending = iter([[7], [7], []])

        def fake_running():
            order.append("poll")
            return next(pending)

        with (
            patch.object(release, "running_ci", side_effect=fake_running),
            patch.object(release.time, "sleep"),
            patch.object(release, "run", side_effect=lambda *a, **k: order.append("dispatch") or ""),
        ):
            release.release(follow=False)
        self.assertEqual(order, ["poll", "poll", "poll", "dispatch"])

    def test_without_follow_dispatches_and_does_not_watch(self):
        with patch.object(release, "wait_for_ci"), patch.object(release, "run") as run:
            release.release(follow=False)
        self.assertEqual([call.args[:2] for call in run.call_args_list], [("gh", "workflow")])

    def test_follow_watches_the_dispatched_run(self):
        with (
            patch.object(release, "wait_for_ci"),
            patch.object(release, "dispatch", return_value=42),
            patch.object(release, "run") as run,
        ):
            release.release(follow=True)
        self.assertEqual(run.call_args.args, ("gh", "run", "watch", "42", "--exit-status"))

    def test_running_ci_reports_only_unfinished_runs(self):
        listing = '[{"databaseId": 1, "status": "completed"}, {"databaseId": 2, "status": "in_progress"}]'
        with patch.object(release, "run", return_value=listing):
            self.assertEqual(release.running_ci(), [2])


if __name__ == "__main__":
    unittest.main()
