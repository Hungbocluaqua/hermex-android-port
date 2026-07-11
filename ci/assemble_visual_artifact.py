#!/usr/bin/env python3
"""Assemble complete visual screenshots from independent Actions artifacts."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import tempfile
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sources", required=True, help="JSON array of {run_id, artifact} objects")
    parser.add_argument("--repo", required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    args = parser.parse_args()

    sources = json.loads(args.sources)
    if not isinstance(sources, list) or not sources:
        raise SystemExit("--sources must be a non-empty JSON array")

    args.output_root.mkdir(parents=True, exist_ok=True)
    seen: set[Path] = set()
    with tempfile.TemporaryDirectory(prefix="hermex-visual-artifacts-") as temp:
        temp_root = Path(temp)
        for index, source in enumerate(sources):
            run_id = str(source["run_id"])
            artifact = str(source["artifact"])
            source_root = temp_root / str(index)
            print(f"Downloading {artifact} from run {run_id}", flush=True)
            subprocess.run(
                [
                    "gh",
                    "run",
                    "download",
                    run_id,
                    "--repo",
                    args.repo,
                    "--name",
                    artifact,
                    "--dir",
                    str(source_root),
                ],
                check=True,
            )
            files = list(source_root.rglob("*.png"))
            if not files:
                raise SystemExit(f"Artifact {artifact} from run {run_id} contained no PNG files")
            for source_file in files:
                relative = source_file.relative_to(source_root)
                if relative in seen:
                    raise SystemExit(f"Duplicate screenshot path in assembled inventory: {relative}")
                seen.add(relative)
                destination = args.output_root / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source_file, destination)

    print(f"Assembled {len(seen)} screenshots under {args.output_root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
