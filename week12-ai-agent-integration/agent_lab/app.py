"""IDE 실행 진입점. 기본값은 고정 응답 재생이며 --live에서만 실제 연결합니다."""
import argparse
import json

from consultation_demo import DEMO_REQUEST, demo_consultation, build_live


def show(result):
    print(json.dumps(result, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description="주문·정책 상담 참고 구현")
    parser.add_argument("--live", action="store_true", help="학습자가 IDE에서 실제 모델·임베딩 연결")
    args = parser.parse_args()
    if args.live:
        try:
            agent = build_live()
        except Exception:
            print("모델·정책 검색 준비에 실패했습니다. IDE의 연결 설정과 네트워크를 확인하세요.")
            return
    else:
        agent = demo_consultation()
    if not args.live:
        print("고정 응답 재생: 실제 LLM의 선택·응답 품질은 확인하지 않습니다.")
        show(agent.reply("demo", DEMO_REQUEST))
        return
    print("대화 식별자가 같으면 이어갑니다. 식별자를 비워 두면 종료합니다.")
    while conversation_id := input("대화 식별자: ").strip():
        request = input("문의: ").strip()
        if request:
            show(agent.reply(conversation_id, request))


if __name__ == "__main__":
    main()
