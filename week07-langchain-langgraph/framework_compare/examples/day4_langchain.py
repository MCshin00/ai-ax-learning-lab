"""VS Code 실행: 기존 LangChain 에이전트를 Gemini와 연결합니다."""

if __package__ in (None, ""):
    import _bootstrap

from shipping.live import run


if __name__ == "__main__":
    run("langchain")