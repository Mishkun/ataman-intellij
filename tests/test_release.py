import hashlib
import json
import os
from pathlib import Path
import tempfile
import subprocess
import unittest
from unittest.mock import patch

from scripts import release


class ReleaseTests(unittest.TestCase):
    def test_semver_bumps_only_literal_project_version(self):
        source = 'kotlin("jvm") version "2.0.20"\nversion = "1.5.1"\n'
        for kind, expected in (
            ("patch", "1.5.2"),
            ("minor", "1.6.0"),
            ("major", "2.0.0"),
        ):
            with self.subTest(kind=kind):
                updated, version = release.bump(source, kind)
                self.assertEqual(version, expected)
                self.assertEqual(
                    updated,
                    source.replace('version = "1.5.1"', f'version = "{expected}"'),
                )

    def test_invalid_or_ambiguous_version_fails(self):
        for source, kind in (
            ('version = "1.5.1"', "other"),
            ('version = "1.5.1-RC"', "patch"),
            ('version = "1.5.1"\nversion = "1.5.2"', "patch"),
        ):
            with self.subTest(source=source), self.assertRaises(ValueError):
                release.bump(source, kind)

    @patch("scripts.release.git")
    def test_additional_notes_are_literal_data(self, git):
        git.side_effect = ["", "v1.5.1", "- Fix plugin"]
        notes = '$(touch injected)\n"quoted"\n${{ untrusted.expression }}'
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            previous = Path.cwd()
            try:
                os.chdir(root)
                Path("build.gradle.kts").write_text('version = "1.5.1"\n')
                with patch.dict(
                    os.environ,
                    {
                        "GITHUB_OUTPUT": str(root / "outputs"),
                        "RELEASE_TYPE": "patch",
                        "ADDITIONAL_NOTES": notes,
                    },
                ):
                    release.prepare(root)
                self.assertEqual(
                    Path("changelog.txt").read_text(), notes + "\n\n- Fix plugin\n"
                )
                self.assertEqual((root / "release-version").read_text(), "1.5.2")
                self.assertFalse(Path("injected").exists())
            finally:
                os.chdir(previous)

    @patch("scripts.release.git", return_value="v1.5.2")
    def test_existing_release_tag_fails_before_edit(self, git):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            previous = Path.cwd()
            try:
                os.chdir(root)
                source = 'version = "1.5.1"\n'
                Path("build.gradle.kts").write_text(source)
                with (
                    patch.dict(os.environ, {"RELEASE_TYPE": "patch"}),
                    self.assertRaises(ValueError),
                ):
                    release.prepare(root)
                self.assertEqual(Path("build.gradle.kts").read_text(), source)
                self.assertFalse(Path("changelog.txt").exists())
            finally:
                os.chdir(previous)

    def test_frozen_archive_is_independent_and_checked_before_publication(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "build/distributions").mkdir(parents=True)
            source = root / "build/distributions/plugin.zip"
            source.write_bytes(b"tested archive")
            temp = root / "runner"
            temp.mkdir()
            previous = Path.cwd()
            try:
                os.chdir(root)
                (temp / "release-tag").write_text("v1.5.2")
                with (
                    patch.dict(os.environ, {"GITHUB_OUTPUT": str(root / "outputs")}),
                    patch("scripts.release.git", return_value="a" * 40),
                ):
                    release.freeze(temp)
                source.write_bytes(b"unexpected rebuild")
                release.check(temp)
                manifest = json.loads((temp / "verified-archive.json").read_text())
                frozen = Path(manifest["path"])
                self.assertEqual(frozen.read_bytes(), b"tested archive")
                frozen.chmod(0o644)
                frozen.write_bytes(b"tampered")
                with self.assertRaises(ValueError):
                    release.check(temp)
            finally:
                os.chdir(previous)

    def test_retry_rejects_unsafe_tag_and_requires_explicit_confirmation(self):
        for tag, confirmed in (
            ("refs/tags/v1.5.2", "true"),
            ("v1.5.2;echo unsafe", "true"),
            ("v1.5.2", "false"),
        ):
            with self.subTest(tag=tag), tempfile.TemporaryDirectory() as directory:
                with (
                    patch.dict(
                        os.environ,
                        {"EXISTING_TAG": tag, "RETRY_PUBLISH_CONFIRMED": confirmed},
                    ),
                    patch("scripts.release.git") as git,
                    self.assertRaises(ValueError),
                ):
                    release.prepare(Path(directory))
                git.assert_not_called()

    @patch("scripts.release.git")
    def test_retry_validates_tag_version_and_master_ancestry_without_bump(self, git):
        git.side_effect = ["", "", 'version = "1.5.2"', "Existing changelog", ""]
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.dict(
                os.environ,
                {"EXISTING_TAG": "v1.5.2", "RETRY_PUBLISH_CONFIRMED": "true"},
            ),
        ):
            root = Path(directory)
            release.prepare(root)
            self.assertEqual((root / "release-version").read_text(), "1.5.2")
            self.assertEqual((root / "release-tag").read_text(), "v1.5.2")
            self.assertEqual(
                (root / "release-body.md").read_text(), "Existing changelog\n"
            )
            self.assertIn(
                unittest.mock.call(
                    "merge-base",
                    "--is-ancestor",
                    "refs/tags/v1.5.2^{commit}",
                    "refs/remotes/origin/master",
                ),
                git.call_args_list,
            )

    def test_retry_rejects_untrusted_ancestry_and_mismatched_project_version(self):
        for responses in (
            ["", subprocess.CalledProcessError(1, "git merge-base")],
            ["", "", 'version = "1.5.3"'],
        ):
            with (
                tempfile.TemporaryDirectory() as directory,
                patch.dict(
                    os.environ,
                    {"EXISTING_TAG": "v1.5.2", "RETRY_PUBLISH_CONFIRMED": "true"},
                ),
                patch("scripts.release.git", side_effect=responses),
            ):
                with self.assertRaises((ValueError, subprocess.CalledProcessError)):
                    release.prepare(Path(directory))
                self.assertFalse((Path(directory) / "release-tag").exists())

    def test_retry_restores_identical_archive_and_validates_manifest(self):
        for tamper in (False, True):
            with (
                self.subTest(tamper=tamper),
                tempfile.TemporaryDirectory() as directory,
            ):
                root = Path(directory)
                (root / "release-tag").write_text("v1.5.2")
                archive = b"original tested ZIP"
                metadata = {
                    "file": "plugin.zip",
                    "tag": "v1.5.2",
                    "commit": "a" * 40,
                    "sha256": hashlib.sha256(archive).hexdigest(),
                }
                response = {
                    "isDraft": True,
                    "tagName": "v1.5.2",
                    "assets": [
                        {"name": "plugin.zip"},
                        {"name": "release-manifest.json"},
                    ],
                }

                def download(*args, **kwargs):
                    (root / "release/plugin.zip").write_bytes(
                        b"corrupt" if tamper else archive
                    )
                    (root / "release/release-manifest.json").write_text(
                        json.dumps(metadata)
                    )

                with (
                    patch.dict(
                        os.environ,
                        {
                            "EXISTING_TAG": "v1.5.2",
                            "GITHUB_REPOSITORY": "owner/repo",
                            "GH_TOKEN": "unused-test-value",
                            "GITHUB_OUTPUT": str(root / "outputs"),
                        },
                    ),
                    patch("scripts.release.release_info", return_value=response),
                    patch("scripts.release.subprocess.run", side_effect=download),
                    patch("scripts.release.git", return_value="a" * 40),
                ):
                    if tamper:
                        with self.assertRaises(ValueError):
                            release.restore(root)
                    else:
                        release.restore(root)
                        release.check(root)
                        self.assertEqual(
                            (root / "release/plugin.zip").read_bytes(), archive
                        )
                        self.assertIn("restored=true", (root / "outputs").read_text())

    @patch("scripts.release.release_info")
    def test_retry_rebuilds_only_when_durable_assets_are_missing(self, info):
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.dict(
                os.environ,
                {
                    "EXISTING_TAG": "v1.5.2",
                    "GITHUB_REPOSITORY": "owner/repo",
                    "GH_TOKEN": "unused-test-value",
                },
            ),
            patch("scripts.release.subprocess.run") as download,
        ):
            root = Path(directory)
            (root / "release-tag").write_text("v1.5.2")
            info.return_value = None
            release.restore(root)
            self.assertFalse((root / "verified-archive.json").exists())
            download.assert_not_called()
            info.side_effect = subprocess.CalledProcessError(
                1, "gh", stderr="Forbidden"
            )
            with self.assertRaises(subprocess.CalledProcessError):
                release.restore(root)
            info.side_effect = None
            response = {
                "isDraft": True,
                "tagName": "v1.5.2",
                "assets": [{"name": "plugin.zip"}],
            }
            info.return_value = response
            release.restore(root)
            download.assert_not_called()
            response["isDraft"] = False
            with self.assertRaises(ValueError):
                release.restore(root)

    @patch("scripts.release.subprocess.run")
    def test_release_lookup_distinguishes_missing_release_from_api_failure(self, run):
        run.return_value = subprocess.CompletedProcess(
            "gh", 1, stdout="", stderr="release not found\n"
        )
        self.assertIsNone(release.release_info("v1.5.2", "owner/repo"))
        run.return_value = subprocess.CompletedProcess(
            "gh", 1, stdout="", stderr="HTTP 403: Forbidden"
        )
        with self.assertRaises(subprocess.CalledProcessError):
            release.release_info("v1.5.2", "owner/repo")
        data = {"isDraft": True, "tagName": "v1.5.2", "assets": []}
        run.return_value = subprocess.CompletedProcess(
            "gh", 0, stdout=json.dumps(data), stderr=""
        )
        self.assertEqual(release.release_info("v1.5.2", "owner/repo"), data)
        self.assertIn("isDraft,tagName,assets", run.call_args.args[0])

    def test_workflow_pushes_and_preserves_assets_before_marketplace(self):
        workflow = (
            Path(__file__).resolve().parents[1] / ".github/workflows/release-plugin.yml"
        ).read_text()
        self.assertLess(
            workflow.index("python scripts/release.py push"),
            workflow.index("name: Preserve frozen ZIP"),
        )
        self.assertLess(
            workflow.index("name: Preserve frozen ZIP"),
            workflow.index("./gradlew publishPlugin"),
        )
        self.assertIn("existing_tag:", workflow)
        self.assertIn("retry_publish_confirmed:", workflow)
        self.assertIn("  workflow_dispatch:", workflow)
        self.assertIn("  push:\n    branches: [master]", workflow)

    def test_automatic_release_defaults_to_patch_and_checks_credentials_first(self):
        workflow = (
            Path(__file__).resolve().parents[1] / ".github/workflows/release-plugin.yml"
        ).read_text()
        self.assertIn("RELEASE_TYPE: ${{ inputs.release_type || 'patch' }}", workflow)
        self.assertNotIn("  pull_request:", workflow)
        self.assertLess(
            workflow.index("name: Check Marketplace publishing credentials"),
            workflow.index("uses: actions/checkout"),
        )

    def test_master_advancement_rejects_atomic_source_and_tag_push(self):
        # Real Git race, entirely inside temporary local repositories: no network.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def run(*args, cwd=root):
                return subprocess.check_output(
                    ["git", *args], cwd=cwd, text=True, stderr=subprocess.PIPE
                ).strip()

            remote = root / "remote.git"
            run("init", "--bare", str(remote))
            local = root / "local"
            run("init", "--initial-branch=master", str(local))
            for key, value in (
                ("user.email", "test@example.test"),
                ("user.name", "Test"),
            ):
                run("config", key, value, cwd=local)
            (local / "version").write_text("1.5.1")
            run("add", "version", cwd=local)
            run("commit", "-m", "Initial", cwd=local)
            run("remote", "add", "origin", str(remote), cwd=local)
            run("push", "origin", "HEAD:master", cwd=local)
            other = root / "other"
            run("clone", str(remote), str(other))
            for key, value in (
                ("user.email", "test@example.test"),
                ("user.name", "Test"),
            ):
                run("config", key, value, cwd=other)
            (other / "advance").write_text("master advanced")
            run("add", "advance", cwd=other)
            run("commit", "-m", "Advance master", cwd=other)
            run("push", "origin", "HEAD:master", cwd=other)
            advanced = run("rev-parse", "HEAD", cwd=other)
            (local / "version").write_text("1.5.2")
            run("commit", "-am", "Release", cwd=local)
            run("tag", "v1.5.2", cwd=local)
            (root / "release-tag").write_text("v1.5.2")
            with patch(
                "scripts.release.git", side_effect=lambda *args: run(*args, cwd=local)
            ):
                with self.assertRaises(subprocess.CalledProcessError):
                    release.push(root)
            self.assertEqual(
                run("rev-parse", "refs/heads/master", cwd=remote), advanced
            )
            self.assertEqual(run("tag", "--list", "v1.5.2", cwd=remote), "")

    def test_missing_or_multiple_archives_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            previous = Path.cwd()
            try:
                os.chdir(directory)
                with self.assertRaises(ValueError):
                    release.freeze(Path(directory))
                dist = Path("build/distributions")
                dist.mkdir(parents=True)
                for name in ("one.zip", "two.zip"):
                    (dist / name).write_bytes(b"zip")
                with self.assertRaises(ValueError):
                    release.freeze(Path(directory))
            finally:
                os.chdir(previous)


if __name__ == "__main__":
    unittest.main()
