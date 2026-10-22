import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts import release_beta as rc


COMMIT = "a" * 40


def archive(version=rc.VERSION, since="243", until="262.*", plugin_id=rc.PLUGIN_ID):
    jar = io.BytesIO()
    with zipfile.ZipFile(jar, "w") as content:
        content.writestr(
            "META-INF/plugin.xml",
            f"""<idea-plugin>
<id>{plugin_id}</id><version>{version}</version>
<idea-version since-build="{since}" until-build="{until}"/>
</idea-plugin>""",
        )
    result = io.BytesIO()
    with zipfile.ZipFile(result, "w") as content:
        content.writestr("ataman-plugin/lib/ataman-plugin.jar", jar.getvalue())
    return result.getvalue()


def release(draft=True):
    return {
        "tagName": rc.TAG,
        "isDraft": draft,
        "isPrerelease": True,
        "targetCommitish": COMMIT,
        "assets": [{"name": rc.ARCHIVE}, {"name": rc.MANIFEST}],
    }


class ReleaseBetaTest(unittest.TestCase):
    def test_descriptor_accepts_exact_beta_version(self):
        self.assertEqual(rc.descriptor(archive())["version"], "2.1.1")
        self.assertEqual(rc.TAG, "v2.1.1-beta")
        self.assertEqual(rc.CHANNEL, "beta")

    def test_descriptor_rejects_other_versions_old_bounds_and_wrong_plugin(self):
        for kwargs in (
            {"version": "2.0.0"},
            {"version": "2.0.0-rc2"},
            {"version": "2.1.1-beta"},
            {"version": "2.1.0"},
            {"until": "261.*"},
            {"since": "242"},
            {"plugin_id": "other"},
        ):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                rc.descriptor(archive(**kwargs))

    def test_missing_descriptor_rejected(self):
        result = io.BytesIO()
        with zipfile.ZipFile(result, "w"):
            pass
        with self.assertRaises(ValueError):
            rc.descriptor(result.getvalue())

    def test_context_requires_manual_canonical_branch(self):
        valid = {
            "GITHUB_REPOSITORY": rc.REPOSITORY,
            "GITHUB_REF": rc.REF,
            "GITHUB_EVENT_NAME": "workflow_dispatch",
            "GITHUB_SHA": COMMIT,
        }
        for key, wrong in (
            ("GITHUB_REPOSITORY", "fork/repo"),
            ("GITHUB_REF", "refs/heads/master"),
            ("GITHUB_REF", "refs/heads/release/v2-rc3"),
            ("GITHUB_REF", "refs/tags/v2.1.1-beta"),
            ("GITHUB_EVENT_NAME", "push"),
        ):
            with (
                self.subTest(key=key),
                patch.dict(os.environ, {**valid, key: wrong}, clear=True),
            ):
                with self.assertRaises(ValueError):
                    rc.context()

    def test_context_checks_head_ancestry_clean_source_and_exact_version(self):
        env = {
            "GITHUB_REPOSITORY": rc.REPOSITORY,
            "GITHUB_REF": rc.REF,
            "GITHUB_EVENT_NAME": "workflow_dispatch",
            "GITHUB_SHA": COMMIT,
        }
        with (
            patch.dict(os.environ, env),
            patch.object(rc, "run", side_effect=[COMMIT, "", ""]) as run,
        ):
            with patch.object(
                Path, "read_text", return_value=f'version = "{rc.VERSION}"\n'
            ):
                self.assertEqual(rc.context(), COMMIT)
            self.assertIn(
                unittest.mock.call(
                    "git", "merge-base", "--is-ancestor", rc.REWRITE, COMMIT
                ),
                run.call_args_list,
            )
        for answers, source in (
            (["b" * 40], f'version = "{rc.VERSION}"'),
            ([COMMIT, "", " M build.gradle.kts"], f'version = "{rc.VERSION}"'),
            ([COMMIT, "", ""], 'version = "2.0.0"'),
        ):
            with (
                patch.dict(os.environ, env),
                patch.object(rc, "run", side_effect=answers),
            ):
                with (
                    patch.object(Path, "read_text", return_value=source),
                    self.assertRaises(ValueError),
                ):
                    rc.context()

    def test_any_existing_tag_or_release_blocks_without_mutation(self):
        for tag, info in ((COMMIT, None), ("", release()), ("", release(False))):
            with (
                self.subTest(tag=tag),
                patch.object(rc, "remote_tag", return_value=tag),
            ):
                with (
                    patch.object(rc, "release_info", return_value=info),
                    self.assertRaises(ValueError),
                ):
                    rc.ensure_unused()

    def test_absent_tag_and_release_allowed(self):
        with (
            patch.object(rc, "remote_tag", return_value=""),
            patch.object(rc, "release_info", return_value=None),
        ):
            rc.ensure_unused()

    def test_only_explicit_release_not_found_is_absence(self):
        for error in ("HTTP 403", "network unavailable", "release not found\nextra"):
            with patch.object(
                subprocess,
                "run",
                return_value=subprocess.CompletedProcess([], 1, "", error),
            ):
                with self.assertRaises(RuntimeError):
                    rc.release_info()
        with patch.object(
            subprocess,
            "run",
            return_value=subprocess.CompletedProcess([], 1, "", "release not found\n"),
        ):
            self.assertIsNone(rc.release_info())

    def test_release_must_be_matching_prerelease_with_exact_assets(self):
        rc.check_release(release(), COMMIT, draft=True)
        for changes in (
            {"isPrerelease": False},
            {"isDraft": False},
            {"targetCommitish": "master"},
            {"tagName": "v2.0.0"},
            {"assets": []},
        ):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                rc.check_release({**release(), **changes}, COMMIT, draft=True)

    def test_tag_exact_commit_including_annotated_tags(self):
        for refs in (
            f"{COMMIT}\trefs/tags/{rc.TAG}",
            f"{'b' * 40}\trefs/tags/{rc.TAG}\n{COMMIT}\trefs/tags/{rc.TAG}^{{}}",
        ):
            with patch.object(rc, "remote_tag", return_value=refs):
                rc.check_tag(COMMIT, required=True)
        for refs in ("", f"{'b' * 40}\trefs/tags/{rc.TAG}"):
            with (
                patch.object(rc, "remote_tag", return_value=refs),
                self.assertRaises(ValueError),
            ):
                rc.check_tag(COMMIT, required=True)

    def test_manifest_and_zip_are_bound_to_commit(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            data = archive()
            (directory / rc.ARCHIVE).write_bytes(data)
            (directory / rc.MANIFEST).write_text(json.dumps(rc.metadata(data, COMMIT)))
            rc.check_frozen(directory, COMMIT)
            with self.assertRaises(ValueError):
                rc.check_frozen(directory, "b" * 40)
            # Valid descriptor, different ZIP bytes: the checksum must still fail.
            (directory / rc.ARCHIVE).write_bytes(data + b"changed")
            with self.assertRaises(ValueError):
                rc.check_frozen(directory, COMMIT)
            (directory / rc.ARCHIVE).write_bytes(archive(until="261.*"))
            with self.assertRaises(ValueError):
                rc.check_frozen(directory, COMMIT)

    def test_freeze_copies_only_the_exact_tested_distribution(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / rc.ARCHIVE
            data = archive()
            source.write_bytes(data)
            frozen = Path(temp) / "frozen"
            with (
                patch.object(rc, "context", return_value=COMMIT),
                patch.object(Path, "glob", return_value=[source]),
                patch.object(rc, "directory", return_value=frozen),
            ):
                rc.freeze()
            self.assertEqual((frozen / rc.ARCHIVE).read_bytes(), data)
            self.assertEqual((frozen / rc.ARCHIVE).stat().st_mode & 0o222, 0)
            source.write_bytes(b"later build output is not the published artifact")
            rc.check_frozen(frozen, COMMIT)
            for sources in ([], [source, source], [source.with_name("other.zip")]):
                with (
                    self.subTest(sources=sources),
                    patch.object(rc, "context", return_value=COMMIT),
                    patch.object(Path, "glob", return_value=sources),
                    self.assertRaises(ValueError),
                ):
                    rc.freeze()

    def test_ancestry_failure_stops_source_validation(self):
        env = {
            "GITHUB_REPOSITORY": rc.REPOSITORY,
            "GITHUB_REF": rc.REF,
            "GITHUB_EVENT_NAME": "workflow_dispatch",
            "GITHUB_SHA": COMMIT,
        }
        with (
            patch.dict(os.environ, env, clear=True),
            patch.object(
                rc,
                "run",
                side_effect=[
                    COMMIT,
                    subprocess.CalledProcessError(1, ["git", "merge-base"]),
                ],
            ),
            self.assertRaises(subprocess.CalledProcessError),
        ):
            rc.context()

    def test_draft_preserves_tag_and_downloaded_assets_without_publishing(self):
        with (
            tempfile.TemporaryDirectory() as temp,
            patch.dict(os.environ, {"RUNNER_TEMP": temp}),
        ):
            frozen = rc.directory()
            frozen.mkdir()
            data = archive()
            (frozen / rc.ARCHIVE).write_bytes(data)
            (frozen / rc.MANIFEST).write_text(json.dumps(rc.metadata(data, COMMIT)))
            calls = []

            def run(*args):
                calls.append(args)
                if args[:3] == ("gh", "release", "download"):
                    destination = Path(args[args.index("--dir") + 1])
                    for name in (rc.ARCHIVE, rc.MANIFEST):
                        (destination / name).write_bytes((frozen / name).read_bytes())
                return ""

            with (
                patch.object(rc, "context", return_value=COMMIT),
                patch.object(rc, "ensure_unused"),
            ):
                with patch.object(rc, "release_info", return_value=release()):
                    with (
                        patch.object(rc, "check_tag"),
                        patch.object(rc, "run", side_effect=run),
                    ):
                        rc.draft()
            self.assertEqual(
                [call[:3] for call in calls],
                [
                    ("gh", "api", "--method"),
                    ("gh", "release", "create"),
                    ("gh", "release", "download"),
                ],
            )
            self.assertIn(f"ref=refs/tags/{rc.TAG}", calls[0])
            self.assertIn(f"sha={COMMIT}", calls[0])
            self.assertIn("POST", calls[0])
            self.assertIn("--draft", calls[1])
            self.assertIn("--verify-tag", calls[1])
            self.assertEqual(calls[1][calls[1].index("--target") + 1], COMMIT)
            self.assertIn("--prerelease", calls[1])
            self.assertIn("--latest=false", calls[1])

    def test_bad_draft_is_never_published(self):
        with (
            patch.object(rc, "context", return_value=COMMIT),
            patch.object(rc, "directory", return_value=Path("fake")),
        ):
            with patch.object(rc, "check_frozen"), patch.object(rc, "ensure_unused"):
                with patch.object(
                    rc,
                    "release_info",
                    return_value={**release(), "isPrerelease": False},
                ):
                    with patch.object(rc, "check_tag"), patch.object(rc, "run") as run:
                        with self.assertRaises(ValueError):
                            rc.finalize()
                    run.assert_not_called()

    def test_preflight_fails_closed_without_secret_presence(self):
        for value in ("", "false", "True"):
            with (
                self.subTest(value=value),
                patch.dict(os.environ, {"HAS_MARKETPLACE_TOKEN": value}),
                patch.object(rc, "context", return_value=COMMIT),
                patch.object(rc, "ensure_unused") as unused,
                self.assertRaises(ValueError),
            ):
                rc.preflight()
            unused.assert_not_called()
        with (
            patch.dict(os.environ, {"HAS_MARKETPLACE_TOKEN": "true"}),
            patch.object(rc, "context", return_value=COMMIT),
            patch.object(rc, "ensure_unused") as unused,
        ):
            rc.preflight()
            unused.assert_called_once()

    def test_manifest_rejects_wrong_channel_tag_version_and_checksum(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            data = archive()
            (directory / rc.ARCHIVE).write_bytes(data)
            for changes in (
                {"channel": "default"},
                {"channel": ""},
                {"channel": None},
                {"tag": "v2.1.1"},
                {"version": "2.1.0"},
                {"sha256": "0" * 64},
                {"commit": "b" * 40},
            ):
                with self.subTest(changes=changes):
                    (directory / rc.MANIFEST).write_text(
                        json.dumps({**rc.metadata(data, COMMIT), **changes})
                    )
                    with self.assertRaises(ValueError):
                        rc.check_frozen(directory, COMMIT)

    def test_finalize_checks_frozen_assets_before_edit_and_preserves_prerelease(self):
        with (
            patch.object(rc, "check") as check,
            patch.object(rc, "context", return_value=COMMIT),
            patch.object(rc, "run") as run,
            patch.object(rc, "release_info", return_value=release(False)),
            patch.object(rc, "check_tag") as tag,
        ):
            rc.finalize()
        check.assert_called_once()
        self.assertIn("--draft=false", run.call_args.args)
        self.assertIn("--prerelease", run.call_args.args)
        self.assertIn("--latest=false", run.call_args.args)
        tag.assert_called_once_with(COMMIT, required=True)

    def test_existing_tag_never_reuploads_or_finalizes_on_rerun(self):
        with (
            patch.object(rc, "context", return_value=COMMIT),
            patch.object(rc, "check_frozen"),
            patch.object(rc, "directory", return_value=Path("fake")),
            patch.object(
                rc, "remote_tag", return_value=f"{COMMIT}\trefs/tags/{rc.TAG}"
            ),
            patch.object(rc, "run") as run,
            self.assertRaises(ValueError),
        ):
            rc.draft()
        run.assert_not_called()

    def test_corrupt_download_is_never_finalized(self):
        with (
            tempfile.TemporaryDirectory() as temp,
            patch.dict(os.environ, {"RUNNER_TEMP": temp}),
        ):
            frozen = rc.directory()
            frozen.mkdir()
            data = archive()
            (frozen / rc.ARCHIVE).write_bytes(data)
            (frozen / rc.MANIFEST).write_text(json.dumps(rc.metadata(data, COMMIT)))
            calls = []

            def run(*args):
                calls.append(args)
                if args[:3] == ("gh", "release", "download"):
                    destination = Path(args[args.index("--dir") + 1])
                    (destination / rc.ARCHIVE).write_bytes(archive(until="261.*"))
                    (destination / rc.MANIFEST).write_bytes(
                        (frozen / rc.MANIFEST).read_bytes()
                    )
                return ""

            with (
                patch.object(rc, "context", return_value=COMMIT),
                patch.object(rc, "check_tag"),
                patch.object(rc, "release_info", return_value=release()),
                patch.object(rc, "run", side_effect=run),
                self.assertRaises(ValueError),
            ):
                rc.finalize()
            self.assertEqual(
                [call[:3] for call in calls], [("gh", "release", "download")]
            )

    def test_workflow_order_and_fixed_beta_configuration(self):
        source = Path("build.gradle.kts").read_text()
        self.assertIn('channels.set(listOf("beta"))', source)
        self.assertIn("channels.disallowChanges()", source)
        self.assertIn('channels.get() == listOf("beta")', source)
        publishing = source[source.index("val publishingArchive =") :]
        self.assertNotIn("orElse", publishing)
        self.assertNotIn("dependsOn", publishing)
        self.assertIn("setDependsOn(emptyList<Any>())", publishing)
        self.assertIn("archiveFiles.setFrom(publishingArchive)", publishing)
        workflow = Path(".github/workflows/release-plugin.yml").read_text()
        stages = [
            "release_beta.py preflight",
            "./gradlew build verifyPlugin",
            "release_beta.py freeze",
            "release_beta.py draft",
            "./gradlew publishPlugin",
            "release_beta.py finalize",
        ]
        positions = [workflow.index(stage) for stage in stages]
        self.assertEqual(positions, sorted(positions))
        self.assertNotIn("  push:", workflow)
        self.assertIn("github.ref == 'refs/heads/2.x'", workflow)
        self.assertEqual(
            workflow.count(
                "JETBRAINS_MARKETPLACE_TOKEN: ${{ secrets.JETBRAINS_MARKETPLACE_TOKEN }}"
            ),
            1,
        )
        publish = workflow[
            workflow.index('test -n "$JETBRAINS_MARKETPLACE_TOKEN"') : workflow.index(
                "- name: Publish preserved"
            )
        ]
        self.assertEqual(publish.count("release_beta.py check"), 2)
        self.assertIn(
            './gradlew publishPlugin "-PpublishingArchive=$FROZEN_ZIP"', publish
        )
        self.assertIn(
            "refs/heads/master",
            Path(".github/workflows/release-snapshot.yml").read_text(),
        )


if __name__ == "__main__":
    unittest.main()
