import math
import os
import struct
import subprocess
import wave

WORK = "/tmp/snapstream_ad"
FONT_DISPLAY = "app/src/main/res/font/space_grotesk.ttf"
FONT_BODY = "app/src/main/res/font/plus_jakarta_sans.ttf"
ICON_PATH = "play_store_assets/app_icon_512x512.png"

SCENE_CHAT = f"{WORK}/ad_scene_friends_chat_1795706011946.jpg"
SCENE_INSTALL = f"{WORK}/ad_scene_downloading_1795706032460.jpg"
SCENE_ENJOY = f"{WORK}/ad_scene_friend_enjoy_1795706012564.jpg"

os.makedirs(WORK, exist_ok=True)
os.makedirs("play_store_assets", exist_ok=True)


def run(cmd):
    print("RUN:", " ".join(cmd) if isinstance(cmd, list) else cmd)
    subprocess.run(cmd, check=True)


def make_caption_png(out_path, text, width, font, size, color, bold=False):
    cmd = [
        "convert",
        "-background", "none",
        "-fill", color,
        "-font", font,
        "-pointsize", str(size),
        "-size", f"{width}x",
        "-gravity", "West",
        f"caption:{text}",
        out_path,
    ]
    run(cmd)


def make_rounded_thumb(src_img, out_path, w, h, crop_region=None, tint_mod=None, radius=20):
    mask = f"{WORK}/mask_{w}x{h}_{radius}.png"
    if not os.path.exists(mask):
        run([
            "convert", "-size", f"{w}x{h}", "xc:none",
            "-fill", "white", "-draw", f"roundrectangle 0,0 {w-1},{h-1} {radius},{radius}",
            mask,
        ])
    cmd = ["convert", src_img]
    if crop_region:
        cmd += ["-crop", crop_region, "+repage"]
    cmd += ["-resize", f"{w}x{h}^", "-gravity", "center", "-extent", f"{w}x{h}"]
    if tint_mod:
        cmd += tint_mod
    cmd += [mask, "-compose", "CopyOpacity", "-composite", out_path]
    run(cmd)


def build_phone_frame_base(out_path, dark=False):
    # Phone frame: 520 x 940
    w, h = 520, 940
    screen_bg = "#090D16" if dark else "#FFFFFF"
    bezel = "#111827" if not dark else "#1E293B"
    cmd = [
        "convert", "-size", f"{w}x{h}", "xc:none",
        "-fill", "#00000044", "-draw", f"roundrectangle 12,16 {w-2},{h-2} 54,54",
        "-fill", bezel, "-draw", f"roundrectangle 4,4 {w-12},{h-12} 52,52",
        "-fill", screen_bg, "-draw", f"roundrectangle 18,18 {w-26},{h-26} 40,40",
        # Camera punch hole
        "-fill", "#0F172A", "-draw", f"circle {w//2 - 4},38 {w//2 + 5},38",
        out_path,
    ]
    run(cmd)


def build_dialogue_overlay(out_path, badge_text, badge_color, speaker, line_text, rec_card=False):
    # Full 1920x1080 transparent overlay with sleek bottom gradient + glassmorphic dialogue card
    w, h = 1920, 1080
    cap_path = f"{WORK}/cap_{os.path.basename(out_path)}"
    make_caption_png(cap_path, line_text, 1380, FONT_BODY, 38, "#FFFFFF")

    cmd = [
        "convert", "-size", f"{w}x{h}", "xc:none",
        # Bottom dark scrim
        "-fill", "#050A18C8", "-draw", "roundrectangle 90,770 1830,1010 30,30",
        "-stroke", "#2979FF88", "-strokewidth", "2", "-fill", "none",
        "-draw", "roundrectangle 90,770 1830,1010 30,30",
        "-stroke", "none",
        # Speaker pill
        "-fill", badge_color, "-draw", "roundrectangle 135,798 355,848 16,16",
        "-font", FONT_DISPLAY, "-pointsize", "26", "-fill", "#FFFFFF",
        "-annotate", "+165+832", speaker,
        # Top-left tag pill
        "-fill", "#090D16CC", "-draw", "roundrectangle 70,56 450,114 20,20",
        "-font", FONT_DISPLAY, "-pointsize", "24", "-fill", "#82B1FF",
        "-annotate", "+96+94", badge_text,
        cap_path, "-geometry", "+135+868", "-composite",
    ]

    if rec_card:
        # Floating SnapStream recommendation card on top-right
        icon_sm = f"{WORK}/icon_110.png"
        run(["convert", ICON_PATH, "-resize", "110x110", icon_sm])
        card = f"{WORK}/rec_card.png"
        run([
            "convert", "-size", "520x160", "xc:none",
            "-fill", "#FFFFFFF2", "-draw", "roundrectangle 0,0 519,159 28,28",
            "-stroke", "#0057FF", "-strokewidth", "3", "-fill", "none",
            "-draw", "roundrectangle 1,1 518,158 28,28",
            "-stroke", "none",
            icon_sm, "-geometry", "+24+25", "-composite",
            "-font", FONT_DISPLAY, "-pointsize", "34", "-fill", "#0B1220",
            "-annotate", "+154+68", "SnapStream",
            "-font", FONT_BODY, "-pointsize", "22", "-fill", "#0057FF",
            "-annotate", "+154+102", "Offline Explorer & Studio",
            "-font", FONT_BODY, "-pointsize", "20", "-fill", "#4B5565",
            "-annotate", "+154+132", "4.9 Rated  •  Free  •  Zero Ads",
            card,
        ])
        cmd += [card, "-geometry", "+1330+56", "-composite"]

    cmd.append(out_path)
    run(cmd)


def build_install_overlay(out_path, phase_label, progress_pct, button_text, button_color):
    # Right-side Google Play install card + bottom caption over ad_scene_downloading
    w, h = 1920, 1080
    icon_140 = f"{WORK}/icon_140.png"
    run(["convert", ICON_PATH, "-resize", "140x140", icon_140])

    bar_w = 560
    fill_w = max(12, int(bar_w * progress_pct / 100.0))

    card = f"{WORK}/install_card_{progress_pct}.png"
    run([
        "convert", "-size", "660x560", "xc:none",
        "-fill", "#FFFFFFF7", "-draw", "roundrectangle 0,0 659,559 36,36",
        "-stroke", "#0057FF", "-strokewidth", "3", "-fill", "none",
        "-draw", "roundrectangle 1,1 658,558 36,36",
        "-stroke", "none",
        # Google Play header strip
        "-fill", "#EFF4FF", "-draw", "roundrectangle 24,24 636,88 18,18",
        "-font", FONT_DISPLAY, "-pointsize", "24", "-fill", "#0057FF",
        "-annotate", "+48+65", "Google Play  •  App Store",
        # App icon + title
        icon_140, "-geometry", "+44+120", "-composite",
        "-font", FONT_DISPLAY, "-pointsize", "42", "-fill", "#0B1220",
        "-annotate", "+208+172", "SnapStream",
        "-font", FONT_BODY, "-pointsize", "24", "-fill", "#4B5565",
        "-annotate", "+208+212", "DevEkene  •  Tools & Media",
        "-font", FONT_BODY, "-pointsize", "22", "-fill", "#0057FF",
        "-annotate", "+208+248", "4.9 Rated  •  Offline  •  v0.0.1",
        # Progress bar track & fill
        "-font", FONT_BODY, "-pointsize", "24", "-fill", "#1E293B",
        "-annotate", "+48+322", phase_label,
        "-fill", "#E2E8F0", "-draw", f"roundrectangle 48,344 {48+bar_w},368 12,12",
        "-fill", "#0057FF", "-draw", f"roundrectangle 48,344 {48+fill_w},368 12,12",
        # Action button
        "-fill", button_color, "-draw", "roundrectangle 48,418 612,504 22,22",
        "-font", FONT_DISPLAY, "-pointsize", "30", "-fill", "#FFFFFF",
        "-gravity", "NorthWest", "-annotate", "+235+448", button_text,
        card,
    ])

    cap_path = f"{WORK}/cap_install_{progress_pct}.png"
    make_caption_png(
        cap_path,
        "Alex downloads SnapStream from Google Play and scans all device photos & videos offline.",
        1420, FONT_BODY, 34, "#FFFFFF"
    )

    run([
        "convert", "-size", f"{w}x{h}", "xc:none",
        # Subtle right-side focus backdrop
        "-fill", "#050A1877", "-draw", "rectangle 1060,0 1920,1080",
        card, "-geometry", "+1150+155", "-composite",
        "-fill", "#050A18DF", "-draw", "roundrectangle 90,820 1830,1028 28,28",
        "-fill", "#0057FF", "-draw", "roundrectangle 130,846 310,892 14,14",
        "-font", FONT_DISPLAY, "-pointsize", "22", "-fill", "#FFFFFF",
        "-annotate", "+156+877", "INSTALLING",
        cap_path, "-geometry", "+130+906", "-composite",
        out_path,
    ])


def build_feature_slide(
    out_path,
    feature_num,
    headline,
    description,
    bullets,
    phone_mockup_path,
    dark=False,
):
    w, h = 1920, 1080
    bg = "#090D16" if dark else "#FFFFFF"
    accent_bg = "#0F172A" if dark else "#EFF4FF"
    text_primary = "#F8FAFF" if dark else "#0B1220"
    text_secondary = "#94A3B8" if dark else "#4B5565"
    pill_bg = "#1E3A8A" if dark else "#DBE8FF"
    pill_fg = "#93C5FD" if dark else "#0057FF"
    accent = "#2979FF" if dark else "#0057FF"

    head_png = f"{WORK}/head_{feature_num}.png"
    make_caption_png(head_png, headline, 920, FONT_DISPLAY, 62, text_primary)
    desc_png = f"{WORK}/desc_{feature_num}.png"
    make_caption_png(desc_png, description, 900, FONT_BODY, 32, text_secondary)

    cmd = [
        "convert", "-size", f"{w}x{h}", f"xc:{bg}",
        # Right decorative geometric card backdrop
        "-fill", accent_bg, "-draw", "roundrectangle 1130,50 1860,1030 48,48",
        # Electric blue accent stripe on left
        "-fill", accent, "-draw", "roundrectangle 96,110 110,230 7,7",
        # Feature pill
        "-fill", pill_bg, "-draw", "roundrectangle 136,110 430,164 20,20",
        "-font", FONT_DISPLAY, "-pointsize", "24", "-fill", pill_fg,
        "-annotate", "+164+146", f"FEATURE 0{feature_num} OF 04",
        # Brand header top-left small
        "-font", FONT_DISPLAY, "-pointsize", "28", "-fill", accent,
        "-annotate", "+136+214", "SnapStream  •  Offline Explorer & Studio",
        head_png, "-geometry", "+136+270", "-composite",
        desc_png, "-geometry", "+136+465", "-composite",
    ]

    # Bullet rows
    y = 635
    for idx, b in enumerate(bullets):
        bp = f"{WORK}/bullet_{feature_num}_{idx}.png"
        make_caption_png(bp, b, 830, FONT_BODY, 28, text_primary)
        cmd += [
            "-fill", accent, "-draw", f"circle 154,{y+20} 168,{y+20}",
            bp, "-geometry", f"+190+{y}", "-composite",
        ]
        y += 88

    # Phone mockup on right
    cmd += [phone_mockup_path, "-geometry", "+1235+70", "-composite", out_path]
    run(cmd)


def build_phone_mockups():
    # Prepare real cropped thumbnails from generated scenes (zero dummy downloads!)
    t1 = f"{WORK}/thumb1.png"
    t2 = f"{WORK}/thumb2.png"
    t3 = f"{WORK}/thumb3.png"
    t4 = f"{WORK}/thumb4.png"
    t3_noir = f"{WORK}/thumb3_noir.png"
    t3_vivid = f"{WORK}/thumb3_vivid.png"
    t3_warm = f"{WORK}/thumb3_warm.png"

    make_rounded_thumb(SCENE_ENJOY, t1, 215, 215, crop_region="900x900+300+60", radius=18)
    make_rounded_thumb(SCENE_CHAT, t2, 215, 215, crop_region="900x900+250+60", radius=18)
    make_rounded_thumb(SCENE_ENJOY, t3, 215, 215, crop_region="800x800+550+80", radius=18)
    make_rounded_thumb(SCENE_INSTALL, t4, 215, 215, crop_region="850x850+450+80", radius=18)

    # Filter preview swatches
    make_rounded_thumb(SCENE_ENJOY, t3_vivid, 96, 96, crop_region="600x600+400+80", tint_mod=["-modulate", "105,145,100"], radius=14)
    make_rounded_thumb(SCENE_ENJOY, t3_noir, 96, 96, crop_region="600x600+400+80", tint_mod=["-colorspace", "Gray", "-contrast"], radius=14)
    make_rounded_thumb(SCENE_ENJOY, t3_warm, 96, 96, crop_region="600x600+400+80", tint_mod=["-modulate", "104,120,92"], radius=14)

    phone_light = f"{WORK}/phone_light.png"
    phone_dark = f"{WORK}/phone_dark.png"
    build_phone_frame_base(phone_light, dark=False)
    build_phone_frame_base(phone_dark, dark=True)

    # Mockup 1: Offline Device Scan & Folder Explorer (Light White + Electric Blue)
    m1 = f"{WORK}/mockup_f1.png"
    run([
        "convert", phone_light,
        "-font", FONT_DISPLAY, "-pointsize", "26", "-fill", "#0B1220",
        "-annotate", "+44+98", "SnapStream",
        "-font", FONT_BODY, "-pointsize", "16", "-fill", "#0057FF",
        "-annotate", "+44+122", "148 files scanned on device",
        # Filter chips
        "-fill", "#0057FF", "-draw", "roundrectangle 40,144 108,182 14,14",
        "-font", FONT_DISPLAY, "-pointsize", "16", "-fill", "#FFFFFF", "-annotate", "+60+169", "All",
        "-fill", "#F1F5FF", "-draw", "roundrectangle 118,144 214,182 14,14",
        "-fill", "#334155", "-annotate", "+136+169", "Photos",
        "-fill", "#F1F5FF", "-draw", "roundrectangle 224,144 322,182 14,14",
        "-fill", "#334155", "-annotate", "+244+169", "Videos",
        "-fill", "#F1F5FF", "-draw", "roundrectangle 332,144 472,182 14,14",
        "-fill", "#334155", "-annotate", "+348+169", "Large >10MB",
        # Folder chips
        "-fill", "#DBE8FF", "-draw", "roundrectangle 40,196 168,230 12,12",
        "-font", FONT_BODY, "-pointsize", "15", "-fill", "#0057FF", "-annotate", "+56+219", "All Folders",
        "-fill", "#F4F7FF", "-draw", "roundrectangle 178,196 280,230 12,12",
        "-fill", "#475569", "-annotate", "+196+219", "Camera",
        "-fill", "#F4F7FF", "-draw", "roundrectangle 290,196 420,230 12,12",
        "-fill", "#475569", "-annotate", "+306+219", "Screenshots",
        # Storage banner
        "-fill", "#EFF4FF", "-draw", "roundrectangle 40,248 472,324 18,18",
        "-font", FONT_DISPLAY, "-pointsize", "19", "-fill", "#0B1220", "-annotate", "+60+280", "148 Media Files",
        "-font", FONT_BODY, "-pointsize", "15", "-fill", "#4B5565", "-annotate", "+60+305", "1.4 GB media  •  42.8 GB free",
        "-fill", "#0057FF", "-draw", "roundrectangle 368,266 456,304 12,12",
        "-font", FONT_DISPLAY, "-pointsize", "15", "-fill", "#FFFFFF", "-annotate", "+386+291", "Device",
        # 2x2 Grid
        t1, "-geometry", "+40+342", "-composite",
        t2, "-geometry", "+264+342", "-composite",
        t3, "-geometry", "+40+574", "-composite",
        t4, "-geometry", "+264+574", "-composite",
        # Video badge on tile 2
        "-fill", "#000000BB", "-draw", "roundrectangle 276,356 356,384 8,8",
        "-font", FONT_BODY, "-pointsize", "14", "-fill", "#FFFFFF", "-annotate", "+290+375", "VID 0:24",
        # Bottom nav bar
        "-fill", "#F8FAFF", "-draw", "roundrectangle 24,824 488,904 0,0",
        "-fill", "#DBE8FF", "-draw", "roundrectangle 58,838 158,874 16,16",
        "-font", FONT_DISPLAY, "-pointsize", "15", "-fill", "#0057FF", "-annotate", "+78+862", "Gallery",
        "-fill", "#64748B", "-annotate", "+232+862", "Videos",
        "-fill", "#64748B", "-annotate", "+378+862", "Profile",
        m1,
    ])

    # Mockup 2: List View + Multi-Select
    m2 = f"{WORK}/mockup_f2.png"
    sm1 = f"{WORK}/sm1.png"
    sm2 = f"{WORK}/sm2.png"
    sm3 = f"{WORK}/sm3.png"
    sm4 = f"{WORK}/sm4.png"
    make_rounded_thumb(SCENE_ENJOY, sm1, 78, 78, crop_region="700x700+300+80", radius=14)
    make_rounded_thumb(SCENE_CHAT, sm2, 78, 78, crop_region="700x700+250+80", radius=14)
    make_rounded_thumb(SCENE_INSTALL, sm3, 78, 78, crop_region="700x700+450+80", radius=14)
    make_rounded_thumb(SCENE_ENJOY, sm4, 78, 78, crop_region="700x700+650+80", radius=14)

    run([
        "convert", phone_light,
        # Multi-select Top Bar
        "-fill", "#DBE8FF", "-draw", "roundrectangle 24,52 488,132 24,24",
        "-font", FONT_DISPLAY, "-pointsize", "24", "-fill", "#003EC2",
        "-annotate", "+56+102", "2 selected",
        "-font", FONT_BODY, "-pointsize", "17", "-fill", "#0057FF",
        "-annotate", "+280+100", "Star   Share   Delete",
        # View toggle label
        "-fill", "#F4F7FF", "-draw", "roundrectangle 40,150 472,196 14,14",
        "-font", FONT_DISPLAY, "-pointsize", "17", "-fill", "#0057FF",
        "-annotate", "+60+179", "List Mode Active  •  Sorted: Largest First",
        # Row 1 (selected)
        "-fill", "#DBE8FF", "-draw", "roundrectangle 40,214 472,326 20,20",
        "-stroke", "#0057FF", "-strokewidth", "3", "-fill", "none",
        "-draw", "roundrectangle 40,214 472,326 20,20", "-stroke", "none",
        sm1, "-geometry", "+58+231", "-composite",
        "-font", FONT_DISPLAY, "-pointsize", "20", "-fill", "#0B1220", "-annotate", "+152+258", "Summer_Trip_Clip.mp4",
        "-font", FONT_BODY, "-pointsize", "16", "-fill", "#334155", "-annotate", "+152+284", "Movies • 42.4 MB • 0:38",
        "-font", FONT_BODY, "-pointsize", "14", "-fill", "#0057FF", "-annotate", "+152+308", "Selected",
        # Row 2 (selected)
        "-fill", "#DBE8FF", "-draw", "roundrectangle 40,342 472,454 20,20",
        "-stroke", "#0057FF", "-strokewidth", "3", "-fill", "none",
        "-draw", "roundrectangle 40,342 472,454 20,20", "-stroke", "none",
        sm2, "-geometry", "+58+359", "-composite",
        "-font", FONT_DISPLAY, "-pointsize", "20", "-fill", "#0B1220", "-annotate", "+152+386", "Cafe_Portrait_04.jpg",
        "-font", FONT_BODY, "-pointsize", "16", "-fill", "#334155", "-annotate", "+152+412", "Camera • 6.2 MB • 4032×3024",
        "-font", FONT_BODY, "-pointsize", "14", "-fill", "#0057FF", "-annotate", "+152+436", "Selected",
        # Row 3
        "-fill", "#F4F7FF", "-draw", "roundrectangle 40,470 472,582 20,20",
        sm3, "-geometry", "+58+487", "-composite",
        "-font", FONT_DISPLAY, "-pointsize", "20", "-fill", "#0B1220", "-annotate", "+152+514", "Receipt_Scan_12.png",
        "-font", FONT_BODY, "-pointsize", "16", "-fill", "#4B5565", "-annotate", "+152+540", "Screenshots • 1.8 MB",
        # Row 4
        "-fill", "#F4F7FF", "-draw", "roundrectangle 40,598 472,710 20,20",
        sm4, "-geometry", "+58+615", "-composite",
        "-font", FONT_DISPLAY, "-pointsize", "20", "-fill", "#0B1220", "-annotate", "+152+642", "Beach_Sunset_Edit.jpg",
        "-font", FONT_BODY, "-pointsize", "16", "-fill", "#4B5565", "-annotate", "+152+668", "SnapStream • 4.1 MB",
        # Bottom nav bar
        "-fill", "#F8FAFF", "-draw", "roundrectangle 24,824 488,904 0,0",
        "-fill", "#DBE8FF", "-draw", "roundrectangle 58,838 158,874 16,16",
        "-font", FONT_DISPLAY, "-pointsize", "15", "-fill", "#0057FF", "-annotate", "+78+862", "Gallery",
        "-fill", "#64748B", "-annotate", "+232+862", "Videos",
        "-fill", "#64748B", "-annotate", "+378+862", "Profile",
        m2,
    ])

    # Mockup 3: Studio Filters & Tone Sliders (Dark Studio UI)
    m3 = f"{WORK}/mockup_f3.png"
    hero_edit = f"{WORK}/hero_edit.png"
    make_rounded_thumb(SCENE_ENJOY, hero_edit, 432, 390, crop_region="1200x1050+320+20", tint_mod=["-modulate", "104,135,100"], radius=22)
    run([
        "convert", phone_dark,
        "-font", FONT_DISPLAY, "-pointsize", "24", "-fill", "#FFFFFF", "-annotate", "+44+98", "SnapStream Studio",
        "-fill", "#2979FF", "-draw", "roundrectangle 380,68 476,112 14,14",
        "-font", FONT_DISPLAY, "-pointsize", "18", "-fill", "#FFFFFF", "-annotate", "+404+96", "Save",
        hero_edit, "-geometry", "+40+136", "-composite",
        # Bottom studio sheet
        "-fill", "#0E1422", "-draw", "roundrectangle 24,552 488,904 28,28",
        t3_vivid, "-geometry", "+44+578", "-composite",
        t3_noir, "-geometry", "+154+578", "-composite",
        t3_warm, "-geometry", "+264+578", "-composite",
        t3_vivid, "-geometry", "+374+578", "-composite",
        "-stroke", "#2979FF", "-strokewidth", "3", "-fill", "none",
        "-draw", "roundrectangle 44,578 140,674 14,14", "-stroke", "none",
        "-font", FONT_DISPLAY, "-pointsize", "15", "-fill", "#2979FF", "-annotate", "+64+696", "Electric",
        "-fill", "#CBD5E1", "-annotate", "+184+696", "Noir",
        "-fill", "#CBD5E1", "-annotate", "+284+696", "Warmth",
        "-fill", "#CBD5E1", "-annotate", "+396+696", "Vivid",
        # Sliders
        "-font", FONT_BODY, "-pointsize", "16", "-fill", "#E2E8F0", "-annotate", "+44+742", "Intensity",
        "-fill", "#1E293B", "-draw", "roundrectangle 140,732 456,742 5,5",
        "-fill", "#2979FF", "-draw", "roundrectangle 140,732 380,742 5,5",
        "-fill", "#FFFFFF", "-draw", "circle 380,737 390,737",
        "-font", FONT_BODY, "-pointsize", "16", "-fill", "#E2E8F0", "-annotate", "+44+792", "Contrast",
        "-fill", "#1E293B", "-draw", "roundrectangle 140,782 456,792 5,5",
        "-fill", "#2979FF", "-draw", "roundrectangle 140,782 325,792 5,5",
        "-fill", "#FFFFFF", "-draw", "circle 325,787 335,787",
        # Tool tabs
        "-fill", "#2979FF33", "-draw", "roundrectangle 44,832 144,884 14,14",
        "-font", FONT_DISPLAY, "-pointsize", "16", "-fill", "#2979FF", "-annotate", "+66+864", "Filters",
        "-fill", "#94A3B8", "-annotate", "+180+864", "Crop",
        "-fill", "#94A3B8", "-annotate", "+280+864", "Adjust",
        "-fill", "#94A3B8", "-annotate", "+390+864", "Details",
        m3,
    ])

    # Mockup 4: Crop Overlay & Video Trim
    m4 = f"{WORK}/mockup_f4.png"
    hero_crop = f"{WORK}/hero_crop.png"
    make_rounded_thumb(SCENE_CHAT, hero_crop, 432, 390, crop_region="1200x1050+320+20", radius=22)
    run([
        "convert", phone_dark,
        "-font", FONT_DISPLAY, "-pointsize", "24", "-fill", "#FFFFFF", "-annotate", "+44+98", "SnapStream Studio",
        "-fill", "#2979FF", "-draw", "roundrectangle 380,68 476,112 14,14",
        "-font", FONT_DISPLAY, "-pointsize", "18", "-fill", "#FFFFFF", "-annotate", "+404+96", "Save",
        hero_crop, "-geometry", "+40+136", "-composite",
        # Crop grid overlay
        "-fill", "#00000066", "-draw", "rectangle 40,136 472,188",
        "-draw", "rectangle 40,468 472,526",
        "-fill", "none", "-stroke", "#FFFFFF", "-strokewidth", "3",
        "-draw", "rectangle 76,188 436,468",
        "-stroke", "#FFFFFF66", "-strokewidth", "1",
        "-draw", "line 196,188 196,468",
        "-draw", "line 316,188 316,468",
        "-draw", "line 76,281 436,281",
        "-draw", "line 76,374 436,374",
        # Corner handles
        "-stroke", "#2979FF", "-strokewidth", "5",
        "-draw", "line 76,188 108,188", "-draw", "line 76,188 76,220",
        "-draw", "line 436,188 404,188", "-draw", "line 436,188 436,220",
        "-draw", "line 76,468 108,468", "-draw", "line 76,468 76,436",
        "-draw", "line 436,468 404,468", "-draw", "line 436,468 436,436",
        "-stroke", "none",
        # Bottom studio sheet
        "-fill", "#0E1422", "-draw", "roundrectangle 24,552 488,904 28,28",
        "-font", FONT_BODY, "-pointsize", "16", "-fill", "#94A3B8", "-annotate", "+44+586", "Aspect Ratio & Geometry",
        "-fill", "#1E293B", "-draw", "roundrectangle 44,606 118,646 12,12",
        "-font", FONT_DISPLAY, "-pointsize", "16", "-fill", "#E2E8F0", "-annotate", "+64+632", "Free",
        "-fill", "#2979FF", "-draw", "roundrectangle 130,606 204,646 12,12",
        "-fill", "#FFFFFF", "-annotate", "+152+632", "16:9",
        "-fill", "#1E293B", "-draw", "roundrectangle 216,606 290,646 12,12",
        "-fill", "#E2E8F0", "-annotate", "+240+632", "1:1",
        "-fill", "#1E293B", "-draw", "roundrectangle 302,606 376,646 12,12",
        "-fill", "#E2E8F0", "-annotate", "+324+632", "4:3",
        "-fill", "#1E293B", "-draw", "roundrectangle 388,606 462,646 12,12",
        "-fill", "#E2E8F0", "-annotate", "+410+632", "9:16",
        # Video Trim bar
        "-font", FONT_BODY, "-pointsize", "16", "-fill", "#E2E8F0", "-annotate", "+44+690", "Trim Clip: 0:03 – 0:19",
        "-fill", "#1E293B", "-draw", "roundrectangle 44,712 464,730 9,9",
        "-fill", "#2979FF", "-draw", "roundrectangle 110,712 370,730 9,9",
        "-fill", "#FFFFFF", "-draw", "circle 110,721 122,721",
        "-fill", "#FFFFFF", "-draw", "circle 370,721 382,721",
        # Rotate & Flip buttons
        "-fill", "#19233B", "-draw", "roundrectangle 44,756 244,804 14,14",
        "-font", FONT_DISPLAY, "-pointsize", "16", "-fill", "#FFFFFF", "-annotate", "+92+786", "Rotate 90°",
        "-fill", "#19233B", "-draw", "roundrectangle 260,756 464,804 14,14",
        "-fill", "#82B1FF", "-annotate", "+312+786", "Flip Horiz",
        # Tool tabs
        "-font", FONT_DISPLAY, "-pointsize", "16", "-fill", "#94A3B8", "-annotate", "+66+864", "Filters",
        "-fill", "#2979FF33", "-draw", "roundrectangle 154,832 254,884 14,14",
        "-fill", "#2979FF", "-annotate", "+180+864", "Crop",
        "-fill", "#94A3B8", "-annotate", "+280+864", "Adjust",
        "-fill", "#94A3B8", "-annotate", "+390+864", "Details",
        m4,
    ])
    return m1, m2, m3, m4


def build_end_card(out_path):
    w, h = 1920, 1080
    icon_big = f"{WORK}/icon_300.png"
    run(["convert", ICON_PATH, "-resize", "300x300", icon_big])
    mask = f"{WORK}/mask_300.png"
    run([
        "convert", "-size", "300x300", "xc:none",
        "-fill", "white", "-draw", "roundrectangle 0,0 299,299 64,64",
        mask,
    ])
    icon_round = f"{WORK}/icon_300_round.png"
    run(["convert", icon_big, mask, "-compose", "CopyOpacity", "-composite", icon_round])

    run([
        "convert", "-size", f"{w}x{h}", "gradient:#0057FF-#071946",
        # Crisp white center card
        "-fill", "#FFFFFFFA", "-draw", "roundrectangle 210,130 1710,950 56,56",
        icon_round, "-geometry", "+310+310", "-composite",
        "-font", FONT_DISPLAY, "-pointsize", "92", "-fill", "#0B1220",
        "-annotate", "+665+370", "SnapStream",
        "-font", FONT_BODY, "-pointsize", "36", "-fill", "#4B5565",
        "-annotate", "+665+435", "Your Offline Photo & Video Explorer + Studio",
        # Feature pills (set -fill #0057FF on each label after #EFF4FF pill background!)
        "-fill", "#EFF4FF", "-draw", "roundrectangle 665,485 900,545 20,20",
        "-font", FONT_DISPLAY, "-pointsize", "24", "-fill", "#0057FF", "-annotate", "+705+523", "100% Offline",
        "-fill", "#EFF4FF", "-draw", "roundrectangle 920,485 1175,545 20,20",
        "-font", FONT_DISPLAY, "-pointsize", "24", "-fill", "#0057FF", "-annotate", "+954+523", "Grid & List View",
        "-fill", "#EFF4FF", "-draw", "roundrectangle 1195,485 1475,545 20,20",
        "-font", FONT_DISPLAY, "-pointsize", "24", "-fill", "#0057FF", "-annotate", "+1228+523", "Filters & Cropping",
        # Google Play CTA pill with crisp drawn play triangle
        "-fill", "#0057FF", "-draw", "roundrectangle 665,610 1475,745 32,32",
        "-fill", "#FFFFFF", "-draw", "polygon 750,656 750,702 790,679",
        "-font", FONT_DISPLAY, "-pointsize", "46", "-fill", "#FFFFFF",
        "-annotate", "+818+694", "Get yours on Google Play",
        "-font", FONT_BODY, "-pointsize", "24", "-fill", "#4B5565",
        "-annotate", "+665+825", "No account needed  •  No cloud clutter  •  Works on every Android device",
        out_path,
    ])


def synthesize_audio(wav_path, duration_sec=54.0):
    sample_rate = 44100
    n_samples = int(duration_sec * sample_rate)
    # Warm commercial progression: Cmaj9 -> G6 -> Am9 -> Fmaj7 (2 sec per chord)
    chords = [
        [261.63, 329.63, 392.00, 493.88, 587.33],
        [196.00, 293.66, 392.00, 493.88, 659.25],
        [220.00, 329.63, 440.00, 523.25, 659.25],
        [174.61, 261.63, 349.23, 440.00, 523.25],
    ]
    transition_times = [6.0, 12.0, 19.0, 25.0, 31.0, 37.0, 43.0, 48.5]

    frames = bytearray()
    for i in range(n_samples):
        t = i / sample_rate
        chord_idx = int(t / 2.0) % len(chords)
        local_t = t % 2.0
        chord = chords[chord_idx]

        # Warm pad + gentle rhythmic pluck
        pad_env = 0.7 + 0.3 * math.sin(math.pi * min(1.0, local_t / 0.35))
        pad = 0.0
        for idx, freq in enumerate(chord):
            pad += math.sin(2.0 * math.pi * freq * t) * (0.28 / (idx + 1.2))
        pad *= pad_env * 0.32

        # Soft 0.5s beat pulse / marimba-like arp
        step = int(t * 2.0)
        arp_t = (t * 2.0) % 1.0
        arp_freq = chord[step % len(chord)] * 2.0
        arp_env = math.exp(-arp_t * 6.5)
        arp = math.sin(2.0 * math.pi * arp_freq * t) * arp_env * 0.14

        # Subtle chime on scene transitions
        chime = 0.0
        for tt in transition_times:
            dt = t - tt
            if 0.0 <= dt <= 0.65:
                chime += math.sin(2.0 * math.pi * 880.0 * dt) * math.exp(-dt * 8.0) * 0.14

        # Smooth intro/outro fade
        master = 1.0
        if t < 0.8:
            master = t / 0.8
        elif t > duration_sec - 1.5:
            master = max(0.0, (duration_sec - t) / 1.5)

        val = (pad + arp + chime) * master
        sample = max(-32000, min(32000, int(val * 32767)))
        frames += struct.pack("<hh", sample, sample)

    with wave.open(wav_path, "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(frames)


def render_clip_zoom_overlay(bg_img, overlay_img, out_mp4, duration_sec, zoom_dir="in"):
    fps = 30
    frames = int(duration_sec * fps)
    if zoom_dir == "in":
        zp = f"zoompan=z='min(zoom+0.0005,1.10)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d={frames}:s=1920x1080:fps={fps}"
    else:
        zp = f"zoompan=z='min(zoom+0.0005,1.10)':x='iw*0.62-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d={frames}:s=1920x1080:fps={fps}"

    cmd = [
        "ffmpeg", "-y",
        "-loop", "1", "-i", bg_img,
        "-loop", "1", "-i", overlay_img,
        "-filter_complex",
        f"[0:v]scale=2304:1296,{zp},setsar=1[bg];[1:v]scale=1920:1080[ov];[bg][ov]overlay=0:0:shortest=1[v]",
        "-map", "[v]",
        "-t", f"{duration_sec:.2f}",
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-r", str(fps), "-preset", "fast", "-crf", "18",
        out_mp4,
    ]
    run(cmd)


def render_static_motion_clip(slide_png, out_mp4, duration_sec, zoom=True):
    fps = 30
    frames = int(duration_sec * fps)
    if zoom:
        vf = f"scale=2048:1152,zoompan=z='min(zoom+0.00025,1.04)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d={frames}:s=1920x1080:fps={fps},setsar=1"
    else:
        vf = f"scale=1920:1080,setsar=1"
    cmd = [
        "ffmpeg", "-y",
        "-loop", "1", "-i", slide_png,
        "-vf", vf,
        "-t", f"{duration_sec:.2f}",
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-r", str(fps), "-preset", "fast", "-crf", "18",
        out_mp4,
    ]
    run(cmd)


def main():
    ov1 = f"{WORK}/ov_chat1.png"
    ov2 = f"{WORK}/ov_chat2.png"
    ov3a = f"{WORK}/ov_inst1.png"
    ov3b = f"{WORK}/ov_inst2.png"
    ov3c = f"{WORK}/ov_inst3.png"
    ov8 = f"{WORK}/ov_enjoy.png"

    build_dialogue_overlay(
        ov1,
        "EVERYDAY PHONE STRUGGLE",
        "#EF4444",
        "Alex",
        "\"Why is it so hard to find a clean photo & video app that works offline without clutter?\"",
        rec_card=False,
    )
    build_dialogue_overlay(
        ov2,
        "FRIEND RECOMMENDATION",
        "#0057FF",
        "Maya",
        "\"Use SnapStream! It scans your device folders offline and edits photos & videos in seconds.\"",
        rec_card=True,
    )

    build_install_overlay(ov3a, "Downloading SnapStream... 42%", 42, "Downloading...", "#0057FF")
    build_install_overlay(ov3b, "Installing offline studio... 92%", 92, "Installing...", "#0057FF")
    build_install_overlay(ov3c, "Installed • Scanning device media", 100, "Open SnapStream", "#059669")

    build_dialogue_overlay(
        ov8,
        "HAPPY & ORGANIZED",
        "#0057FF",
        "Alex",
        "\"Every photo and video on my phone is organized, edited, and saved in seconds — 100% offline!\"",
        rec_card=False,
    )

    m1, m2, m3, m4 = build_phone_mockups()

    f1 = f"{WORK}/slide_f1.png"
    f2 = f"{WORK}/slide_f2.png"
    f3 = f"{WORK}/slide_f3.png"
    f4 = f"{WORK}/slide_f4.png"
    end_png = f"{WORK}/slide_end.png"

    build_feature_slide(
        f1, 1,
        "Instant Offline Scan & Folder Explorer",
        "Automatically discovers all pictures and videos across your device on first launch.",
        [
            "Zero internet, zero cloud sync, 100% private",
            "Browse by Camera, Screenshots, Downloads & Movies",
            "Spot Large Files (>10 MB) and check real free storage",
        ],
        m1, dark=False,
    )
    build_feature_slide(
        f2, 2,
        "Grid & List Views + Multi-Select",
        "Browse like Google's file explorer with one-tap layout switching and batch actions.",
        [
            "Switch between visual Grid cards and detailed List rows",
            "Sort by newest, oldest, file size, or alphabetical name",
            "Long-press to multi-select, favorite, share, or delete",
        ],
        m2, dark=False,
    )
    build_feature_slide(
        f3, 3,
        "Built-In Color Filters & Studio Sliders",
        "Transform any photo right on your device with real-time filter presets and tone controls.",
        [
            "8 curated filters: Electric, Vivid, Noir, Warmth, Cyber, Cinema",
            "Dial in exact filter intensity from 0% to 100%",
            "Fine-tune Brightness, Contrast, and Saturation smoothly",
        ],
        m3, dark=True,
    )
    build_feature_slide(
        f4, 4,
        "Precision Crop, Rotate & Video Trim",
        "Frame shots cleanly and trim video clips locally without exporting to external apps.",
        [
            "Draggable crop handles with Free, 1:1, 4:3, 16:9 & 9:16 ratios",
            "Instant 90-degree rotation and horizontal flip",
            "Interactive video trim slider & local save to Pictures/SnapStream",
        ],
        m4, dark=True,
    )
    build_end_card(end_png)

    # Render individual clips
    clips = [
        (f"{WORK}/c1.mp4", 6.5),
        (f"{WORK}/c2.mp4", 6.5),
        (f"{WORK}/c3a.mp4", 2.5),
        (f"{WORK}/c3b.mp4", 2.5),
        (f"{WORK}/c3c.mp4", 2.5),
        (f"{WORK}/c4.mp4", 6.5),
        (f"{WORK}/c5.mp4", 6.5),
        (f"{WORK}/c6.mp4", 6.5),
        (f"{WORK}/c7.mp4", 6.5),
        (f"{WORK}/c8.mp4", 6.0),
        (f"{WORK}/c9.mp4", 6.0),
    ]

    render_clip_zoom_overlay(SCENE_CHAT, ov1, clips[0][0], clips[0][1], "in")
    render_clip_zoom_overlay(SCENE_CHAT, ov2, clips[1][0], clips[1][1], "pan_right")
    render_clip_zoom_overlay(SCENE_INSTALL, ov3a, clips[2][0], clips[2][1], "in")
    render_clip_zoom_overlay(SCENE_INSTALL, ov3b, clips[3][0], clips[3][1], "in")
    render_clip_zoom_overlay(SCENE_INSTALL, ov3c, clips[4][0], clips[4][1], "in")
    render_static_motion_clip(f1, clips[5][0], clips[5][1], zoom=True)
    render_static_motion_clip(f2, clips[6][0], clips[6][1], zoom=True)
    render_static_motion_clip(f3, clips[7][0], clips[7][1], zoom=True)
    render_static_motion_clip(f4, clips[8][0], clips[8][1], zoom=True)
    render_clip_zoom_overlay(SCENE_ENJOY, ov8, clips[9][0], clips[9][1], "in")
    render_static_motion_clip(end_png, clips[10][0], clips[10][1], zoom=False)

    # Build xfade chain (use 0.05s quick cut inside c3a->c3b->c3c so progress looks continuous, 0.45s smooth fade elsewhere)
    xfade_durs = [0.45, 0.45, 0.05, 0.05, 0.45, 0.45, 0.45, 0.45, 0.45, 0.45]
    total_dur = sum(d for _, d in clips) - sum(xfade_durs)

    wav_path = f"{WORK}/soundtrack.wav"
    synthesize_audio(wav_path, duration_sec=total_dur + 0.5)

    inputs = []
    for path, _ in clips:
        inputs += ["-i", path]
    inputs += ["-i", wav_path]

    fc_parts = []
    cum = clips[0][1]
    prev_label = "[0:v]"
    for idx in range(1, len(clips)):
        xd = xfade_durs[idx - 1]
        offset = cum - xd
        out_label = "[vfinal]" if idx == len(clips) - 1 else f"[vx{idx}]"
        fc_parts.append(
            f"{prev_label}[{idx}:v]xfade=transition=fade:duration={xd:.2f}:offset={offset:.2f}{out_label}"
        )
        cum = offset + clips[idx][1]
        prev_label = out_label

    out_video = "play_store_assets/snapstream_google_ad_1080p.mp4"
    audio_idx = len(clips)
    run([
        "ffmpeg", "-y",
        *inputs,
        "-filter_complex", ";".join(fc_parts),
        "-map", "[vfinal]",
        "-map", f"{audio_idx}:a",
        "-t", f"{total_dur:.2f}",
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-profile:v", "high", "-level", "4.1",
        "-crf", "18", "-preset", "medium", "-movflags", "+faststart",
        "-c:a", "aac", "-b:a", "192k", "-ar", "44100",
        out_video,
    ])
    print("SUCCESS:", out_video, "duration:", total_dur)


if __name__ == "__main__":
    main()
