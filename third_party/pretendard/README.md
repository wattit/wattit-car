# Pretendard

`app/src/main/res/font/pretendard_*.otf` are subsets of Pretendard 1.3.9 (SIL Open Font License 1.1, `LICENSE.txt`).

Pretendard(OFL 1.1)에서 KS X 1001 subset을 만들었고, "Pretendard"가 예약 글꼴 이름(Reserved Font Name)이라 글꼴 이름을 Wattit Sans(PostScript `WattitSans-Regular` 등)로 바꿨습니다. 저작권과 상표 표기(name ID 0, 7, 9)는 원본 그대로 둡니다. 파일 이름의 `pretendard_`는 앱 안의 리소스 이름일 뿐 글꼴 이름이 아닙니다.

The subset keeps ASCII, Latin-1, the symbols the screens use (₩, –, ·, …), Hangul compatibility jamo and the
2,350 KS X 1001 Hangul syllables, so each weight is about 370 KB instead of 1.5 MB. A syllable outside that set
in a server string is drawn with the system Korean font (Android falls back per glyph).

Regenerate from the full OTF files with:

```bash
uv run --no-project --with "fonttools==4.*" python third_party/pretendard/subset_fonts.py <dir with Pretendard-*.otf> app/src/main/res/font
```
