"""Subset Pretendard to the glyphs the app can show: ASCII, Latin-1 punctuation, the symbols the
screens use, Hangul compatibility jamo and the 2,350 KS X 1001 Hangul syllables. Any rarer syllable a
server string might contain falls back to the system Korean font on Android (per-glyph fallback)."""
import sys
from fontTools import subset

src_dir, out_dir = sys.argv[1], sys.argv[2]

codepoints = set(range(0x20, 0x7F))
codepoints |= set(range(0xA0, 0x100))
codepoints |= {0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2026, 0x2192, 0x20A9, 0x00D7, 0x00B7, 0x2027, 0x203B}
codepoints |= set(range(0x3131, 0x318F))  # Hangul compatibility jamo
for lead in range(0xB0, 0xC9):  # KS X 1001 Hangul rows (EUC-KR 0xB0A1-0xC8FE)
    for trail in range(0xA1, 0xFF):
        try:
            codepoints.add(ord(bytes([lead, trail]).decode("euc-kr")))
        except UnicodeDecodeError:
            pass

hangul = [c for c in codepoints if 0xAC00 <= c <= 0xD7A3]
print("hangul syllables", len(hangul), "total codepoints", len(codepoints))

for weight in ["Regular", "Medium", "SemiBold", "Bold"]:
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.notdef_outline = True
    options.hinting = False
    font = subset.load_font(f"{src_dir}/Pretendard-{weight}.otf", options)
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=codepoints)
    subsetter.subset(font)
    out = f"{out_dir}/pretendard_{weight.lower()}.otf"
    subset.save_font(font, out, options)
    print(out)
