#!/usr/bin/env python3
"""Reject Android visual captures that are obviously system error dialogs.

The hosted emulator can occasionally show a centered Android ANR / system dialog
while window focus still mentions the app. Keep this dependency-free so it runs
on macOS runners before screenshot artifacts are uploaded.
"""

from __future__ import annotations

import argparse
import struct
import sys
import zlib
from pathlib import Path


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


class PngDecodeError(RuntimeError):
    pass


def paeth(left: int, up: int, upper_left: int) -> int:
    predictor = left + up - upper_left
    distance_left = abs(predictor - left)
    distance_up = abs(predictor - up)
    distance_upper_left = abs(predictor - upper_left)
    if distance_left <= distance_up and distance_left <= distance_upper_left:
        return left
    if distance_up <= distance_upper_left:
        return up
    return upper_left


def read_png_rgba(path: Path) -> tuple[int, int, bytes]:
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        raise PngDecodeError(f"{path} is not a PNG file.")

    offset = len(PNG_SIGNATURE)
    width = height = None
    color_type = None
    bit_depth = None
    compressed_parts: list[bytes] = []

    while offset + 8 <= len(data):
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        chunk_type = data[offset + 4 : offset + 8]
        chunk_data = data[offset + 8 : offset + 8 + length]
        offset += 12 + length

        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, compression, filter_method, interlace = struct.unpack(
                ">IIBBBBB",
                chunk_data,
            )
            if bit_depth != 8:
                raise PngDecodeError(f"Only 8-bit PNGs are supported, got bit depth {bit_depth}.")
            if color_type not in {2, 6}:
                raise PngDecodeError(f"Only RGB/RGBA PNGs are supported, got color type {color_type}.")
            if compression != 0 or filter_method != 0 or interlace != 0:
                raise PngDecodeError("Unsupported PNG compression/filter/interlace settings.")
        elif chunk_type == b"IDAT":
            compressed_parts.append(chunk_data)
        elif chunk_type == b"IEND":
            break

    if width is None or height is None or color_type is None:
        raise PngDecodeError("PNG is missing IHDR metadata.")
    if not compressed_parts:
        raise PngDecodeError("PNG is missing IDAT image data.")

    channels = 4 if color_type == 6 else 3
    stride = width * channels
    raw = zlib.decompress(b"".join(compressed_parts))
    expected = (stride + 1) * height
    if len(raw) < expected:
        raise PngDecodeError(f"PNG image data is truncated: expected {expected} bytes, got {len(raw)}.")

    rows: list[bytearray] = []
    cursor = 0
    previous = bytearray(stride)
    for _ in range(height):
        filter_type = raw[cursor]
        cursor += 1
        row = bytearray(raw[cursor : cursor + stride])
        cursor += stride

        for index in range(stride):
            left = row[index - channels] if index >= channels else 0
            up = previous[index]
            upper_left = previous[index - channels] if index >= channels else 0
            if filter_type == 1:
                row[index] = (row[index] + left) & 0xFF
            elif filter_type == 2:
                row[index] = (row[index] + up) & 0xFF
            elif filter_type == 3:
                row[index] = (row[index] + ((left + up) // 2)) & 0xFF
            elif filter_type == 4:
                row[index] = (row[index] + paeth(left, up, upper_left)) & 0xFF
            elif filter_type != 0:
                raise PngDecodeError(f"Unsupported PNG row filter: {filter_type}.")

        rows.append(row)
        previous = row

    if channels == 4:
        rgba = b"".join(rows)
    else:
        expanded = bytearray(width * height * 4)
        out = 0
        for row in rows:
            for index in range(0, len(row), 3):
                expanded[out : out + 4] = bytes((row[index], row[index + 1], row[index + 2], 255))
                out += 4
        rgba = bytes(expanded)

    return width, height, rgba


def center_light_pixel_ratio(width: int, height: int, rgba: bytes) -> float:
    left = width // 8
    right = width - left
    top = height // 5
    bottom = height - top
    light = 0
    total = 0

    for y in range(top, bottom):
        row_offset = y * width * 4
        for x in range(left, right):
            index = row_offset + x * 4
            r, g, b, a = rgba[index : index + 4]
            if a > 200 and r >= 218 and g >= 218 and b >= 218:
                light += 1
            total += 1

    return light / total if total else 0.0


def visible_pixel_ratio(width: int, height: int, rgba: bytes) -> float:
    visible = 0
    total = width * height

    for index in range(0, len(rgba), 4):
        r, g, b, a = rgba[index : index + 4]
        if a > 16 and max(r, g, b) > 10:
            visible += 1

    return visible / total if total else 0.0


def app_content_foreground_pixel_ratio(width: int, height: int, rgba: bytes) -> float:
    left = width // 12
    right = width - left
    top = max(72, height // 10)
    bottom = min(height - 80, height * 9 // 10)
    foreground = 0
    total = 0

    for y in range(top, bottom):
        row_offset = y * width * 4
        for x in range(left, right):
            index = row_offset + x * 4
            r, g, b, a = rgba[index : index + 4]
            if a > 200 and max(r, g, b) >= 54:
                foreground += 1
            total += 1

    return foreground / total if total else 0.0


def looks_like_android_splash_screen(visible_ratio: float, foreground_ratio: float) -> bool:
    # Android 12+ splash screens paint a nearly full-viewport non-black background
    # plus the centered launcher icon. Hermex content screens are black/glass and
    # do not cover almost every pixel with a visible system background.
    return visible_ratio > 0.92 and foreground_ratio < 0.10


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("screenshot", type=Path)
    parser.add_argument(
        "--max-center-light-ratio",
        type=float,
        default=0.16,
        help="Reject captures with a large light system-dialog panel in the center.",
    )
    parser.add_argument(
        "--min-visible-pixel-ratio",
        type=float,
        default=0.003,
        help="Reject blank captures with too few non-black visible pixels.",
    )
    parser.add_argument(
        "--min-app-content-foreground-ratio",
        type=float,
        default=0.003,
        help="Reject captures whose app content area has no visible Hermex foreground.",
    )
    args = parser.parse_args()

    try:
        width, height, rgba = read_png_rgba(args.screenshot)
    except (OSError, PngDecodeError, zlib.error) as error:
        print(f"Could not inspect Android screenshot {args.screenshot}: {error}", file=sys.stderr)
        return 1

    ratio = center_light_pixel_ratio(width, height, rgba)
    if ratio > args.max_center_light_ratio:
        print(
            "Android screenshot looks like a system dialog instead of Hermex "
            f"(center light pixel ratio {ratio:.3f} > {args.max_center_light_ratio:.3f}): {args.screenshot}",
            file=sys.stderr,
        )
        return 1

    visible_ratio = visible_pixel_ratio(width, height, rgba)
    if visible_ratio < args.min_visible_pixel_ratio:
        print(
            "Android screenshot looks blank instead of Hermex "
            f"(visible pixel ratio {visible_ratio:.4f} < {args.min_visible_pixel_ratio:.4f}): {args.screenshot}",
            file=sys.stderr,
        )
        return 1

    foreground_ratio = app_content_foreground_pixel_ratio(width, height, rgba)
    if foreground_ratio < args.min_app_content_foreground_ratio:
        print(
            "Android screenshot has no visible Hermex app content "
            f"(content foreground ratio {foreground_ratio:.4f} < "
            f"{args.min_app_content_foreground_ratio:.4f}): {args.screenshot}",
            file=sys.stderr,
        )
        return 1

    if looks_like_android_splash_screen(visible_ratio, foreground_ratio):
        print(
            "Android screenshot looks like the Android launch splash instead of Hermex content "
            f"(visible pixel ratio {visible_ratio:.4f}, content foreground ratio {foreground_ratio:.4f}): "
            f"{args.screenshot}",
            file=sys.stderr,
        )
        return 1

    print(
        "Android screenshot pixel guard OK: "
        f"center_light_ratio={ratio:.3f} visible_ratio={visible_ratio:.4f} "
        f"content_foreground_ratio={foreground_ratio:.4f}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
