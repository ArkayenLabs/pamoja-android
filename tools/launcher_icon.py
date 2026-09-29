"""Generates the launcher icon's foreground layer and the Play Store icon.

    python tools/launcher_icon.py

Needs Pillow. The mark is two tapered soles mid-stride, each the union of
circles shrinking heel to toe, rotated -30 degrees, in a 100-unit design space.
This file is the mark's only source (the old Compose PamojaMark was deleted
2026-09-29). The monochrome layer (ic_launcher_monochrome_pamoja.png) and
drawable/ic_notification.xml carry the same silhouette but are not written
here, so if the shape changes, redraw them too.

Lilac: ink front sole, lime back sole, on the lilac ground that
values/ic_launcher_background.xml supplies. The tread marks are cut out of
the foreground so the ground shows through them.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

INK = (0x16, 0x13, 0x2B, 255)
LIME = (0xD4, 0xF7, 0x5A, 255)
LILAC = (0xCF, 0xC6, 0xFF, 255)

ORIGIN, SOLE_LENGTH, HEEL_RADIUS, TOE_RADIUS, SOLE_ANGLE, SOLE_STEPS = 50, 60, 15.5, 8.5, -30, 40
BACK_OFFSET, FRONT_OFFSET = (6, -7), (-6, 9)
# The box the two soles actually occupy, measured from the geometry above.
CENTER_X, CENTER_Y, WIDTH, HEIGHT, FIT_MARGIN = 49.53, 52.75, 67.18, 58.00, 0.98
TREAD_LENGTH, TREAD_WIDTH = 0.68, 1.9
BACK_TREADS, FRONT_TREADS = (0.62, 0.78), (0.22, 0.38)

# The mark spans 48dp of the adaptive icon's 108dp layer, inside the 66dp safe
# circle (the soles' far ends reach about 32dp from the centre).
LAYER_FRACTION = 48 / 108
SUPERSAMPLE = 4

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"


def sole_at(t):
    x0, x1 = -SOLE_LENGTH / 2 + HEEL_RADIUS, SOLE_LENGTH / 2 - TOE_RADIUS
    return x0 + (x1 - x0) * t, HEEL_RADIUS + (TOE_RADIUS - HEEL_RADIUS) * t


def render(size, fraction, background=(0, 0, 0, 0)):
    s = size * SUPERSAMPLE
    image = Image.new("RGBA", (s, s), background)
    draw = ImageDraw.Draw(image)
    box = fraction * s
    scale = min(box / WIDTH, box / HEIGHT) * FIT_MARGIN

    def px(x, y):
        return s / 2 + (x - CENTER_X) * scale, s / 2 + (y - CENTER_Y) * scale

    angle = math.radians(SOLE_ANGLE)
    along = (math.cos(angle), math.sin(angle))
    across = (-along[1], along[0])

    for (ox, oy), colour in ((BACK_OFFSET, LIME), (FRONT_OFFSET, INK)):
        for i in range(SOLE_STEPS + 1):
            local_x, r = sole_at(i / SOLE_STEPS)
            cx, cy = px(ORIGIN + local_x * along[0] + ox, ORIGIN + local_x * along[1] + oy)
            draw.ellipse((cx - r * scale, cy - r * scale, cx + r * scale, cy + r * scale), fill=colour)

    # Drawing with the background colour replaces pixels rather than blending,
    # so on a transparent layer this cuts the treads out.
    width = TREAD_WIDTH * scale
    for (ox, oy), positions in ((BACK_OFFSET, BACK_TREADS), (FRONT_OFFSET, FRONT_TREADS)):
        for t in positions:
            local_x, r = sole_at(t)
            x, y = ORIGIN + local_x * along[0] + ox, ORIGIN + local_x * along[1] + oy
            half = r * TREAD_LENGTH
            ends = [px(x - across[0] * half, y - across[1] * half), px(x + across[0] * half, y + across[1] * half)]
            draw.line(ends, fill=background, width=round(width))
            for ex, ey in ends:
                draw.ellipse((ex - width / 2, ey - width / 2, ex + width / 2, ey + width / 2), fill=background)

    return image.resize((size, size), Image.LANCZOS)


def main():
    for density, size in (("xxhdpi", 324), ("xxxhdpi", 432)):
        path = RES / f"mipmap-{density}/ic_launcher_foreground_pamoja.png"
        render(size, LAYER_FRACTION).save(path, optimize=True)
        print(path.relative_to(ROOT))

    # Play's hi-res icon is a full square that Play masks itself, so the mark
    # keeps its proportion of the adaptive icon's visible 72dp.
    store = ROOT / "design/store-listing/pamoja-icon-512.png"
    store.parent.mkdir(parents=True, exist_ok=True)
    render(512, 48 / 72, background=LILAC).convert("RGB").save(store, optimize=True)
    print(store.relative_to(ROOT), "(gitignored; upload in Play Console)")


if __name__ == "__main__":
    main()
