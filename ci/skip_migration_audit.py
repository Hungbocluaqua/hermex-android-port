#!/usr/bin/env python3
"""Audit that every iOS app area is represented in the Skip migration plan."""

from __future__ import annotations

import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "ci" / "skip_migration_manifest.json"
ALLOWED_STATUSES = {
    "core-contract-started",
    "ui-contract-started",
    "platform-shim-started",
    "platform-shim-required",
    "migrated",
}


def swift_file_count(relative: str) -> int:
    base = ROOT / "HermesMobile" / relative
    if not base.exists():
        base = ROOT / relative
    if base.is_file():
        return 1 if base.suffix == ".swift" else 0
    return sum(1 for path in base.rglob("*.swift")) if base.exists() else 0


def main() -> int:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    required = set(manifest["required_feature_areas"])
    targets = manifest["migration_targets"]
    statuses = manifest["status"]
    shared_ui_entrypoints = manifest.get("shared_ui_entrypoints", {})

    represented = {area for areas in targets.values() for area in areas}
    ok = True

    missing_from_targets = sorted(required - represented)
    if missing_from_targets:
        ok = False
        print("Feature areas missing from migration targets:")
        for area in missing_from_targets:
            print(f"  - {area}")

    missing_status = sorted(required - set(statuses))
    if missing_status:
        ok = False
        print("Feature areas missing migration status:")
        for area in missing_status:
            print(f"  - {area}")

    invalid_status = sorted(area for area, status in statuses.items() if status not in ALLOWED_STATUSES)
    if invalid_status:
        ok = False
        print("Feature areas with invalid migration status:")
        for area in invalid_status:
            print(f"  - {area}: {statuses[area]}")

    for area in sorted(required):
        count = swift_file_count(area)
        if count == 0:
            ok = False
            print(f"No Swift source found for required area: {area}")
        else:
            print(f"{area}: {count} Swift files -> {statuses.get(area)}")

    hermex_ui_areas = set(targets.get("HermexUI", []))
    missing_ui_entrypoints = sorted(hermex_ui_areas - set(shared_ui_entrypoints))
    if missing_ui_entrypoints:
        ok = False
        print("HermexUI feature areas missing shared UI entrypoints:")
        for area in missing_ui_entrypoints:
            print(f"  - {area}")

    for area, files in sorted(shared_ui_entrypoints.items()):
        if area not in hermex_ui_areas:
            ok = False
            print(f"Shared UI entrypoint declared for non-HermexUI area: {area}")
            continue
        for relative in files:
            path = ROOT / relative
            if not path.is_file():
                ok = False
                print(f"Missing shared UI entrypoint for {area}: {relative}")
            else:
                print(f"{area}: shared UI entrypoint {relative}")

    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
