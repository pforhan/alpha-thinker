#!/usr/bin/env python3
"""Validate and shape the phase-color palettes in PhaseTheme.kt.

Mirrors the invariant checks in `PhaseThemeTest`:
  - light & dark content inks clear WCAG AA (4.5:1) against their containers
  - light containers stay >= MIN_SURFACE_DISTANCE (CIE Lab dE) from the light
    card surface, dark containers from the dark surface

Commands:
  verify [PhaseTheme.kt path]
      Parse every `PhaseColors(...)` entry in the source and check it.
  check  <containerLight> <contents> <containerDark> <contentDark> [...]
      Check one or more explicit quads (extra pairs wrap to new phases).
  darken <containerLight> [factor] [--inks HEX...]
      Darken a container (default factor 0.5) and report how the result does
      against the dark surface and any candidate content inks.

Colors are 0xAARRGGBB (the format used in PhaseTheme.kt), `#RRGGBB`, or bare
hex. Exits non-zero if any check fails.
"""

import math
import re
import sys

MIN_CONTRAST = 4.5
MIN_SURFACE_DISTANCE = 12.0
LIGHT_SURFACE = "#F4F3FA"
DARK_SURFACE = "#1A1B21"

# Default PhaseTheme.kt relative to the repo root.
DEFAULT_SOURCE = "shared/src/commonMain/kotlin/alphainterplanetary/thinker/ui/theme/PhaseTheme.kt"

PHASE_LINE = re.compile(r"\b([A-Za-z]+) to PhaseColors\(((?:0x[0-9A-Fa-f]{8}[\s,]*)+)\)")
KEY_LINE = re.compile(r'key = "([^"]+)"')
HEX = re.compile(r"0x([0-9A-Fa-f]{8})")


def parse_color(token):
    token = token.strip()
    if token.startswith("0x"):
        hexstr = token[2:]
    else:
        hexstr = token.lstrip("#")
    if len(hexstr) == 8:
        start = 2
    elif len(hexstr) == 6:
        start = 0
    else:
        raise ValueError("not a 6- or 8-digit color: %r" % token)
    return tuple(int(hexstr[i : i + 2], 16) for i in range(start, start + 6, 2))


def fmt(rgb):
    return "0x%02X%02X%02X%02X" % ((255,) + tuple(rgb))


def linear(channel):
    c = channel / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def luminance(rgb):
    r, g, b = (linear(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    hi, lo = sorted((luminance(a), luminance(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def _lab_channel(t):
    return t ** (1 / 3) if t > 0.008856 else 7.787 * t + 16 / 116


def cie_lab(rgb):
    r, g, b = (linear(c) for c in rgb)
    x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
    y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
    fx, fy, fz = (_lab_channel(c) for c in (x, y, z))
    return (116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))


def delta_e(a, b):
    la, lb = cie_lab(a), cie_lab(b)
    return math.sqrt(sum((la[i] - lb[i]) ** 2 for i in range(3)))


def darken(rgb, factor):
    return tuple(max(0, min(255, round(c * factor))) for c in rgb)


def check_phase(name, container_light, contents, container_dark, content_dark):
    light_contrast = contrast(contents, container_light)
    dark_contrast = contrast(content_dark, container_dark)
    light_distance = delta_e(container_light, parse_color(LIGHT_SURFACE))
    dark_distance = delta_e(container_dark, parse_color(DARK_SURFACE))
    ok = (
        light_contrast >= MIN_CONTRAST
        and dark_contrast >= MIN_CONTRAST
        and light_distance >= MIN_SURFACE_DISTANCE
        and dark_distance >= MIN_SURFACE_DISTANCE
    )
    print(
        "%-16s light %-5s dark %-5s surfL %-5s surfD %-5s %s"
        % (
            name,
            "%.2f" % light_contrast,
            "%.2f" % dark_contrast,
            "%.1f" % light_distance,
            "%.1f" % dark_distance,
            "OK" if ok else "FAIL",
        )
    )
    return ok


def extract_entries(source_path):
    """Yield (theme_key, phase_name, [c1, contents, c2, c3]) from PhaseTheme.kt."""
    theme = "unknown"
    entries = []
    for line in open(source_path, encoding="utf-8"):
        if "data class" in line or "FallbackColors" in line:
            continue
        key_match = KEY_LINE.search(line)
        if key_match:
            theme = key_match.group(1)
            continue
        phase_match = PHASE_LINE.search(line)
        if not phase_match:
            continue
        hexes = [parse_color(h) for h in HEX.findall(phase_match.group(2))]
        entries.append((theme, phase_match.group(1), hexes))
    return entries


def cmd_verify(source_path):
    entries = extract_entries(source_path)
    if not entries:
        print("No PhaseColors entries found in %s" % source_path)
        return 1
    grouped = {}
    for theme, phase, hexes in entries:
        grouped.setdefault(theme, []).append(phase)
    for theme, phases in grouped.items():
        print("%s (%d phases)" % (theme, len(phases)))
    print()
    results = [check_phase("%s/%s" % (theme, phase), *hexes) for theme, phase, hexes in entries]
    print()
    failures = sum(1 for ok in results if not ok)
    print("checked=%d failed=%d" % (len(results), failures))
    return 1 if failures else 0


def cmd_check(args):
    if len(args) % 4 != 0:
        print("error: check expects quads of containerLight contents containerDark contentDark")
        return 2
    results = []
    for i in range(0, len(args), 4):
        rgb = [parse_color(t) for t in args[i : i + 4]]
        results.append(check_phase("phase %d" % (i // 4 + 1), *rgb))
    failures = sum(1 for ok in results if not ok)
    print("checked=%d failed=%d" % (len(results), failures))
    return 1 if failures else 0


def cmd_darken(args):
    if not args:
        print("error: darken expects 0xAARRGGBB [factor]")
        return 2
    base = parse_color(args[0])
    factor = float(args[1]) if len(args) > 1 else 0.5
    result = darken(base, factor)
    print("%s x %.2f -> %s" % (fmt(base), factor, fmt(result)))
    print("  dark surface distance: %.1f (need >= %.1f)" % (delta_e(result, parse_color(DARK_SURFACE)), MIN_SURFACE_DISTANCE))
    if "--inks" in args:
        inks = args[args.index("--inks") + 1 :]
        for token in inks:
            ink = parse_color(token)
            print("  contrast %s on darkened: %.2f (need >= %.2f)" % (fmt(ink), contrast(ink, result), MIN_CONTRAST))
    return 0


USAGE = (__doc__ or "").strip()


def main():
    if len(sys.argv) < 2:
        print(USAGE)
        return 2
    command, args = sys.argv[1], sys.argv[2:]
    if command == "verify":
        return cmd_verify(args[0] if args else DEFAULT_SOURCE)
    if command == "check":
        return cmd_check(args)
    if command == "darken":
        return cmd_darken(args)
    print(USAGE)
    return 2


if __name__ == "__main__":
    sys.exit(main())