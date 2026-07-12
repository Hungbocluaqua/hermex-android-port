#!/usr/bin/env python3
"""Verify native iOS and Android endpoint path sets stay aligned."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def paths_from_ios() -> set[str]:
    text = (ROOT / "HermesMobile" / "Networking" / "Endpoints.swift").read_text(encoding="utf-8")
    return set(re.findall(r'return\s+"(/[^"]+)"', text))


def paths_from_android() -> set[str]:
    text = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "uzairansar" / "hermex" / "core" / "network" / "Endpoint.kt").read_text(encoding="utf-8")
    return set(re.findall(r'Endpoint\(\s*"(/[^"]+)"', text))


def report_delta(left_name: str, left: set[str], right_name: str, right: set[str]) -> bool:
    ok = True
    missing = sorted(left - right)
    extra = sorted(right - left)
    if missing:
        ok = False
        print(f"{right_name} is missing paths from {left_name}:")
        for path in missing:
            print(f"  - {path}")
    if extra:
        ok = False
        print(f"{right_name} has paths not present in {left_name}:")
        for path in extra:
            print(f"  - {path}")
    return ok


def main() -> int:
    ios = paths_from_ios()
    android = paths_from_android()

    ok = True
    ok &= report_delta("iOS", ios, "Android", android)
    ok &= report_delta("Android", android, "iOS", ios)

    if ok:
        print(f"Endpoint parity OK: {len(ios)} paths")
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
