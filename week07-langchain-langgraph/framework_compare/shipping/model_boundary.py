"""Day 4의 Gemini 연결과 기존 배송 코드의 문자열 응답 계약을 연결합니다."""
import os
import time
from threading import Lock
from pathlib import Path

from dotenv import dotenv_values
from langchain_google_genai import ChatGoogleGenerativeAI


CONFIG_PATH = Path(__file__).resolve().parents[3] / ".local" / "week07-gemini.env"


MIN_REQUEST_INTERVAL = 13.0
_REQUEST_LOCK = Lock()
_LAST_REQUEST_START = None


def _wait_for_request_slot():
    """동기 모델 호출의 시작 간격을 지키며 첫 요청도 한 간격 기다립니다."""
    global _LAST_REQUEST_START
    with _REQUEST_LOCK:
        now = time.monotonic()
        delay = (MIN_REQUEST_INTERVAL if _LAST_REQUEST_START is None
                 else max(0.0, MIN_REQUEST_INTERVAL - (now - _LAST_REQUEST_START)))
        if delay > 0:
            time.sleep(delay)
        # 실패한 요청도 전송을 시도했으므로 다음 요청의 간격에 포함합니다.
        _LAST_REQUEST_START = time.monotonic()


class ShippingGemini(ChatGoogleGenerativeAI):
    """동기 실행에서 최종 텍스트를 변환하고 도구 요청 메시지는 그대로 둡니다."""

    def _generate(self, *args, **kwargs):
        _wait_for_request_slot()
        result = super()._generate(*args, **kwargs)
        for generation in result.generations:
            message = generation.message
            if not message.tool_calls and not isinstance(message.content, str):
                # Gemini의 콘텐츠 블록에서 공개 텍스트를 추출합니다.
                generation.message = message.model_copy(update={"content": str(message.text)})
        return result


def live_model():
    settings = dotenv_values(CONFIG_PATH)
    enabled = settings.get("AI_AX_LIVE") or os.getenv("AI_AX_LIVE")
    api_key = settings.get("GEMINI_API_KEY") or os.getenv("GEMINI_API_KEY")
    model_name = settings.get("GEMINI_MODEL") or os.getenv("GEMINI_MODEL")
    if enabled != "1":
        raise ValueError(".local/week07-gemini.env에서 AI_AX_LIVE=1로 설정하세요.")
    if not api_key or not api_key.strip():
        raise ValueError(".local/week07-gemini.env의 GEMINI_API_KEY를 입력하세요.")
    if not model_name or not model_name.strip():
        raise ValueError(".local/week07-gemini.env의 GEMINI_MODEL을 입력하세요.")
    options = {}
    # Gemini 3 계열은 서버의 샘플링 기본값을 사용합니다.
    # 특히 3.5 Flash-Lite는 사용자 지정 temperature를 지원하지 않습니다.
    if not model_name.removeprefix("models/").startswith("gemini-3"):
        options["temperature"] = 1.0
    if model_name == "gemini-2.5-flash":
        options["thinking_budget"] = 0
    return ShippingGemini(
        model=model_name, api_key=api_key, vertexai=False,
        max_tokens=2048, timeout=30, max_retries=0,
        disable_streaming=True, **options
    )