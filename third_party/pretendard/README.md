# Pretendard

`app/src/main/res/font/pretendard_*.otf` are subsets of Pretendard 1.3.9 (SIL Open Font License 1.1, `LICENSE.txt`).

The subset keeps ASCII, Latin-1, the symbols the screens use (₩, –, ·, …), Hangul compatibility jamo and the
2,350 KS X 1001 Hangul syllables, so each weight is about 370 KB instead of 1.5 MB. A syllable outside that set
in a server string is drawn with the system Korean font (Android falls back per glyph).

Regenerate from the full OTF files with:

```bash
uv run --no-project --with "fonttools==4.*" python third_party/pretendard/subset_fonts.py <dir with Pretendard-*.otf> app/src/main/res/font
```
