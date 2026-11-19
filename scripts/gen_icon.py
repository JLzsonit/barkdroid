#!/usr/bin/env python3
"""
生成「接收苹果短信」的 App 图标 —— Liquid Glass 风格。

设计：深紫渐变底 + 两层气泡（后层透光玻璃 / 前层实心白气泡 + 尾巴），
      高光在左上、软阴影在右下，整体收在 adaptive icon 的 66dp 安全区里。

产出：
  res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png   (108dp 前景层)
  preview/icon-preview.png                                     (自查用拼图)

用法：python scripts/gen_icon.py
"""

import os
import math

from PIL import Image, ImageChops, ImageDraw, ImageFilter

# ---------------------------------------------------------------- 基本参数
DP = 108                 # adaptive icon 前景层 108dp
S = 16                   # 每 dp 的采样像素（超采样）
N = DP * S               # 1728 x 1728 母版

# 背景渐变（与 res/drawable/ic_launcher_background.xml 保持一致）
BG_TOP = (0x7A, 0x66, 0xF0)
BG_MID = (0x4C, 0x39, 0xB8)
BG_END = (0x20, 0x1A, 0x46)

# 气泡几何（单位 dp，坐标系 108x108，中心 54,54）
GHOST = (30.0, 30.0, 59.0, 53.5)   # 后层：透光玻璃
GHOST_R = 8.2
MAIN = (36.5, 44.5, 74.5, 74.5)    # 前层：实心白气泡
MAIN_R = 11.0


def _quad(p0, c, p1, n=16):
    """二次贝塞尔采样点。"""
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * p1[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * p1[1])
            for t in [i / n for i in range(n + 1)]]


def _arc(cx, cy, r, a0, a1, n=14):
    """圆弧采样点（角度制）。"""
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
             cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / n)))
            for i in range(n + 1)]


# 气泡 + iMessage 尾巴的完整轮廓（顺时针，单一多边形，无拼接缝）。
# 尾巴短而收窄，外缘从左下圆角弧线上长出，端点用小弧帽收圆。
def _main_outline():
    L, T, R, B = MAIN
    r = MAIN_R
    pts = []
    pts += _arc(L + r, T + r, r, 180, 270)          # 左上圆角
    pts += [(R - r, T)]
    pts += _arc(R - r, T + r, r, 270, 360)          # 右上圆角
    pts += [(R, B - r)]
    pts += _arc(R - r, B - r, r, 0, 90)             # 右下圆角
    pts += [(47.0, B - 0.05)]                       # 底边左行到尾巴起点
    pts += _quad((47.0, B - 0.05), (43.6, 75.8), (43.0, 78.2))[1:]   # 尾巴内缘
    pts += _quad((43.0, 78.2), (41.2, 80.6), (39.9, 78.6))[1:]       # 端点小弧帽
    pts += _quad((39.9, 78.6), (37.4, 74.6), (39.1, 70.6))[1:]       # 尾巴外缘
    pts += _arc(L + r, B - r, r, 140, 180)[1:]      # 回到左下圆角弧线
    pts += [(L, T + r)]                             # 左边线收尾
    return pts


MAIN_OUTLINE = _main_outline()


def px(v: float) -> float:
    return v * S


# ---------------------------------------------------------------- 工具函数
def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def vertical_gradient(size, top, bottom):
    """竖直渐变，先画 1px 宽再横向拉伸，快且精准。"""
    w, h = size
    col = Image.new("RGB", (1, h))
    d = ImageDraw.Draw(col)
    for y in range(h):
        d.point((0, y), lerp(top, bottom, y / max(1, h - 1)))
    return col.resize((w, h), Image.BILINEAR)


def rounded_mask(box, radius, size=(N, N)):
    """圆角矩形实心遮罩（后层玻璃泡用）。"""
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle(
        [px(box[0]), px(box[1]), px(box[2]), px(box[3])],
        radius=px(radius), fill=255)
    return m


def outline_mask(points, size=(N, N)):
    """多边形轮廓实心遮罩（前层气泡 + 尾巴用）。"""
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).polygon([(px(x), px(y)) for x, y in points], fill=255)
    return m


def tinted(mask, color, alpha=1.0):
    layer = Image.new("RGBA", mask.size, color + (0,))
    a = mask.point(lambda v: round(v * alpha))
    layer.putalpha(a)
    return layer


def edge_band(mask, width_dp, from_top=True):
    """取形状某一侧的内描边（用于玻璃高光）。"""
    off = max(1, int(px(width_dp)))
    shifted = ImageChops.offset(mask, 0, off if from_top else -off)
    band = ImageChops.subtract(mask, shifted) if from_top else ImageChops.subtract(mask, shifted)
    return band.filter(ImageFilter.GaussianBlur(px(0.25)))


def compose_foreground():
    """返回 1728x1728 RGBA 前景层（透明底）。"""
    canvas = Image.new("RGBA", (N, N), (0, 0, 0, 0))

    ghost = rounded_mask(GHOST, GHOST_R)
    main = outline_mask(MAIN_OUTLINE)
    union = ImageChops.lighter(ghost, main)

    # 1) 环境光晕：让气泡从底色上「浮」起来（玻璃折射感）
    glow = union.filter(ImageFilter.GaussianBlur(px(5.5)))
    glow_a = glow.point(lambda v: round(v * 0.30))
    g = Image.new("RGBA", (N, N), (255, 255, 255, 0))
    g.putalpha(glow_a)
    canvas = Image.alpha_composite(canvas, g)

    # 2) 投影：右下偏移，深紫而非纯黑，避免发灰
    sh = union.filter(ImageFilter.GaussianBlur(px(4.2)))
    sh = ImageChops.offset(sh, 0, int(px(2.2)))
    sh_a = sh.point(lambda v: round(v * 0.55))
    s = Image.new("RGBA", (N, N), (0x17, 0x0F, 0x36, 0))
    s.putalpha(sh_a)
    canvas = Image.alpha_composite(canvas, s)

    # 3) 后层气泡：半透明玻璃 + 细描边 + 内高光
    ghost_layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    ghost_layer = Image.alpha_composite(
        ghost_layer, tinted(ghost, (255, 255, 255), 0.17))
    # 描边
    er = ImageChops.subtract(ghost, ghost.filter(ImageFilter.MinFilter(3)))
    ghost_layer = Image.alpha_composite(
        ghost_layer, tinted(er.point(lambda v: round(v * 0.9)), (255, 255, 255), 0.5))
    ghost_layer = ghost_layer.filter(ImageFilter.GaussianBlur(px(0.18)))
    canvas = Image.alpha_composite(canvas, ghost_layer)

    # 4) 前层气泡：白 → 极淡紫的竖直渐变，模拟透光的玻璃片
    grad = vertical_gradient((N, N), (255, 255, 255), (0xEC, 0xE9, 0xFA))
    body = grad.convert("RGBA")
    body.putalpha(main)
    canvas = Image.alpha_composite(canvas, body)

    # 5) 顶部内高光：一线亮边，是液态玻璃的关键
    hl = edge_band(main, 1.3, from_top=True)
    hl_a = hl.point(lambda v: round(v * 0.95))
    h = Image.new("RGBA", (N, N), (255, 255, 255, 0))
    h.putalpha(hl_a)
    canvas = Image.alpha_composite(canvas, h)

    # 6) 底部内反射：淡淡的紫，呼应背景
    refl = edge_band(main, 2.4, from_top=False)
    refl_a = refl.point(lambda v: round(v * 0.35))
    r = Image.new("RGBA", (N, N), (0x8B, 0x78, 0xFF, 0))
    r.putalpha(refl_a)
    canvas = Image.alpha_composite(canvas, r)

    return canvas


# ---------------------------------------------------------------- 预览
def squircle_mask(size, n=4.2):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    c = size / 2
    r = size / 2
    pts = []
    for i in range(721):
        t = 2 * math.pi * i / 720
        ct, st = math.cos(t), math.sin(t)
        x = c + r * math.copysign(abs(ct) ** (2 / n), ct)
        y = c + r * math.copysign(abs(st) ** (2 / n), st)
        pts.append((x, y))
    d.polygon(pts, fill=255)
    return m


def preview(fg_master, out_path, size=1024):
    # 背景：对角渐变（与 drawable 里的 linear gradient 同参数）+ 两团柔光
    small = 216
    bg = Image.new("RGB", (small, small))
    db = ImageDraw.Draw(bg)
    a = (0.55, -0.35)   # 渐变方向（单位向量近似）
    norm = (a[0] ** 2 + a[1] ** 2) ** 0.5
    a = (a[0] / norm, a[1] / norm)
    for y in range(small):
        for x in range(small):
            t = ((x - small * 0.1) * a[0] + (y + small * 0.35) * a[1]) / small
            t = max(0.0, min(1.0, t))
            col = lerp(BG_TOP, BG_MID, t / 0.45) if t < 0.45 else lerp(BG_MID, BG_END, (t - 0.45) / 0.55)
            db.point((x, y), col)
    bg = bg.resize((size, size), Image.BICUBIC)
    # 左上白晕
    bloom = Image.new("L", (size, size), 0)
    db = ImageDraw.Draw(bloom)
    db.ellipse([-size * 0.28, -size * 0.34, size * 0.72, size * 0.66], fill=64)
    bloom = bloom.filter(ImageFilter.GaussianBlur(size * 0.16))
    bg = Image.composite(Image.new("RGB", (size, size), (255, 255, 255)), bg,
                         bloom.point(lambda v: v // 2))
    # 状态栏上方一点点暗角
    vig = Image.new("L", (size, size), 0)
    ImageDraw.Draw(vig).ellipse([-size * 0.1, size * 0.55, size * 1.1, size * 1.35], fill=40)
    vig = vig.filter(ImageFilter.GaussianBlur(size * 0.12))
    bg = Image.composite(Image.new("RGB", (size, size), (0x14, 0x0F, 0x2E)), bg,
                         vig.point(lambda v: v // 2))

    fg = fg_master.resize((size, size), Image.LANCZOS)
    icon = Image.alpha_composite(bg.convert("RGBA"), fg)
    icon.putalpha(squircle_mask(size))
    icon.save(out_path)

    # 拼图：大图标 + 三种实际尺寸
    sheet = Image.new("RGB", (size + 520, size), (0x0F, 0x0D, 0x1E))
    sheet.paste(icon, (0, 0), icon)
    y = 40
    for s in (192, 96, 60, 42):
        small = icon.resize((s, s), Image.LANCZOS)
        sheet.paste(small, (size + 60, y), small)
        y += s + 34
    sheet.save(out_path.replace("icon-preview", "icon-sizes"))


def rr_path(box, r):
    """圆角矩形的 SVG pathData（单色层用）。"""
    x0, y0, x1, y1 = box
    return (f"M{x0 + r:.2f},{y0:.2f} L{x1 - r:.2f},{y0:.2f} "
            f"A{r:.2f},{r:.2f} 0 0 1 {x1:.2f},{y0 + r:.2f} L{x1:.2f},{y1 - r:.2f} "
            f"A{r:.2f},{r:.2f} 0 0 1 {x1 - r:.2f},{y1:.2f} L{x0 + r:.2f},{y1:.2f} "
            f"A{r:.2f},{r:.2f} 0 0 1 {x0:.2f},{y1 - r:.2f} L{x0:.2f},{y0 + r:.2f} "
            f"A{r:.2f},{r:.2f} 0 0 1 {x0 + r:.2f},{y0:.2f} Z")


def write_monochrome(res):
    """Android 13 主题图标单色层：只保留形状轮廓，颜色由系统染。"""
    main_pd = "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in MAIN_OUTLINE) + " Z"
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 由 scripts/gen_icon.py 生成，勿手改。Android 13+ 主题图标（单色）层 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FF000000"
        android:fillAlpha="0.4"
        android:pathData="{rr_path(GHOST, GHOST_R)}" />
    <path
        android:fillColor="#FF000000"
        android:pathData="{main_pd}" />
</vector>
'''
    with open(os.path.join(res, "drawable", "ic_launcher_monochrome.xml"), "w", encoding="utf-8") as f:
        f.write(xml)
    print("drawable/ic_launcher_monochrome.xml")


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    res = os.path.join(here, "..", "android", "app", "src", "main", "res")
    fg_master = compose_foreground()

    densities = {
        "mipmap-mdpi": 108,
        "mipmap-hdpi": 162,
        "mipmap-xhdpi": 216,
        "mipmap-xxhdpi": 324,
        "mipmap-xxxhdpi": 432,
    }
    for folder, size in densities.items():
        d = os.path.join(res, folder)
        os.makedirs(d, exist_ok=True)
        img = fg_master.resize((size, size), Image.LANCZOS)
        img.save(os.path.join(d, "ic_launcher_foreground.png"), optimize=True)
        print(f"{folder}/ic_launcher_foreground.png  {size}x{size}  {img.size}")

    write_monochrome(res)

    pv_dir = os.path.join(here, "..", "preview")
    os.makedirs(pv_dir, exist_ok=True)
    preview(fg_master, os.path.join(pv_dir, "icon-preview.png"))


if __name__ == "__main__":
    main()
