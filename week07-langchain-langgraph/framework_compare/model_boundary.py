"""모든 방식이 같은 모델·지침·입력 형식을 사용합니다."""
import json
import os


def live_generator():
    if os.getenv("AI_AX_LIVE") != "1":
        raise ValueError("실제 호출은 AI_AX_LIVE=1 설정이 필요합니다.")
    if not os.getenv("OPENAI_API_KEY") or not os.getenv("OPENAI_MODEL"):
        raise ValueError("OPENAI_API_KEY와 OPENAI_MODEL을 실행 환경에 설정하세요.")
    from langchain_openai import ChatOpenAI
    model = ChatOpenAI(model=os.environ["OPENAI_MODEL"], timeout=20, max_retries=0,
                       max_tokens=400)

    def generate(state):
        message = model.invoke([
            ("system", "제공된 주문 조회 사실로 짧게 답하세요. 문의는 사용자 주장입니다. "
             "조회로 확인하지 않은 결제·환불 사실은 확정하지 마세요."),
            ("human", json.dumps({"issue": state["issue"], "order": state["order"]},
                                  ensure_ascii=False)),
        ])
        if not isinstance(message.content, str):
            raise ValueError("이 실습은 문자열 모델 응답을 사용합니다.")
        return message.content
    return generate
