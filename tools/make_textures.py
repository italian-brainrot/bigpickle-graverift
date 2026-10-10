#!/usr/bin/env python3
"""Generate the two 16x16 item textures for the Grave Rift mod.

Uses only the Python standard library (zlib + struct) so it runs anywhere the
project is built. The art is drawn procedurally with 4x supersampling for clean
edges, then quantised to a hand-drawn looking pixel-art palette.
"""
import math
import os
import struct
import zlib

W = H = 16
SS = 4  # supersampling factor


def write_png(path, pixels):
    raw = bytearray()
    for y in range(H):
        raw.append(0)  # filter type 0 (None)
        for x in range(W):
            r, g, b, a = pixels[y][x]
            raw += bytes((r, g, b, a))

    def chunk(typ, data):
        return (
            struct.pack(">I", len(data))
            + typ
            + data
            + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)
        )

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote", path, len(png), "bytes")


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    return tuple(int(round(lerp(c1[i], c2[i], t))) for i in range(3))


# ---------------------------------------------------------------------------
# Pierced Ender Pearl
# ---------------------------------------------------------------------------

# Ender pearl palette.
P_DARK = (7, 40, 49)
P_BASE = (20, 96, 96)
P_MID = (41, 150, 138)
P_LIT = (104, 214, 191)
P_HI = (196, 250, 232)
# The "pierce": a shaft of cold light driven through the pearl.
P_PIERCE = (214, 255, 248)
P_PIERCE_GLOW = (120, 210, 220)


def pearl_sample(fx, fy):
    cx, cy = 7.5, 7.5
    dx, dy = fx - cx, fy - cy
    dist = math.hypot(dx, dy)
    radius = 6.3
    if dist > radius + 0.75:
        return (0, 0, 0, 0)

    # Translucent rim near the silhouette.
    a = 1.0
    if dist > radius - 0.55:
        a = max(0.0, min(1.0, (radius + 0.75 - dist) / 1.3))

    nx = dx / radius
    ny = dy / radius
    nz = math.sqrt(max(0.0, 1.0 - min(1.0, nx * nx + ny * ny)))

    # Light from the upper-left, plus a soft ambient fill.
    lx, ly, lz = -0.55, -0.72, 0.42
    diff = max(0.0, nx * lx + ny * ly + nz * lz)
    rim = (1.0 - nz) ** 3

    if dist > radius - 0.9:
        base = P_DARK
    elif diff > 0.72:
        base = P_HI
    elif diff > 0.45:
        base = P_LIT
    elif diff > 0.12:
        base = P_MID
    else:
        base = P_BASE

    # Push the rim towards the dark teal.
    base = mix(base, P_DARK, rim * 0.8)

    # The piercing shaft: a straight, slightly diagonal line through the middle.
    # Distance to the line y = x rotated a touch.
    line_x = dx * 0.82 - dy * 0.57
    line_y = dx * 0.57 + dy * 0.82
    if abs(line_x) < 0.62 and -5.6 < line_y < 5.6:
        core = abs(line_x) / 0.62
        t = 1.0 - core
        col = mix(P_PIERCE_GLOW, P_PIERCE, t)
        base = mix(base, col, 0.55 + 0.45 * t)

    return (base[0], base[1], base[2], int(round(a * 255)))


# ---------------------------------------------------------------------------
# Grave Rift
# ---------------------------------------------------------------------------

R_DARK = (16, 6, 30)
R_DEEP = (43, 12, 72)
R_MID = (94, 32, 130)
R_BRIGHT = (162, 74, 200)
R_EDGE = (214, 132, 236)
R_BONE = (236, 226, 218)


def rift_sample(fx, fy):
    cx, cy = 7.5, 7.5
    dx, dy = fx - cx, fy - cy
    dist = math.hypot(dx, dy)
    angle = math.atan2(dy, dx)
    radius = 6.4
    if dist > radius + 0.85:
        return (0, 0, 0, 0)

    a = 1.0
    if dist > radius - 0.4:
        a = max(0.0, min(1.0, (radius + 0.85 - dist) / 1.25))

    norm = dist / radius

    # Swirling violet bands that wind tighter towards the rim.
    swirl = 0.5 + 0.5 * math.sin(angle * 2.0 + norm * 7.5)
    swirl = swirl ** 2

    if norm < 0.34:
        # A hole straight through: near-black core with a faint glow.
        col = mix(R_DARK, R_DEEP, norm / 0.34 * 0.5)
    elif norm < 0.82:
        t = (norm - 0.34) / 0.48
        col = mix(mix(R_DEEP, R_MID, t), mix(R_MID, R_BRIGHT, t), swirl)
    else:
        t = (norm - 0.82) / 0.18
        col = mix(R_BRIGHT, R_EDGE, min(1.0, t + swirl * 0.4))

    # A couple of bone-white specks caught in the swirl, like burial dust.
    for (sx, sy) in ((4.9, 3.4), (11.6, 10.7), (6.2, 11.9)):
        if math.hypot(fx - sx, fy - sy) < 0.62:
            col = mix(col, R_BONE, 0.85)

    # Darken the very outer ring so the tear reads as torn, not a smooth ball.
    if dist > radius - 1.0:
        col = mix(col, R_DARK, 0.5)

    return (col[0], col[1], col[2], int(round(a * 255)))


def render(sampler):
    """Supersample the shape and average premultiplied colour for clean edges."""
    pixels = []
    pixels = []
    for y in range(H):
        row = []
        for x in range(W):
            r = g = b = a = 0.0
            for sy in range(SS):
                for sx in range(SS):
                    fx = x + (sx + 0.5) / SS
                    fy = y + (sy + 0.5) / SS
                    sr, sg, sb, sa = sampler(fx, fy)
                    r += sr * sa
                    g += sg * sa
                    b += sb * sa
                    a += sa
            n = SS * SS
            if a <= 0.001:
                row.append((0, 0, 0, 0))
            else:
                row.append((
                    int(round(r / a)),
                    int(round(g / a)),
                    int(round(b / a)),
                    int(round(a / n)),
                ))
        pixels.append(row)
    return pixels


if __name__ == "__main__":
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    base = os.path.join(
        project_root,
        "src", "main", "resources", "assets", "bigpickle_graverift", "textures", "item",
    )
    write_png(os.path.join(base, "pierced_ender_pearl.png"), render(pearl_sample))
    write_png(os.path.join(base, "grave_rift.png"), render(rift_sample))
