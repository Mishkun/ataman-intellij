import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

from scripts import compatibility as c


PROPERTIES = """# Keep user settings
platformVersion=2025.1
pluginVerificationVersions=2024.3,2026.2.3
org.gradle.jvmargs=-Xmx1024m
"""
BUILD = """version = "1.5.1"
ideaVersion {
    sinceBuild = "243"
    untilBuild = "261.*"
}
latest {
    sinceBuild = "243"
    untilBuild = "261.*"
}
"""


def payload(version="2026.2.3", build="262.10968.63", kind="release"):
    return {"IIU": [{"version": version, "build": build, "type": kind}]}


class CompatibilityTests(unittest.TestCase):
    def setUp(self):
        self.stable = c.Release("2026.2.3", "262.10968.63", "release")
        self.stale_rc = c.Release("2026.2.2", "262.10315.69", "rc")
        self.new_rc = c.Release("2026.3", "263.10.2", "rc")

    def test_empty_or_invalid_payload_fails(self):
        for bad in ({}, {"IIU": []}, {"IIU": None}, [], {"IIU": [None]}):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                c.parse_releases(bad, "release")

    def test_strict_numeric_metadata_and_type(self):
        for version, build, kind in (
            ("2026.3 RC", "263.10.2", "rc"),
            ("2026.3", "IU-263.10.2", "rc"),
            ("2026.3", "263.10", "rc"),
            ("2026.3", "263.10.2\n", "rc"),
            ("2026.3", "263.10.2", "release"),
            (None, "263.10.2", "rc"),
            ("2026.3", 263, "rc"),
            ("2026", "263.10.2", "rc"),
            ("2026.2.0.1.2", "263.10.2", "rc"),
            ("2026.2.00.1", "263.10.2", "rc"),
        ):
            with (
                self.subTest(version=version, build=build),
                self.assertRaises(ValueError),
            ):
                c.parse_releases(payload(version, build, kind), "rc")

    def test_four_component_stable_hotfix_is_accepted(self):
        stable = c.parse_releases(payload(version="2026.2.0.1"), "release")
        self.assertEqual(stable.version, "2026.2.0.1")
        plan = c.plan_update(BUILD, PROPERTIES, stable, self.new_rc)
        self.assertEqual(plan["matrix"], ["2024.3", "2026.2.0.1", "263.10.2"])

    def test_duplicate_releases_are_deduplicated_but_conflicts_fail(self):
        data = payload()
        data["IIU"] *= 2
        self.assertEqual(c.parse_releases(data, "release"), self.stable)
        data["IIU"].append(
            {"version": "2026.2.4", "build": self.stable.build, "type": "release"}
        )
        with self.assertRaises(ValueError):
            c.parse_releases(data, "release")

    def test_latest_build_is_numeric_not_lexical(self):
        data = payload(build="262.9.1")
        data["IIU"].extend(payload(build="262.100.1")["IIU"])
        self.assertEqual(c.parse_releases(data, "release").build, "262.100.1")

    def test_current_stable_branch_and_stale_rc_are_noop(self):
        self.assertFalse(
            c.plan_update(
                BUILD.replace("261.*", "262.*"), PROPERTIES, self.stable, self.stale_rc
            )["changed"]
        )

    def test_same_branch_newer_rc_is_noop(self):
        newer_patch_rc = c.Release("2026.2.4", "262.20000.1", "rc")
        self.assertFalse(
            c.plan_update(
                BUILD.replace("261.*", "262.*"), PROPERTIES, self.stable, newer_patch_rc
            )["changed"]
        )

    def test_new_rc_uses_full_build_and_explicit_matrix(self):
        plan = c.plan_update(BUILD, PROPERTIES, self.stable, self.new_rc)
        self.assertEqual(plan["until"], "263.*")
        self.assertEqual(plan["branch"], "compatibility/idea-263")
        self.assertEqual(plan["matrix"], ["2024.3", "2026.2.3", "263.10.2"])

    def test_stable_fallback_for_superseded_rc(self):
        plan = c.plan_update(BUILD, PROPERTIES, self.stable, self.stale_rc)
        self.assertEqual(plan["candidate"], vars(self.stable))
        self.assertEqual(plan["matrix"], ["2024.3", "2026.2.3"])
        self.assertIn("Stable fallback", plan["reason"])

    def test_new_stable_after_rc_is_supported(self):
        stable = c.Release("2026.3", "263.20.3", "release")
        plan = c.plan_update(BUILD, PROPERTIES, stable, self.new_rc)
        self.assertEqual(plan["candidate"], vars(stable))
        self.assertEqual(plan["until"], "263.*")

    def test_preserves_all_other_properties_and_crlf(self):
        text = PROPERTIES.replace("\n", "\r\n")
        build = BUILD.replace("\n", "\r\n")
        plan = c.plan_update(build, text, self.stable, self.new_rc)
        changed_build, changed = c.apply_plan(build, text, plan)
        self.assertEqual(changed_build, build.replace("261.*", "263.*"))
        self.assertEqual(
            changed,
            text.replace(
                "pluginVerificationVersions=2024.3,2026.2.3",
                "pluginVerificationVersions=2024.3,2026.2.3,263.10.2",
            ),
        )

    def test_duplicate_matrix_property_fails(self):
        with self.assertRaises(ValueError):
            c.plan_update(
                BUILD,
                PROPERTIES + "pluginVerificationVersions=2024.3\n",
                self.stable,
                self.new_rc,
            )

    def test_missing_duplicate_mismatched_or_nonliteral_until_fails(self):
        for build in (
            BUILD.replace('    untilBuild = "261.*"\n', "", 1),
            BUILD + 'untilBuild = "261.*"\n',
            BUILD.replace("261.*", "262.*", 1),
            BUILD.replace('"261.*"', "provider", 1),
        ):
            with self.subTest(build=build), self.assertRaises(ValueError):
                c.plan_update(build, PROPERTIES, self.stable, self.new_rc)

    def test_missing_optional_matrix_is_appended_once_and_update_is_idempotent(self):
        properties = "platformVersion=2025.1\r\n"
        plan = c.plan_update(BUILD, properties, self.stable, self.new_rc)
        build, changed = c.apply_plan(BUILD, properties, plan)
        self.assertEqual(
            changed,
            properties + "pluginVerificationVersions=2024.3,2026.2.3,263.10.2\r\n",
        )
        second = c.plan_update(build, changed, self.stable, self.new_rc)
        self.assertFalse(second["changed"])
        self.assertEqual(c.apply_plan(build, changed, second), (build, changed))

    @patch("scripts.compatibility.fetch_release")
    def test_both_files_are_validated_before_either_is_written(self, fetch):
        for build, properties in (
            (BUILD.replace("261.*", "262.*", 1), PROPERTIES),
            (BUILD, PROPERTIES + "pluginVerificationVersions=2024.3\n"),
        ):
            fetch.side_effect = [self.stable, self.new_rc]
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                source, props = root / "build.gradle.kts", root / "gradle.properties"
                source.write_text(build)
                props.write_text(properties)
                with self.assertRaises(ValueError):
                    c.create_plan(props, root / "plan.json", source)
                self.assertEqual(source.read_text(), build)
                self.assertEqual(props.read_text(), properties)

    @patch("scripts.compatibility.urllib.request.urlopen")
    def test_http_timeout_is_explicit_and_network_errors_propagate(self, urlopen):
        urlopen.side_effect = urllib.error.URLError("timeout")
        with self.assertRaises(urllib.error.URLError):
            c.fetch_release("rc")
        self.assertEqual(urlopen.call_args.kwargs["timeout"], 30)

    @patch("scripts.compatibility.fetch_release")
    def test_bad_api_does_not_edit_properties(self, fetch):
        fetch.side_effect = [self.stable, ValueError("bad RC data")]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "gradle.properties"
            path.write_text(PROPERTIES)
            with self.assertRaises(ValueError):
                c.create_plan(path, Path(directory) / "plan.json")
            self.assertEqual(path.read_text(), PROPERTIES)

    def test_failure_report_never_claims_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            plan = Path(directory) / "plan.json"
            plan.write_text(
                json.dumps(c.plan_update(BUILD, PROPERTIES, self.stable, self.new_rc))
            )
            body = Path(directory) / "body.md"
            with patch.dict(
                os.environ,
                {
                    "BUILD_STATUS": "success",
                    "VERIFIER_STATUS": "failure",
                    "RUN_URL": "https://example.test/run",
                    "REPORT_URL": "https://example.test/artifact",
                },
            ):
                c.report(plan, body)
            text = body.read_text()
            self.assertIn("did not pass", text)
            self.assertNotIn("verification passed", text)
            self.assertIn("| Plugin Verifier | failure |", text)
            self.assertIn("263.10.2", text)
            self.assertIn("https://example.test/artifact", text)

    def render_report(self, root, reports, build="success", verifier="success"):
        plan = root / "plan.json"
        plan.write_text(
            json.dumps(c.plan_update(BUILD, PROPERTIES, self.stable, self.new_rc))
        )
        body = root / "body.md"
        with patch.dict(
            os.environ,
            {
                "BUILD_STATUS": build,
                "VERIFIER_STATUS": verifier,
                "RUN_URL": "https://example.test/run",
                "RUNNER_TEMP": str(root),
            },
        ):
            c.report(plan, body, reports)
        return body.read_text()

    def test_report_includes_real_verdicts_warnings_and_errors_per_ide(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = root / "reports"
            for ide, verdict in (
                ("IU-243.100.1", "OK"),
                ("IU-263.10.2", "Compatibility problems"),
            ):
                destination = reports / ide / "ataman-1.5.1"
                destination.mkdir(parents=True)
                (destination / "verification-verdict.txt").write_text(verdict)
            (destination / "compatibility-warnings.txt").write_text(
                "Warning: deprecated method use"
            )
            (destination / "compatibility-problems.txt").write_text(
                "Error: unresolved class Example"
            )
            text = self.render_report(root, reports, verifier="failure")
            for expected in (
                "IU-243.100.1",
                "IU-263.10.2",
                "<pre>OK</pre>",
                "Compatibility problems",
                "Warning: deprecated method use",
                "Error: unresolved class Example",
            ):
                self.assertIn(expected, text)

    def test_api_usage_reports_do_not_hide_the_verdict(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = root / "reports"
            reports.mkdir()
            for name, text in {
                "compatibility-problems.txt": "Missing method",
                "deprecated-usages.txt": "deprecated-symbol",
                "experimental-api-usages.txt": "Experimental API",
                "internal-api-usages.txt": "Internal API",
                "verification-verdict.txt": "real-verdict",
            }.items():
                (reports / name).write_text(text)
            text = self.render_report(root, reports)
            self.assertIn("real-verdict", text)
            self.assertIn("deprecated-symbol", text)

    def test_missing_reports_and_failed_verifier_include_bounded_log_tail(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "verifier.log").write_text(
                "OLD LINE\n" + "x\n" * 10000 + "FINAL verifier error\n"
            )
            text = self.render_report(root, root / "missing", verifier="failure")
            self.assertIn("No verdict, warning or error reports were found", text)
            self.assertIn("not evidence of zero problems", text)
            self.assertIn("did not pass", text)
            self.assertIn("verifier.log (tail)", text)
            self.assertIn("FINAL verifier error", text)
            self.assertNotIn("OLD LINE", text)
            self.assertLess(len(text), 5000)

    def test_failure_uses_existing_workflow_log_names(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "compatibility-verifier.log").write_text(
                "Verifier failed to resolve IDE"
            )
            (root / "compatibility-build.log").write_text("Tests failed")
            text = self.render_report(
                root, root / "missing", build="failure", verifier="failure"
            )
            self.assertIn("Verifier failed to resolve IDE", text)
            self.assertIn("Tests failed", text)

    def test_report_excerpt_is_bounded_and_escapes_untrusted_markup(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = root / "reports"
            reports.mkdir()
            (reports / "compatibility-problems.txt").write_text(
                "<script>unsafe</script>\n" + "x" * 100000
            )
            text = self.render_report(root, reports)
            self.assertIn("&lt;script&gt;unsafe&lt;/script&gt;", text)
            self.assertNotIn("<script>", text)
            self.assertIn("Excerpt truncated", text)
            self.assertLess(len(text), 5000)


if __name__ == "__main__":
    unittest.main()
