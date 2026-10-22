import sys
sys.path.insert(0, r"D:\work\java\yijia-ai-chat-assistant\.resume-tmp\lib")
from pypdf import PdfReader

SRC = r"C:\Users\lenovo\Desktop\moba\AI应用开发工程师.pdf"
OUT = r"D:\work\java\yijia-ai-chat-assistant\.resume-tmp\resume_text.txt"

reader = PdfReader(SRC)
chunks = []
chunks.append(f"pages: {len(reader.pages)}")
for i, page in enumerate(reader.pages, 1):
    chunks.append(f"\n########## PAGE {i} ##########")
    try:
        txt = page.extract_text(extraction_mode="layout")
    except Exception as e:
        chunks.append(f"[layout failed: {e}]")
        txt = page.extract_text()
    chunks.append(txt if txt else "<EMPTY>")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(chunks))
print("wrote", OUT, "chars:", sum(len(c) for c in chunks))
