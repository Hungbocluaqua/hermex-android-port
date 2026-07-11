#!/usr/bin/env python3
"""Build the shared iOS fixture app and capture the complete golden inventory."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image


def run(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    print("+", " ".join(args), flush=True)
    return subprocess.run(args, check=check, text=True, capture_output=False)


def available_iphones() -> list[tuple[str, str]]:
    result = subprocess.run(
        ("xcrun", "simctl", "list", "devices", "available", "--json"),
        check=True,
        text=True,
        capture_output=True,
    )
    payload = json.loads(result.stdout)
    devices: list[tuple[str, str]] = []
    for runtime_devices in payload.get("devices", {}).values():
        for device in runtime_devices:
            name = device.get("name", "")
            if name.startswith("iPhone") and device.get("isAvailable"):
                devices.append((name, device["udid"]))
    return devices


def select_device(preferred: str, devices: list[tuple[str, str]]) -> tuple[str, str]:
    for name, udid in devices:
        if name == preferred:
            return name, udid
    if devices:
        return devices[0]
    raise RuntimeError("No available iPhone simulator found")


def configure_simulator(udid: str, state: str) -> None:
    booted = subprocess.run(
        ("xcrun", "simctl", "list", "devices", "--json"),
        check=True,
        text=True,
        capture_output=True,
    )
    payload = json.loads(booted.stdout)
    current_state = next(
        (
            device.get("state")
            for runtime_devices in payload.get("devices", {}).values()
            for device in runtime_devices
            if device.get("udid") == udid
        ),
        "Shutdown",
    )
    if current_state != "Booted":
        run("xcrun", "simctl", "boot", udid)
    run("xcrun", "simctl", "bootstatus", udid, "-b")
    run("xcrun", "simctl", "ui", udid, "appearance", state)


def build_app(project: Path, scheme: str, derived_data: Path, udid: str) -> Path:
    run(
        "xcodebuild",
        "build",
        "-project",
        str(project),
        "-scheme",
        scheme,
        "-destination",
        f"platform=iOS Simulator,id={udid}",
        "-derivedDataPath",
        str(derived_data),
        "-configuration",
        "Debug",
        "CODE_SIGNING_ALLOWED=NO",
    )
    app = derived_data / "Build" / "Products" / "Debug-iphonesimulator" / f"{scheme}.app"
    if not app.is_dir():
        raise RuntimeError(f"Built app was not found at {app}")
    return app


def capture_screen(
    udid: str,
    bundle_id: str,
    screen: str,
    state: str,
    output: Path,
    width: int,
    height: int,
) -> None:
    raw = output.with_suffix(".raw.png")
    output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(("xcrun", "simctl", "terminate", udid, bundle_id), check=False)
    run(
        "xcrun",
        "simctl",
        "launch",
        udid,
        bundle_id,
        "--screen",
        screen,
        "--state",
        state,
        "-AppleInterfaceStyle",
        "Dark" if state == "dark" else "Light",
    )
    time.sleep(8 if screen == "chat-keyboard-open" else 4)
    run("xcrun", "simctl", "io", udid, "screenshot", str(raw))
    with Image.open(raw) as image:
        image.convert("RGBA").resize((width, height), Image.Resampling.LANCZOS).save(output)
    raw.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=Path("ci/visual-goldens/hermex-screens.json"))
    parser.add_argument("--project", type=Path, required=True)
    parser.add_argument("--scheme", default="HermexVisualFixtures")
    parser.add_argument("--bundle-id", default="com.uzairansar.hermesmobile.visualfixtures")
    parser.add_argument("--derived-data", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    args = parser.parse_args()

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    devices = available_iphones()
    selected: dict[str, tuple[str, str]] = {}
    for device in manifest["device_matrix"]:
        selected[device["name"]] = select_device(device["ios"], devices)

    for device in manifest["device_matrix"]:
        device_name = device["name"]
        simulator_name, udid = selected[device_name]
        print(f"Using {simulator_name} ({udid}) for {device_name}", flush=True)
        configure_simulator(udid, "dark")
        app = build_app(args.project, args.scheme, args.derived_data / device_name, udid)
        run("xcrun", "simctl", "install", udid, str(app))
        for state in manifest["states"]:
            configure_simulator(udid, state)
            for screen in manifest["screens"]:
                capture_screen(
                    udid,
                    args.bundle_id,
                    screen,
                    state,
                    args.output_root / device_name / state / f"{screen}.png",
                    device["width"],
                    device["height"],
                )
        run("xcrun", "simctl", "shutdown", udid, check=False)

    print(f"Captured iOS visual goldens under {args.output_root}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
