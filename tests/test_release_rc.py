import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts import release_rc as rc


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


class ReleaseRcTest(unittest.TestCase):
    def test_descriptor_accepts_exact_rc3(self):
        self.assertEqual(rc.descriptor(archive())["version"], "2.0.0-rc3")

    def test_descriptor_rejects_stable_old_bounds_and_wrong_plugin(self):
        for kwargs in (
            {"version": "2.0.0"},
            {"version": "2.0.0-rc2"},
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
            (directory / rc.ARCHIVE).write_bytes(archive(until="261.*"))
            with self.assertRaises(ValueError):
                rc.check_frozen(directory, COMMIT)

    def test_publish_checks_downloaded_draft_before_publishing(self):
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
                with patch.object(
                    rc, "release_info", side_effect=[release(), release(False)]
                ):
                    with (
                        patch.object(rc, "check_tag"),
                        patch.object(rc, "run", side_effect=run),
                    ):
                        rc.publish()
            self.assertEqual(
                [call[2] for call in calls], ["create", "download", "edit"]
            )
            self.assertIn("--draft", calls[0])
            self.assertIn("--target", calls[0])
            self.assertEqual(calls[0][calls[0].index("--target") + 1], COMMIT)
            self.assertIn("--prerelease", calls[2])
            self.assertIn("--latest=false", calls[2])

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
                    with patch.object(rc, "run") as run, self.assertRaises(ValueError):
                        rc.publish()
                    self.assertEqual(run.call_count, 1)


if __name__ == "__main__":
    unittest.main()
