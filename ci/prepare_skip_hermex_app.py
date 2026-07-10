#!/usr/bin/env python3
"""Patch a generated Skip app shell so it launches the shared Hermex SwiftUI UI."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def posix_path(path: Path) -> str:
    return path.resolve().as_posix()


def patch_package(package_path: Path, repo_root: Path, module_name: str) -> None:
    text = package_path.read_text(encoding="utf-8")
    dependency = f'.package(name: "HermexShared", path: "{posix_path(repo_root)}")'

    if dependency not in text:
        text = text.replace(
            "dependencies: [",
            f"dependencies: [\n        {dependency},",
            1,
        )

    target_pattern = re.compile(
        rf'(\.target\(\s*name:\s*"{re.escape(module_name)}",\s*dependencies:\s*\[)(.*?)(\]\s*,)',
        re.DOTALL,
    )
    target_match = target_pattern.search(text)
    if not target_match:
        fail(f"Could not locate generated target dependencies for {module_name} in {package_path}")

    hermex_dependencies = [
        '.product(name: "HermexCore", package: "HermexShared")',
        '.product(name: "HermexPlatform", package: "HermexShared")',
        '.product(name: "HermexUI", package: "HermexShared")',
    ]
    body = target_match.group(2)
    for product in reversed(hermex_dependencies):
        if product not in body:
            body = f"\n            {product},{body}"

    text = text[: target_match.start(2)] + body + text[target_match.end(2) :]
    package_path.write_text(text, encoding="utf-8")


def validate_visual_fixture_name(repo_root: Path, fixture_name: str) -> None:
    manifest_path = repo_root / "ci" / "visual-goldens" / "hermex-screens.json"
    if not manifest_path.is_file():
        fail(f"Visual golden screen manifest is missing: {manifest_path}")

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    screen_names = manifest.get("screens", [])
    if fixture_name not in screen_names:
        fail(f"Unknown visual fixture '{fixture_name}'. Expected one of: {', '.join(screen_names)}")


def replace_app_source(
    app_dir: Path,
    repo_root: Path,
    module_name: str,
    fixture_name: str | None,
    enable_runtime_visual_fixtures: bool,
) -> None:
    source_dir = app_dir / "Sources" / module_name
    if not source_dir.is_dir():
        fail(f"Generated Skip source directory is missing: {source_dir}")

    for swift_file in source_dir.glob("*.swift"):
        swift_file.unlink()

    template = repo_root / "ci" / "skip-hermex-app" / "HermexSkipApp.swift"
    if not template.is_file():
        fail(f"Hermex Skip app template is missing: {template}")

    text = template.read_text(encoding="utf-8")
    marker = "private let hermexVisualFixtureName: String? = nil"
    if marker not in text:
        fail("Hermex Skip app template is missing the visual fixture injection marker.")
    runtime_marker = "private let hermexRuntimeVisualFixturesEnabled = false"
    if runtime_marker not in text:
        fail("Hermex Skip app template is missing the runtime fixture enable marker.")

    if fixture_name:
        text = text.replace(marker, f"private let hermexVisualFixtureName: String? = {json.dumps(fixture_name)}", 1)
    if enable_runtime_visual_fixtures:
        text = text.replace(runtime_marker, "private let hermexRuntimeVisualFixturesEnabled = true", 1)

    (source_dir / f"{module_name}.swift").write_text(text, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--app-dir", required=True, type=Path)
    parser.add_argument("--repo-root", required=True, type=Path)
    parser.add_argument("--module-name", default="HermexSkipApp")
    parser.add_argument("--visual-fixture-name", default=None)
    parser.add_argument("--enable-runtime-visual-fixtures", action="store_true")
    args = parser.parse_args()

    app_dir = args.app_dir.resolve()
    repo_root = args.repo_root.resolve()
    fixture_name = (args.visual_fixture_name or os.environ.get("HERMEX_VISUAL_FIXTURE_NAME") or "").strip() or None
    package_path = app_dir / "Package.swift"
    if not package_path.is_file():
        fail(f"Generated Skip Package.swift is missing: {package_path}")

    if fixture_name:
        validate_visual_fixture_name(repo_root, fixture_name)

    patch_package(package_path, repo_root, args.module_name)
    env_enables_runtime_fixtures = os.environ.get("HERMEX_ENABLE_RUNTIME_VISUAL_FIXTURES", "").strip() == "1"
    replace_app_source(
        app_dir,
        repo_root,
        args.module_name,
        fixture_name,
        args.enable_runtime_visual_fixtures or env_enables_runtime_fixtures,
    )
    print(f"Prepared generated Skip app at {app_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
