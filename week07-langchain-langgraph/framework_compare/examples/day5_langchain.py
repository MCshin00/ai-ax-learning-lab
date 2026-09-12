"""Day 5: 실제 Gemini로 같은 문의의 번호를 O-100 → O-200 → O-999로 정정합니다."""

if __package__ in (None, ""):
    import _bootstrap

from shipping.live import run


if __name__ == "__main__":
    run("langchain", day=5, default_case="correction")
