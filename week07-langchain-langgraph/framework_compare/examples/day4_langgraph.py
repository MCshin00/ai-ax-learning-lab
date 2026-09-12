"""VS Code 실행: 기존 LangGraph 업무 흐름을 Gemini와 연결합니다."""

if __package__ in (None, ""):
    import _bootstrap

from shipping.live import run


if __name__ == "__main__":
    run("langgraph")