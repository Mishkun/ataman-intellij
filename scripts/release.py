"""Release-only semver bump and frozen tested archive (stdlib)."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


VERSION = re.compile(
    r'^version = "(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"$', re.M
)
TAG = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")


def output(**values):
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as destination:
            for key, value in values.items():
                destination.write(f"{key}={value}\n")


def bump(source, kind):
    matches = list(VERSION.finditer(source))
    if len(matches) != 1 or kind not in {"major", "minor", "patch"}:
        raise ValueError("Expected one literal project semver and a valid release type")
    numbers = [int(part) for part in matches[0].groups()]
    index = {"major": 0, "minor": 1, "patch": 2}[kind]
    numbers[index] += 1
    numbers[index + 1 :] = [0] * (2 - index)
    version = ".".join(map(str, numbers))
    return VERSION.sub(f'version = "{version}"', source), version


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def prepare(temp):
    existing = os.environ.get("EXISTING_TAG", "")
    if existing:
        if not TAG.fullmatch(existing):
            raise ValueError("existing_tag must be exactly vMAJOR.MINOR.PATCH")
        if os.environ.get("RETRY_PUBLISH_CONFIRMED") != "true":
            raise ValueError(
                "Check Marketplace first; retry requires confirmation that this version is not published"
            )
        # Only a tag fetched from this repository and reachable from trusted
        # master is accepted. Never accept an arbitrary commit/ref or fork URL.
        git(
            "fetch",
            "origin",
            "+refs/heads/master:refs/remotes/origin/master",
            f"refs/tags/{existing}:refs/tags/{existing}",
        )
        git(
            "merge-base",
            "--is-ancestor",
            f"refs/tags/{existing}^{{commit}}",
            "refs/remotes/origin/master",
        )
        source = git("show", f"refs/tags/{existing}:build.gradle.kts")
        matches = list(VERSION.finditer(source))
        if len(matches) != 1 or ".".join(matches[0].groups()) != existing[1:]:
            raise ValueError("Tag does not match its literal project version")
        body = git("show", f"refs/tags/{existing}:changelog.txt") + "\n"
        git("checkout", "--detach", f"refs/tags/{existing}")
        (temp / "release-body.md").write_text(body)
        (temp / "release-version").write_text(existing[1:])
        (temp / "release-tag").write_text(existing)
        output(version=existing[1:])
        return
    path = Path("build.gradle.kts")
    source, version = bump(path.read_text(), os.environ["RELEASE_TYPE"])
    tag = f"v{version}"
    if tag in git("tag", "--list", tag).splitlines():
        raise ValueError(f"Tag {tag} already exists")
    try:
        previous = git("describe", "--tags", "--abbrev=0")
    except subprocess.CalledProcessError:
        previous = ""
    commits = git(
        "log",
        *([f"{previous}..HEAD"] if previous else []),
        "--pretty=format:- %s",
        "--no-merges",
    )
    notes = os.environ.get("ADDITIONAL_NOTES", "").strip()
    body = "\n\n".join(part for part in (notes, commits) if part) + "\n"
    path.write_text(source)
    Path("changelog.txt").write_text(body)
    (temp / "release-body.md").write_text(body)
    (temp / "release-version").write_text(version)
    (temp / "release-tag").write_text(tag)
    output(version=version)


def write_manifest(temp, archive, metadata):
    (temp / "release" / "release-manifest.json").write_text(
        json.dumps(metadata, indent=2) + "\n"
    )
    (temp / "verified-archive.json").write_text(
        json.dumps({**metadata, "path": str(archive)})
    )


def freeze(temp):
    archives = list(Path("build/distributions").glob("*.zip"))
    if len(archives) != 1:
        raise ValueError("Expected exactly one tested distribution ZIP")
    directory = temp / "release"
    directory.mkdir(exist_ok=False)
    archive = (directory / archives[0].name).resolve()
    shutil.copyfile(archives[0], archive)
    archive.chmod(0o444)
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    write_manifest(
        temp,
        archive,
        {
            "file": archive.name,
            "sha256": digest,
            "tag": (temp / "release-tag").read_text(),
            "commit": git("rev-parse", "HEAD"),
        },
    )
    output(archive=archive)


def check(temp):
    expected = json.loads((temp / "verified-archive.json").read_text())
    if (
        hashlib.sha256(Path(expected["path"]).read_bytes()).hexdigest()
        != expected["sha256"]
    ):
        raise ValueError("Frozen tested archive changed")
    output(archive=expected["path"])


def release_info(tag, repository):
    # gh resolves drafts by pending tag via GraphQL; REST /releases/tags alone
    # only finds published releases and cannot restore frozen draft assets.
    command = [
        "gh",
        "release",
        "view",
        tag,
        "--repo",
        repository,
        "--json",
        "isDraft,tagName,assets",
    ]
    result = subprocess.run(command, capture_output=True, text=True, timeout=60)
    if result.returncode:
        if result.stderr.strip() == "release not found":
            return None
        raise subprocess.CalledProcessError(
            result.returncode, command, output=result.stdout, stderr=result.stderr
        )
    return json.loads(result.stdout)


def restore(temp):
    if not os.environ.get("EXISTING_TAG"):
        output(restored="false")
        return
    tag = (temp / "release-tag").read_text()
    repository = os.environ["GITHUB_REPOSITORY"]
    release = release_info(tag, repository)
    if release is None:
        # The earlier run never reached durable draft storage, so it could not
        # have reached Marketplace. Rebuild only this already validated tag.
        output(restored="false")
        return
    if not release.get("isDraft") or release.get("tagName") != tag:
        raise ValueError(
            "Retry requires this tag's draft release; inspect already published releases manually"
        )
    names = [asset["name"] for asset in release["assets"]]
    archives = [name for name in names if name.endswith(".zip")]
    if len(archives) > 1:
        raise ValueError("Draft contains ambiguous ZIP assets")
    if "release-manifest.json" not in names or not archives:
        output(restored="false")
        return
    name = archives[0]
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,199}\.zip", name):
        raise ValueError("Unsafe archive asset name")
    directory = temp / "release"
    directory.mkdir(exist_ok=False)
    subprocess.run(
        [
            "gh",
            "release",
            "download",
            tag,
            "--repo",
            repository,
            "--dir",
            str(directory),
            "--pattern",
            name,
            "--pattern",
            "release-manifest.json",
        ],
        check=True,
        timeout=300,
    )
    metadata = json.loads((directory / "release-manifest.json").read_text())
    if (
        metadata.get("file") != name
        or metadata.get("tag") != tag
        or metadata.get("commit") != git("rev-parse", "HEAD")
        or not re.fullmatch(r"[0-9a-f]{64}", str(metadata.get("sha256", "")))
    ):
        raise ValueError("Frozen assets do not match the trusted tag/commit")
    archive = (directory / name).resolve()
    if hashlib.sha256(archive.read_bytes()).hexdigest() != metadata["sha256"]:
        raise ValueError("Downloaded frozen archive checksum mismatch")
    archive.chmod(0o444)
    write_manifest(temp, archive, metadata)
    output(restored="true", archive=archive)


def push(temp):
    tag = (temp / "release-tag").read_text()
    git("push", "--atomic", "origin", "HEAD:refs/heads/master", f"refs/tags/{tag}")


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in {
        "prepare",
        "freeze",
        "check",
        "restore",
        "push",
    }:
        sys.exit("Usage: release.py prepare|freeze|check|restore|push")
    globals()[sys.argv[1]](Path(os.environ["RUNNER_TEMP"]))
