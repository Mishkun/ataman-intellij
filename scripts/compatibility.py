"""Discover an exact IntelliJ IDEA candidate and update only compatibility properties."""

import argparse
from dataclasses import dataclass
import html
import json
import os
from pathlib import Path
import re
import urllib.request


API = (
    "https://data.services.jetbrains.com/products/releases?code=IIU&type={}&latest=true"
)
MINIMUM_IDE = "2024.3"
NUMERIC_VERSION = re.compile(r"20[0-9]{2}\.[1-9][0-9]?(?:\.(?:0|[1-9][0-9]*)){0,2}")
NUMERIC_BUILD = re.compile(
    r"[1-9][0-9]{2}\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:\.(?:0|[1-9][0-9]*))?"
)
UNTIL_BUILD = re.compile(
    r'^(?P<prefix>[ \t]*untilBuild[ \t]*=[ \t]*)"(?P<value>[1-9][0-9]{2}\.\*)"(?=[ \t]*\r?$)',
    re.M,
)


def numeric(value):
    return tuple(int(part) for part in value.split("."))


@dataclass(frozen=True)
class Release:
    version: str
    build: str
    type: str

    @property
    def branch(self):
        return int(self.build.split(".")[0])


def parse_releases(payload, expected_type):
    if expected_type not in {"rc", "release"}:
        raise ValueError("Unsupported release type")
    if (
        not isinstance(payload, dict)
        or not isinstance(payload.get("IIU"), list)
        or not payload["IIU"]
    ):
        raise ValueError(f"JetBrains {expected_type} response has no IIU releases")
    releases = {}
    for item in payload["IIU"]:
        if not isinstance(item, dict):
            raise ValueError("Release must be an object")
        version, build, kind = (item.get(key) for key in ("version", "build", "type"))
        if not isinstance(version, str) or not NUMERIC_VERSION.fullmatch(version):
            raise ValueError("Release version must be strictly numeric")
        if not isinstance(build, str) or not NUMERIC_BUILD.fullmatch(build):
            raise ValueError("Release build must be a full numeric build")
        if kind != expected_type:
            raise ValueError("Unexpected release type")
        release = Release(version, build, kind)
        if build in releases and releases[build] != release:
            raise ValueError("Conflicting metadata for the same IDE build")
        releases[build] = release
    return sorted(releases.values(), key=lambda release: numeric(release.build))[-1]


def fetch_release(kind):
    request = urllib.request.Request(
        API.format(kind), headers={"User-Agent": "ataman-compatibility-ci"}
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise ValueError(f"JetBrains API returned HTTP {response.status}")
        # Bound the response as well as the socket wait. No empty/error fallback.
        raw = response.read(2_000_001)
        if len(raw) > 2_000_000:
            raise ValueError("JetBrains API response is too large")
    return parse_releases(json.loads(raw), kind)


def property_value(text, key, required=True):
    matches = re.findall(
        r"^\s*" + re.escape(key) + r"[ \t]*=[ \t]*([^\r\n]*)", text, re.M
    )
    if len(matches) > 1 or (required and not matches):
        raise ValueError(
            f"Expected {'exactly one' if required else 'at most one'} {key} property"
        )
    return matches[0].strip() if matches else None


def select_candidate(stable, rc, current_branch):
    if numeric(rc.build) > numeric(stable.build) and rc.branch > current_branch:
        return rc
    if stable.branch > current_branch:
        return stable
    return None


def supported_until(build):
    assignments = re.findall(r"^[ \t]*untilBuild[ \t]*=", build, re.M)
    values = [match["value"] for match in UNTIL_BUILD.finditer(build)]
    if len(assignments) != 2 or len(values) != 2 or values[0] != values[1]:
        raise ValueError(
            "Expected exactly two matching literal untilBuild branch wildcards"
        )
    return values[0]


def plan_update(build, properties, stable, rc):
    until = supported_until(build)
    property_value(properties, "pluginVerificationVersions", required=False)
    candidate = select_candidate(stable, rc, int(until.split(".")[0]))
    if candidate is None:
        return {
            "changed": False,
            "reason": "No newer platform branch",
            "stable": vars(stable),
            "rc": vars(rc),
        }
    # Stable versions resolve via the product API; RCs use the complete build,
    # never a version label that could resolve to a different patch or channel.
    exact_candidate = candidate.build if candidate.type == "rc" else candidate.version
    matrix = list(dict.fromkeys([MINIMUM_IDE, stable.version, exact_candidate]))
    return {
        "changed": True,
        "candidate": vars(candidate),
        "stable": vars(stable),
        "rc": vars(rc),
        "branch": f"compatibility/idea-{candidate.branch}",
        "until": f"{candidate.branch}.*",
        "matrix": matrix,
        "reason": "New RC"
        if candidate.type == "rc"
        else "Stable fallback (RC already superseded)",
    }


def apply_plan(build, properties, plan):
    # Validate both complete source files before producing either replacement.
    supported_until(build)
    previous_matrix = property_value(
        properties, "pluginVerificationVersions", required=False
    )
    if not plan["changed"]:
        return build, properties
    build = UNTIL_BUILD.sub(
        lambda match: match["prefix"] + '"' + plan["until"] + '"', build
    )
    if previous_matrix is None:
        newline = "\r\n" if "\r\n" in properties else "\n"
        if properties and not properties.endswith("\n"):
            properties += newline
        return build, properties + "pluginVerificationVersions=" + ",".join(
            plan["matrix"]
        ) + newline
    pattern = r"^(?P<prefix>[ \t]*pluginVerificationVersions[ \t]*=[ \t]*)[^\r\n]*"
    properties, count = re.subn(
        pattern,
        lambda match: match["prefix"] + ",".join(plan["matrix"]),
        properties,
        flags=re.M,
    )
    if count != 1:
        raise ValueError("Could not update exactly one pluginVerificationVersions")
    return build, properties


def create_plan(properties, output, build=Path("build.gradle.kts")):
    # Fetch and validate both responses before any edit.
    stable, rc = fetch_release("release"), fetch_release("rc")
    with properties.open(newline="") as source:
        properties_text = source.read()
    with build.open(newline="") as source:
        build_text = source.read()
    plan = plan_update(build_text, properties_text, stable, rc)
    new_build, new_properties = apply_plan(build_text, properties_text, plan)
    output.write_text(json.dumps(plan, indent=2) + "\n")
    if plan["changed"]:
        with build.open("w", newline="") as destination:
            destination.write(new_build)
        with properties.open("w", newline="") as destination:
            destination.write(new_properties)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as destination:
            destination.write(f"changed={str(plan['changed']).lower()}\n")
            if plan["changed"]:
                destination.write(f"branch={plan['branch']}\n")
                destination.write(f"until={plan['until']}\n")
    print(json.dumps(plan, indent=2))


def bounded_excerpt(path, tail=False):
    # Read a bounded prefix/suffix even when a report or Gradle log is huge.
    with path.open("rb") as source:
        size = source.seek(0, 2)
        source.seek(max(0, size - 8192) if tail else 0)
        text = source.read(8192).decode("utf-8", errors="replace")
    text = re.sub(r"\x1b\[[0-9;]*m", "", text)
    lines = text.splitlines()
    excerpt = "\n".join(lines[-16:] if tail else lines[:16])
    excerpt = excerpt[-1200:] if tail else excerpt[:1200]
    truncated = size > 8192 or len(excerpt) < len(text.rstrip("\r\n"))
    # Reports are untrusted text: escaping prevents them from breaking out of
    # a Markdown fence or introducing HTML/hidden links into the generated PR.
    escaped = html.escape(excerpt or "(empty file)")
    truncated = truncated or len(escaped) > 1200
    escaped = escaped[-1200:] if tail else escaped[:1200]
    return (
        "<pre>"
        + escaped
        + "</pre>"
        + ("\nExcerpt truncated; see the full artifact." if truncated else "")
    )


def verifier_findings(reports):
    groups = {}
    for path in sorted(reports.rglob("*.txt")):
        if re.search(
            r"verdict|warning|problem|error|verification-result|usage", path.name, re.I
        ):
            groups.setdefault(path.parent, []).append(path)
    if not groups:
        return "No verdict, warning or error reports were found. Missing reports are not evidence of zero problems."
    sections = []
    # Directory labels retain the actual IDE/build and plugin identifiers from
    # Plugin Verifier's directory layout without guessing an IDE from file text.
    for directory, paths in list(groups.items())[:8]:
        label = html.escape(str(directory.relative_to(reports)))
        sections.append(f"### Report directory: {label}")
        paths.sort(
            key=lambda path: (
                0
                if re.search(r"verdict|verification-result", path.name, re.I)
                else 1
                if re.search(r"problem|error", path.name, re.I)
                else 2,
                path.name,
            )
        )
        for path in paths[:4]:
            sections.append(f"**{html.escape(path.name)}**\n\n{bounded_excerpt(path)}")
        if len(paths) > 4:
            sections.append("Additional report files omitted; see the full artifact.")
    if len(groups) > 8:
        sections.append(
            "Additional IDE report directories omitted; see the full artifact."
        )
    return "\n\n".join(sections)


def failure_logs(statuses):
    if all(value == "success" for value in statuses.values()):
        return ""
    temp = os.environ.get("RUNNER_TEMP")
    sections = []
    if temp:
        root = Path(temp)
        candidates = [("verifier.log", "compatibility-verifier.log")]
        if statuses["build"] != "success":
            candidates.append(("build.log", "compatibility-build.log"))
        for names in candidates:
            path = next(
                (root / name for name in names if (root / name).is_file()), None
            )
            if path:
                sections.append(
                    f"**{path.name} (tail)**\n\n{bounded_excerpt(path, tail=True)}"
                )
    return "\n\n## Failure log excerpts\n\n" + (
        "\n\n".join(sections)
        if sections
        else "No failure log was available in RUNNER_TEMP."
    )


def report(plan_path, output, reports=Path("build/reports/pluginVerifier")):
    plan = json.loads(plan_path.read_text())
    candidate = plan["candidate"]
    statuses = {
        name: os.environ.get(name.upper() + "_STATUS", "not run")
        for name in ("build", "verifier")
    }
    passed = all(value == "success" for value in statuses.values())
    run = os.environ["RUN_URL"]
    artifacts = os.environ.get("REPORT_URL") or run + "#artifacts"
    body = f"""## IntelliJ IDEA compatibility: {plan["until"]}

{plan["reason"]}. IDE **{candidate["version"]}**, exact build **{candidate["build"]}**, type **{candidate["type"]}**.

Only the two literal `untilBuild` values in `build.gradle.kts` and `pluginVerificationVersions` in `gradle.properties` change. Minimum compatibility, compilation platform and plugin version are unchanged.

Explicit verifier matrix: `{",".join(plan["matrix"])}`.

| Check | Outcome |
| --- | --- |
| Build and tests | {statuses["build"]} |
| Plugin Verifier | {statuses["verifier"]} |

**{"Build and verification passed." if passed else "Compatibility checks did not pass. Do not treat this PR as verified."}**

[Workflow run and complete logs]({run}) · [Verifier reports, test results and logs]({artifacts})

## Plugin Verifier findings

{verifier_findings(reports)}
{failure_logs(statuses)}

Warnings and compatibility findings are retained in the full reports above; this summary does not replace reviewing them. UI smoke testing was not performed. No automatic merge is enabled.
"""
    output.write_text(body)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="command", required=True)
    plan = subcommands.add_parser("plan")
    plan.add_argument("--properties", type=Path, default=Path("gradle.properties"))
    plan.add_argument("--build", type=Path, default=Path("build.gradle.kts"))
    plan.add_argument("--output", type=Path, required=True)
    body = subcommands.add_parser("report")
    body.add_argument("--plan", type=Path, required=True)
    body.add_argument("--output", type=Path, required=True)
    body.add_argument(
        "--reports", type=Path, default=Path("build/reports/pluginVerifier")
    )
    args = parser.parse_args()
    if args.command == "plan":
        create_plan(args.properties, args.output, args.build)
    else:
        report(args.plan, args.output, args.reports)


if __name__ == "__main__":
    main()
