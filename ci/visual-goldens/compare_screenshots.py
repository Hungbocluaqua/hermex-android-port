#!/usr/bin/env python3
"""Compare iOS golden screenshots with Android screenshots.

Expected layout:
  <ios-root>/<device>/<state>/<screen>.png
  <android-root>/<device>/<state>/<screen>.png
"""

from __future__ import annotations

import argparse
import json
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

try:
    from PIL import Image, ImageChops
except ImportError as exc:  # pragma: no cover - exercised in CI setup failures.
    raise SystemExit("Pillow is required. Install ci/visual-goldens/requirements.txt") from exc


@dataclass(frozen=True)
class ComparisonResult:
    device: str
    state: str
    screen: str
    delta_percent: float
    passed: bool
    reason: str


def load_manifest(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def compare_pair(ios_path: Path, android_path: Path, threshold: float, device: str, state: str, screen: str) -> ComparisonResult:
    if not ios_path.exists():
        return ComparisonResult(device, state, screen, 100.0, False, f"missing iOS golden: {ios_path}")
    if not android_path.exists():
        return ComparisonResult(device, state, screen, 100.0, False, f"missing Android screenshot: {android_path}")

    with Image.open(ios_path).convert("RGBA") as ios_image, Image.open(android_path).convert("RGBA") as android_image:
        if ios_image.size != android_image.size:
            return ComparisonResult(
                device,
                state,
                screen,
                100.0,
                False,
                f"size mismatch: iOS {ios_image.size} vs Android {android_image.size}",
            )

        diff = ImageChops.difference(ios_image, android_image)
        histogram = diff.convert("L").histogram()
        changed = sum(count for value, count in enumerate(histogram) if value > 8)
        total = ios_image.size[0] * ios_image.size[1]
        delta_percent = (changed / total) * 100.0 if total else 100.0
        passed = delta_percent <= threshold
        reason = "ok" if passed else f"pixel delta {delta_percent:.3f}% exceeds {threshold:.3f}%"
        return ComparisonResult(device, state, screen, delta_percent, passed, reason)


def compare_manifest(manifest: dict, ios_root: Path, android_root: Path) -> list[ComparisonResult]:
    threshold = float(manifest["acceptance"]["max_pixel_delta_percent"])
    results: list[ComparisonResult] = []
    for device in manifest["device_matrix"]:
        device_name = device["name"]
        for state in manifest["states"]:
            for screen in manifest["screens"]:
                relative = Path(device_name) / state / f"{screen}.png"
                results.append(
                    compare_pair(
                        ios_root / relative,
                        android_root / relative,
                        threshold,
                        device_name,
                        state,
                        screen,
                    )
                )
    return results


def print_results(results: list[ComparisonResult]) -> None:
    for result in results:
        status = "PASS" if result.passed else "FAIL"
        print(
            f"{status} {result.device}/{result.state}/{result.screen}: "
            f"{result.delta_percent:.3f}% - {result.reason}"
        )


def run_self_test() -> int:
    manifest = {
        "device_matrix": [{"name": "test-device"}],
        "states": ["dark"],
        "screens": ["chat"],
        "acceptance": {"max_pixel_delta_percent": 1.0},
    }
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        ios = root / "ios" / "test-device" / "dark"
        android = root / "android" / "test-device" / "dark"
        ios.mkdir(parents=True)
        android.mkdir(parents=True)
        Image.new("RGBA", (8, 8), (0, 0, 0, 255)).save(ios / "chat.png")
        Image.new("RGBA", (8, 8), (0, 0, 0, 255)).save(android / "chat.png")
        results = compare_manifest(manifest, root / "ios", root / "android")
        print_results(results)
        return 0 if all(result.passed for result in results) else 1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=Path("ci/visual-goldens/hermex-screens.json"))
    parser.add_argument("--ios-root", type=Path)
    parser.add_argument("--android-root", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        return run_self_test()

    if args.ios_root is None or args.android_root is None:
        parser.error("--ios-root and --android-root are required unless --self-test is used")

    manifest = load_manifest(args.manifest)
    results = compare_manifest(manifest, args.ios_root, args.android_root)
    print_results(results)
    return 0 if all(result.passed for result in results) else 1


if __name__ == "__main__":
    sys.exit(main())
