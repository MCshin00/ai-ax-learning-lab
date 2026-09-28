"""실제 프레임워크에 정해진 응답을 넣어 보완 경로를 읽습니다."""
from consultation import Consultation, PolicyEvidence
from business import OrderLookup, PolicySearch, load_orders, load_policies
from offline import ScriptedModel, TopicEmbeddings, call

DEMO_REQUEST = "A-102를 취소할 수 있나요? 신청 경로도 알려 주세요."


def demo_consultation(**options):
    model = ScriptedModel(responses=[
        call("Intake", {"intent": "cancel", "order_ids": ["A-102"]}, "intake"),
        call("search_policy", {"query": "출고 전 취소"}, "search"),
        call("ConsultationDraft", {"items": [{"order_id": "A-102", "answer": "출고 전 취소 조건을 확인했습니다.",
                                               "source_ids": ["POL-CANCEL"]}]}, "draft"),
        call("ConsultationDraft", {"items": [{"order_id": "A-102", "answer": "배송 준비·출고 전입니다. 주문 상세에서 취소 신청을 선택할 수 있습니다.",
                                               "source_ids": ["POL-CANCEL"]}]}, "revised"),
    ])
    evidence = PolicyEvidence(PolicySearch(TopicEmbeddings(), load_policies()), load_policies())
    return Consultation(model, OrderLookup(load_orders()), evidence, **options)


def build_live():
    from model_boundary import live_dependencies
    model, embeddings = live_dependencies()
    return Consultation(model, OrderLookup(load_orders()),
                        PolicyEvidence(PolicySearch(embeddings, load_policies()), load_policies()))


class ReplayApp:
    """API 모양을 살피는 단일 입력 대역. 대화 학습은 실제 연결에서 합니다."""
    def reply(self, conversation_id, request):
        if request != DEMO_REQUEST:
            raise ValueError("고정 응답 모드에서는 화면의 제공 문의를 사용하세요.")
        return {**demo_consultation().reply(conversation_id, request), "mode": "SCRIPTED_DEMO"}
