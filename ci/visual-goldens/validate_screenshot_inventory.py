#!/usr/bin/env python3
"""Validate that a screenshot bundle covers every visual-golden manifest entry."""

from __future__ import annotations

import argparse
import json
import sys
import tempfile
from pathlib import Path

try:
    from PIL import Image
except ImportError as exc:  # pragma: no cover - exercised in CI setup failures.
    raise SystemExit("Pillow is required. Install ci/visual-goldens/requirements.txt") from exc


def load_manifest(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def expected_paths(manifest: dict) -> list[Path]:
    paths: list[Path] = []
    for device in manifest["device_matrix"]:
        device_name = device["name"]
        for state in manifest["states"]:
            for screen in manifest["screens"]:
                paths.append(Path(device_name) / state / f"{screen}.png")
    return paths


def validate_inventory(manifest: dict, root: Path) -> list[str]:
    errors: list[str] = []
    for relative in expected_paths(manifest):
        path = root / relative
        if not path.is_file():
            errors.append(f"missing screenshot: {relative.as_posix()}")
            continue

        try:
            with Image.open(path) as image:
                image.verify()
        except Exception as exc:  # noqa: BLE001 - report image decoder failures clearly.
            errors.append(f"invalid screenshot {relative.as_posix()}: {exc}")
    return errors


def run_self_test() -> int:
    manifest = {
        "device_matrix": [{"name": "compact-phone"}],
        "states": ["dark"],
        "screens": ["session-list"],
    }
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        screenshot_dir = root / "compact-phone" / "dark"
        screenshot_dir.mkdir(parents=True)
        Image.new("RGBA", (16, 16), (0, 0, 0, 255)).save(screenshot_dir / "session-list.png")
        errors = validate_inventory(manifest, root)
        if errors:
            print("\n".join(errors))
            return 1
        print("Screenshot inventory self-test OK")
        return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=Path("ci/visual-goldens/hermex-screens.json"))
    parser.add_argument("--root", type=Path)
    parser.add_argument("--label", default="screenshots")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        return run_self_test()

    if args.root is None:
        parser.error("--root is required unless --self-test is used")

    manifest = load_manifest(args.manifest)
    errors = validate_inventory(manifest, args.root)
    if errors:
        print(f"{args.label} screenshot inventory is incomplete:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(f"{args.label} screenshot inventory OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
