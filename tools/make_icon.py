"""One-off generator for the mod icon.

Draws a 7x7 cross-section of a mall room: the 5x5 finished interior sits empty in the
middle, the 20 face-plate cells are emerald (mined and skinned), and the 4 corner cells
are neutral (the framing, never touched). That is the whole mod in one picture.

Sibling to bedrock-line-placement's locked-row icon and bedrock-crafting-controls' 3x3
grid — same palette, same rounded-square background. Supersampled for clean edges.
"""

from PIL import Image, ImageDraw, ImageFilter

S = 4  # supersampling factor
N = 256  # final size
W = N * S

img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
d = ImageDraw.Draw(img)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sx(v):
    """Scale a value expressed in final (256px) units up to the supersampled canvas."""
    return v * S


# --- shared palette with the sibling mods -----------------------------------
top = (38, 42, 56)
bot = (24, 27, 38)
accent = (61, 199, 142)  # emerald: the skin we place
accent_dark = (38, 150, 104)
neutral = (62, 69, 92)  # stone: the framing we leave alone
neutral_hi = (78, 86, 112)
void = (18, 20, 29)  # the carved-out interior

# --- background: vertical gradient inside a rounded square ------------------
bg = Image.new("RGBA", (W, W), (0, 0, 0, 0))
bgd = ImageDraw.Draw(bg)
for y in range(W):
    bgd.line([(0, y), (W, y)], fill=lerp(top, bot, y / W) + (255,))
mask = Image.new("L", (W, W), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 1, W - 1], radius=sx(52), fill=255)
img.paste(bg, (0, 0), mask)

# subtle border
d.rounded_rectangle(
    [sx(2), sx(2), W - sx(2), W - sx(2)], radius=sx(50), outline=(70, 78, 104, 255), width=sx(2)
)

# --- the 7x7 cross-section --------------------------------------------------
GRID = 7
RADIUS = GRID // 2  # 3
cell = 26  # cell size in final units
gap = 4
radius = 5  # cell corner radius

span = GRID * cell + (GRID - 1) * gap
origin = (N - span) / 2


def cell_box(ix, iy):
    x0 = origin + ix * (cell + gap)
    y0 = origin + iy * (cell + gap)
    return x0, y0, x0 + cell, y0 + cell


# Soft emerald glow behind the whole skin ring, so the room reads as lit from inside.
glow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
ImageDraw.Draw(glow).rounded_rectangle(
    [sx(origin - 6), sx(origin - 6), sx(origin + span + 6), sx(origin + span + 6)],
    radius=sx(radius + 10),
    fill=accent + (60,),
)
glow = glow.filter(ImageFilter.GaussianBlur(sx(9)))
img.alpha_composite(glow)

for iy in range(GRID):
    for ix in range(GRID):
        dx = ix - RADIUS
        dy = iy - RADIUS
        # The same predicate the mod uses: count coordinates sitting at an envelope extreme.
        extremes = (abs(dx) == RADIUS) + (abs(dy) == RADIUS)
        x0, y0, x1, y1 = cell_box(ix, iy)

        if extremes == 0:
            # Interior: carved away. Draw it recessed and dark.
            d.rounded_rectangle(
                [sx(x0), sx(y0), sx(x1), sx(y1)], radius=sx(radius), fill=void + (255,)
            )
        elif extremes == 1:
            # A flat face plate: mined, then skinned. Vertical gradient for a little depth.
            cw = int(sx(cell))
            cellimg = Image.new("RGBA", (cw, cw), (0, 0, 0, 0))
            cd = ImageDraw.Draw(cellimg)
            for yy in range(cw):
                cd.line([(0, yy), (cw, yy)], fill=lerp(accent, accent_dark, yy / cw) + (255,))
            cmask = Image.new("L", (cw, cw), 0)
            ImageDraw.Draw(cmask).rounded_rectangle(
                [0, 0, cw - 1, cw - 1], radius=sx(radius), fill=255
            )
            img.paste(cellimg, (int(sx(x0)), int(sx(y0))), cmask)
        else:
            # A corner: framing. Never visible from inside, so never mined — plain stone.
            d.rounded_rectangle(
                [sx(x0), sx(y0), sx(x1), sx(y1)], radius=sx(radius), fill=neutral + (255,)
            )
            d.rounded_rectangle(
                [sx(x0), sx(y0), sx(x1), sx(y0 + cell * 0.5)],
                radius=sx(radius),
                fill=neutral_hi + (90,),
            )

# --- downscale --------------------------------------------------------------
out = img.resize((N, N), Image.LANCZOS)
out.save(r"C:\Users\jenny\mall-room-builder\src\main\resources\mallroombuilder.png")
print("wrote icon")
