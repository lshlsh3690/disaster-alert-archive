#!/usr/bin/env python3
"""Connect Claude's skill discovery directory to the shared skill source.

Uses a junction on Windows (no administrator rights) and a relative directory
symlink on other platforms. --check is read-only. Existing paths are never removed.
"""

import argparse
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check the shared link without modifying it")
    args = parser.parse_args()
    target = ROOT / ".agents" / "skills"
    alias = ROOT / ".claude" / "skills"
    if not target.is_dir() or not any(target.glob("*/SKILL.md")):
        parser.error("Shared skill definitions are missing from .agents/skills")
    if not alias.parent.resolve().is_relative_to(ROOT.resolve()):
        parser.error("The Claude directory must stay inside this checkout")
    if os.path.lexists(alias):
        try:
            linked = alias.is_dir() and os.path.samefile(alias, target)
        except OSError:
            linked = False
        if not linked:
            parser.error(".claude/skills already exists and does not point to .agents/skills; inspect it before creating the link")
    elif args.check:
        print("Missing shared skill link: .claude/skills -> .agents/skills", file=sys.stderr)
        return 1
    else:
        alias.parent.mkdir(parents=True, exist_ok=True)
        if os.name == "nt":
            env = os.environ.copy()
            env["SHARED_SKILL_ALIAS"] = str(alias)
            env["SHARED_SKILL_TARGET"] = str(target)
            subprocess.run([
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "New-Item -ItemType Junction -Path $env:SHARED_SKILL_ALIAS -Target $env:SHARED_SKILL_TARGET -ErrorAction Stop | Out-Null",
            ], env=env, check=True)
        else:
            alias.symlink_to(os.path.relpath(target, alias.parent), target_is_directory=True)
        if not os.path.samefile(alias, target):
            raise RuntimeError("The created link does not point to the shared skills")
    print("Shared skills ready: .claude/skills -> .agents/skills")
    return 0


if __name__ == "__main__":
    sys.exit(main())
