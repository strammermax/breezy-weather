"""Generate the offline in-app release history from Git tags and commit subjects."""

from __future__ import annotations

import json
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "release-notes.json"
MAX_RELEASES = 10


def git(*args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(ROOT), *args],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return result.stdout.strip()


def visible_commits(revision: str) -> list[str]:
    output = git("log", "--no-merges", "--pretty=format:%s", revision)
    hidden_prefixes = ("merge ", "wip", "settings", "update app information")
    return [
        line.strip()
        for line in output.splitlines()
        if line.strip() and not line.strip().lower().startswith(hidden_prefixes)
    ]


def main() -> None:
    raw_tags = git(
        "for-each-ref",
        "--sort=-creatordate",
        "--format=%(refname:short)|%(creatordate:short)",
        "refs/tags",
    )
    all_tags = [line.split("|", 1) for line in raw_tags.splitlines() if "|" in line]
    releases: list[dict[str, object]] = []
    if all_tags:
        unreleased = visible_commits(f"{all_tags[0][0]}..HEAD")
        if unreleased:
            releases.append(
                {
                    "version": f"Niet uitgebracht (+{len(unreleased)})",
                    "date": "",
                    "changes": unreleased,
                }
            )

    tag_limit = MAX_RELEASES - len(releases)
    tags = all_tags[:tag_limit]
    for index, (tag, date) in enumerate(tags):
        older_tag = all_tags[index + 1][0] if index + 1 < len(all_tags) else None
        revision = f"{older_tag}..{tag}" if older_tag else tag
        releases.append(
            {
                "version": tag.lstrip("v"),
                "date": date,
                "changes": visible_commits(revision),
            }
        )

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    content = json.dumps(releases, ensure_ascii=False, indent=2) + "\n"
    if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != content:
        OUTPUT.write_text(content, encoding="utf-8")
        print(f"Updated {OUTPUT.relative_to(ROOT)} with {len(releases)} releases")
    else:
        print(f"No changes in {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
