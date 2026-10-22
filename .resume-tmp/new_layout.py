import sys, os
sys.path.insert(0, r"D:\work\java\yijia-ai-chat-assistant\.resume-tmp\lib")
from pypdf import PdfReader
SRC = r"D:\work\java\yijia-ai-chat-assistant\resume\李一甲-Java后端开发工程师.pdf"
OUT = r"D:\work\java\yijia-ai-chat-assistant\.resume-tmp\new_layout.txt"
r = PdfReader(SRC)
lines = [f"pages={len(r.pages)}"]
for pno, page in enumerate(r.pages, 1):
    lines.append(f"== PAGE {pno}")
    frags = []
    def visitor(text, cm, tm, fd, fs):
        if not text or not text.strip(): return
        frags.append({"t":text,"x":round(tm[4],1),"y":round(tm[5],1),"s":round(fs*(abs(cm[0]) if cm and cm[0] else 1),1)})
    page.extract_text(visitor_text=visitor)
    groups={}
    for f in frags: groups.setdefault(round(f["y"]/3.0),[]).append(f)
    for k in sorted(groups.keys(), reverse=True):
        row=sorted(groups[k], key=lambda f:f["x"])
        lines.append(f"y={row[0]['y']:6.1f} x={row[0]['x']:.0f}-{row[-1]['x']:.0f} sz={sorted({r2['s'] for r2 in row})} | " + "".join(r2["t"] for r2 in row))
open(OUT,"w",encoding="utf-8").write("\n".join(lines))
print("ok")
