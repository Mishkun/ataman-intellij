"""One guarded GitHub-only RC3 release. Existing tags/releases fail closed."""

import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile


REPOSITORY = "Mishkun/ataman-intellij"
REF = "refs/heads/release/v2-rc3"
VERSION = "2.0.0-rc3"
TAG = f"v{VERSION}"
PLUGIN_ID = "io.github.mishkun.ataman-plugin"
ARCHIVE = f"ataman-plugin-{VERSION}.zip"
MANIFEST = "release-manifest.json"
REWRITE = "dc7209ab85473ca2544e06a39f028b620d240025"


def run(*args):
    return subprocess.check_output(args, text=True, timeout=120).strip()


def context():
    if (
        os.environ.get("GITHUB_REPOSITORY") != REPOSITORY
        or os.environ.get("GITHUB_REF") != REF
        or os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch"
    ):
        raise ValueError(
            "RC3 requires manual dispatch on the canonical candidate branch"
        )
    commit = run("git", "rev-parse", "HEAD")
    if not re.fullmatch(r"[0-9a-f]{40}", commit) or commit != os.environ.get(
        "GITHUB_SHA"
    ):
        raise ValueError("Checkout must be the exact dispatched source commit")
    run("git", "merge-base", "--is-ancestor", REWRITE, commit)
    if run("git", "status", "--porcelain", "--untracked-files=no"):
        raise ValueError("Tracked source changed after checkout")
    versions = re.findall(
        r'^version = "([^"]+)"$', Path("build.gradle.kts").read_text(), re.M
    )
    if versions != [VERSION]:
        raise ValueError("Expected the exact literal RC3 project version")
    return commit


def release_info():
    command = [
        "gh",
        "release",
        "view",
        TAG,
        "--repo",
        REPOSITORY,
        "--json",
        "tagName,isDraft,isPrerelease,targetCommitish,assets",
    ]
    result = subprocess.run(command, capture_output=True, text=True, timeout=120)
    if result.returncode:
        if result.stderr.strip() == "release not found":
            return None
        raise RuntimeError(
            f"Cannot inspect existing RC release: {result.stderr.strip()}"
        )
    return json.loads(result.stdout)


def remote_tag():
    return run(
        "git", "ls-remote", "origin", f"refs/tags/{TAG}", f"refs/tags/{TAG}^{{}}"
    )


def ensure_unused():
    if remote_tag() or release_info() is not None:
        raise ValueError(
            "RC3 tag/release already exists; refusing to overwrite or resume automatically"
        )


def preflight():
    context()
    ensure_unused()


def descriptor(data):
    descriptors = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in archive.namelist():
            if name.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(archive.read(name))) as jar:
                    if "META-INF/plugin.xml" in jar.namelist():
                        descriptors.append(
                            ET.fromstring(jar.read("META-INF/plugin.xml"))
                        )
    if len(descriptors) != 1:
        raise ValueError("Expected exactly one plugin descriptor in distribution")
    root = descriptors[0]
    bounds = root.find("idea-version")
    if (
        root.findtext("id") != PLUGIN_ID
        or root.findtext("version") != VERSION
        or bounds is None
        or bounds.get("since-build") != "243"
        or bounds.get("until-build") != "262.*"
    ):
        raise ValueError("Distribution descriptor must be RC3 with bounds 243..262.*")
    return {
        "id": PLUGIN_ID,
        "version": VERSION,
        "sinceBuild": "243",
        "untilBuild": "262.*",
    }


def metadata(data, commit):
    return {
        "file": ARCHIVE,
        "sha256": hashlib.sha256(data).hexdigest(),
        "tag": TAG,
        "commit": commit,
        **descriptor(data),
    }


def directory():
    return Path(os.environ["RUNNER_TEMP"]) / "rc3-release"


def freeze():
    commit = context()
    archives = list(Path("build/distributions").glob("*.zip"))
    if len(archives) != 1 or archives[0].name != ARCHIVE:
        raise ValueError("Expected exactly the tested RC3 distribution ZIP")
    data = archives[0].read_bytes()
    manifest = metadata(data, commit)
    destination = directory()
    destination.mkdir(exist_ok=False)
    frozen = destination / ARCHIVE
    frozen.write_bytes(data)
    frozen.chmod(0o444)
    (destination / MANIFEST).write_text(json.dumps(manifest, indent=2) + "\n")


def check_frozen(destination, commit):
    data = (destination / ARCHIVE).read_bytes()
    if json.loads((destination / MANIFEST).read_text()) != metadata(data, commit):
        raise ValueError(
            "Frozen archive/manifest does not match the tested source and descriptor"
        )


def check_release(release, commit, draft):
    if (
        release is None
        or release.get("tagName") != TAG
        or release.get("isDraft") is not draft
        or release.get("isPrerelease") is not True
        or release.get("targetCommitish") != commit
        or sorted(asset["name"] for asset in release.get("assets", []))
        != sorted([ARCHIVE, MANIFEST])
    ):
        raise ValueError(
            "Unexpected release identity/state/assets; refusing publication"
        )


def check_tag(commit, required=False):
    refs = remote_tag().splitlines()
    if not refs and required:
        raise ValueError("Published RC tag is missing")
    if refs:
        peeled = [line.split()[0] for line in refs if line.endswith("^{}")]
        target = peeled[0] if peeled else refs[0].split()[0]
        if target != commit:
            raise ValueError("RC tag does not point to the tested source commit")


def publish():
    commit = context()
    frozen = directory()
    check_frozen(frozen, commit)
    ensure_unused()
    # No source commit or tag push. GitHub creates the release tag at this exact SHA.
    run(
        "gh",
        "release",
        "create",
        TAG,
        "--repo",
        REPOSITORY,
        "--target",
        commit,
        "--title",
        f"Ataman {VERSION}",
        "--notes",
        "RC3 of the v2 keyboard dispatcher rewrite. GitHub prerelease only; not published to Marketplace.",
        "--draft",
        "--prerelease",
        "--latest=false",
        str(frozen / ARCHIVE),
        str(frozen / MANIFEST),
    )
    check_release(release_info(), commit, draft=True)
    with tempfile.TemporaryDirectory(
        prefix="rc3-download-", dir=os.environ["RUNNER_TEMP"]
    ) as downloaded:
        run("gh", "release", "download", TAG, "--repo", REPOSITORY, "--dir", downloaded)
        downloaded = Path(downloaded)
        check_frozen(downloaded, commit)
        for name in (ARCHIVE, MANIFEST):
            if (downloaded / name).read_bytes() != (frozen / name).read_bytes():
                raise ValueError("Uploaded assets differ from frozen tested assets")
    check_frozen(frozen, commit)
    check_tag(commit)
    run(
        "gh",
        "release",
        "edit",
        TAG,
        "--repo",
        REPOSITORY,
        "--draft=false",
        "--prerelease",
        "--latest=false",
    )
    check_release(release_info(), commit, draft=False)
    check_tag(commit, required=True)


if __name__ == "__main__":
    commands = {"preflight": preflight, "freeze": freeze, "publish": publish}
    if len(sys.argv) != 2 or sys.argv[1] not in commands:
        sys.exit("Usage: release_rc.py preflight|freeze|publish")
    commands[sys.argv[1]]()
