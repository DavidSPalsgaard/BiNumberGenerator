# Tegner programmets ikon (icon.png, icon.ico, icon.icns). Kræver Pillow: python3 lav_ikon.py desktop/icons
import math, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
N = 1024
out = sys.argv[1]
FONT = "/home/user/binumbergenerator/core/src/main/resources/fonts/DejaVuSans-Bold.ttf"

def rounded(size, r, fill):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0]-1, size[1]-1], r, fill=255)
    im = Image.new("RGBA", size, fill)
    im.putalpha(m)
    return im

# Baggrund: orange flise med lodret gradient og højdekurver
bg = Image.new("RGBA", (N, N))
top, bot = (246, 128, 44), (214, 84, 12)
for y in range(N):
    t = y / (N - 1)
    ImageDraw.Draw(bg).line([(0, y), (N, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bot)) + (255,))
d = ImageDraw.Draw(bg)
for ring in range(5):
    r = 520 - ring * 85
    pts = []
    for i in range(181):
        a = 2 * math.pi * i / 180
        w = 1 + 0.09 * math.sin(3 * a + ring * 0.4) + 0.05 * math.sin(5 * a)
        pts.append((830 + r * w * math.cos(a), 900 + r * w * math.sin(a) * 0.8))
    d.line(pts, fill=(255, 255, 255, 46 if ring % 2 else 70), width=10 if ring == 0 else 7)
mask = Image.new("L", (N, N), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, N - 1, N - 1], 220, fill=255)
icon = Image.new("RGBA", (N, N), (0, 0, 0, 0))
icon.paste(bg, (0, 0), mask)

# Startnummeret: hvidt, let drejet, med skygge
bw, bh = 760, 560
bib = rounded((bw, bh), 46, (255, 255, 255, 255))
bd = ImageDraw.Draw(bib)
# Foldehjørner med stiplet linje
for cx, cy, sx, sy in [(0, 0, 1, 1), (bw, 0, -1, 1)]:
    s = 120
    bd.polygon([(cx, cy), (cx + sx * s, cy), (cx, cy + sy * s)], fill=(222, 222, 222, 255))
    for k in range(5):
        a, b = k / 5, (k + 0.55) / 5
        bd.line([(cx + sx * s * (1 - a), cy + sy * s * a), (cx + sx * s * (1 - b), cy + sy * s * b)], fill=(130, 130, 130, 255), width=7)
# Postflag øverst til venstre (efter foldehjørnet)
fx, fy, fs = 150, 52, 120
bd.rectangle([fx, fy, fx + fs, fy + fs], fill=(255, 255, 255, 255), outline=(60, 60, 60, 255), width=6)
bd.polygon([(fx + fs, fy), (fx + fs, fy + fs), (fx, fy + fs)], fill=(238, 106, 27, 255))
# Nummeret
font = ImageFont.truetype(FONT, 400)
txt = "1"
tb = bd.textbbox((0, 0), txt, font=font)
bd.text(((bw - (tb[2] - tb[0])) / 2 - tb[0], 190 + (300 - (tb[3] - tb[1])) / 2 - tb[1] - 40), txt, font=font, fill=(28, 24, 22, 255))
# Stregkode i bunden
x = 210
import random
random.seed(4)
while x < bw - 210:
    w = random.choice([7, 7, 14, 21])
    bd.rectangle([x, bh - 95, x + w, bh - 45], fill=(28, 24, 22, 255))
    x += w + random.choice([7, 14])
bib = bib.rotate(-7, resample=Image.BICUBIC, expand=True)
shadow = Image.new("RGBA", bib.size, (0, 0, 0, 0))
shadow.putalpha(bib.getchannel("A").point(lambda v: int(v * 0.35)))
shadow = shadow.filter(ImageFilter.GaussianBlur(22))
px = (N - bib.size[0]) // 2
py = (N - bib.size[1]) // 2 + 20
icon.alpha_composite(shadow, (px + 10, py + 26))
icon.alpha_composite(bib, (px, py))
# Sikkerhedsnål øverst til højre på nummeret
pin = ImageDraw.Draw(icon)
dx, dy = -50, 78
pin.line([(690 + dx, 238 + dy), (850 + dx, 188 + dy)], fill=(90, 90, 90, 255), width=14)
pin.line([(690 + dx, 262 + dy), (820 + dx, 222 + dy)], fill=(90, 90, 90, 255), width=14)
pin.ellipse([664 + dx, 234 + dy, 704 + dx, 274 + dy], outline=(90, 90, 90, 255), width=12)
pin.rounded_rectangle([815 + dx, 176 + dy, 870 + dx, 236 + dy], 10, fill=(70, 70, 70, 255))

icon.save(out + "/icon.png")
icon.resize((512, 512), Image.LANCZOS).save(out + "/icon-512.png")
icon.save(out + "/icon.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
icon.save(out + "/icon.icns")
