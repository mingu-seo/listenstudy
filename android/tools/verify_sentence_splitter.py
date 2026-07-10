#!/usr/bin/env python3
"""Lightweight verifier for the Phase 1 Korean sentence splitting rules.
This script mirrors the Kotlin splitter cases so the cron environment can verify behavior even without Android SDK/kotlinc.
"""

def split(text: str):
    result = []
    start = 0
    i = 0

    def looks_like_next_list_item(pos):
        cursor = pos
        while cursor < len(text) and text[cursor].isspace():
            cursor += 1
        return cursor + 1 < len(text) and text[cursor].isdigit() and text[cursor + 1] == "."

    def append_sentence(end):
        nonlocal start
        raw = text[start:end]
        trimmed = raw.strip()
        if trimmed:
            result.append(trimmed)
        start = end

    while i < len(text):
        ch = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else None
        prev = text[i - 1] if i > 0 else None
        should_split = False
        if ch == "\n":
            should_split = nxt == "\n" or looks_like_next_list_item(i + 1)
        elif ch == "." and (prev and prev.isdigit()) and (nxt and nxt.isdigit()):
            should_split = False
        elif ch == ":" and (prev and prev.isdigit()) and (nxt and nxt.isdigit()):
            should_split = False
        elif ch == "." and nxt == ".":
            should_split = False
        elif ch in ".?!。？！":
            should_split = True
        if should_split:
            end = i + 1
            while end < len(text) and text[end] in ".?!。？！":
                end += 1
            while end < len(text) and text[end].isspace() and text[end] != "\n":
                end += 1
            append_sentence(end)
            i = end
        else:
            i += 1
    if start < len(text):
        append_sentence(len(text))
    return result

CASES = [
    ("오늘은 3.14에 대해 공부한다. 다음 문장입니다.", ["오늘은 3.14에 대해 공부한다.", "다음 문장입니다."]),
    ("오전 10:30에 시작한다. 이후 복습한다.", ["오전 10:30에 시작한다.", "이후 복습한다."]),
    ("안녕하세요? 반갑습니다!", ["안녕하세요?", "반갑습니다!"]),
    ("이것은 문장입니다... 다음 문장입니다.", ["이것은 문장입니다...", "다음 문장입니다."]),
]

if __name__ == "__main__":
    for text, expected in CASES:
        actual = split(text)
        assert actual == expected, f"input={text!r}\nexpected={expected}\nactual={actual}"
    print(f"sentence splitter verification passed: {len(CASES)} cases")
