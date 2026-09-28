"""OpenAI 연결의 응답을 기존 배송 앱의 메시지·문자열 계약에 맞춥니다."""
import os
from pathlib import Path

from dotenv import dotenv_values
from langchain_openai import ChatOpenAI


CONFIG_PATH = Path(__file__).resolve().parents[3] / ".local" / "week07-openai.env"
DEFAULT_MODEL = "gpt-4.1-mini"


class ShippingOpenAI(ChatOpenAI):
    """도구 요청은 보존하고 최종 답변의 텍스트 블록만 문자열로 변환합니다."""

    def _generate(self, *args, **kwargs):
        result = super()._generate(*args, **kwargs)
        for generation in result.generations:
            message = generation.message
            if not message.tool_calls and not isinstance(message.content, str):
                generation.message = message.model_copy(update={"content": str(message.text)})
        return result


def live_model():
    settings = dotenv_values(CONFIG_PATH)

    def value(name, default=""):
        return (settings.get(name) or os.getenv(name) or default).strip()

    if value("AI_AX_LIVE") != "1":
        raise ValueError(".local/week07-openai.env에서 AI_AX_LIVE=1로 설정하세요.")
    api_key = value("OPENAI_API_KEY")
    if not api_key:
        raise ValueError(".local/week07-openai.env 또는 실행 환경에 OPENAI_API_KEY를 입력하세요.")
    return ShippingOpenAI(
        model=value("OPENAI_MODEL", DEFAULT_MODEL), api_key=api_key,
        use_responses_api=True, use_previous_response_id=False,
        output_version="responses/v1", max_tokens=2048,
        timeout=30, max_retries=0, disable_streaming=True,
    )
