import sys, json
sys.path.insert(0, r"D:\work\java\yijia-ai-chat-assistant\.resume-tmp\lib")
from pypdf import PdfReader

SRC = r"C:\Users\lenovo\Desktop\moba\AI应用开发工程师.pdf"
OUT = r"D:\work\java\yijia-ai-chat-assistant\.resume-tmp\resume_layout.txt"

reader = PdfReader(SRC)
lines = []

for pno, page in enumerate(reader.pages, 1):
    mb = page.mediabox
    lines.append(f"== PAGE {pno} mediabox w={float(mb.width):.1f} h={float(mb.height):.1f}")
    frags = []

    def visitor(text, cm, tm, font_dict, font_size):
        if not text or not text.strip():
            return
        size = font_size
        name = ""
        if font_dict:
            name = str(font_dict.get("/BaseFont", ""))
        # effective size considering cm scale
        eff = size * (abs(cm[0]) if cm and cm[0] else 1)
        frags.append({
            "t": text,
            "x": round(tm[4], 1),
            "y": round(tm[5], 1),
            "size": round(eff, 1),
            "font": name,
        })

    page.extract_text(visitor_text=visitor)

    # group fragments into lines by rounded y
    groups = {}
    for f in frags:
        key = round(f["y"] / 3.0)
        groups.setdefault(key, []).append(f)

    for key in sorted(groups.keys(), reverse=True):
        row = sorted(groups[key], key=lambda f: f["x"])
        text = "".join(r["t"] for r in row)
        sizes = sorted({r["size"] for r in row})
        fonts = sorted({r["font"] for r in row})
        xs = f"{row[0]['x']:.0f}-{row[-1]['x']:.0f}"
        lines.append(f"y={row[0]['y']:6.1f} x={xs:>9} sz={sizes} font={fonts} | {text}")

with open(OUT, "w", encoding="utf-8") as fh:
    fh.write("\n".join(lines))
print("wrote", OUT)
