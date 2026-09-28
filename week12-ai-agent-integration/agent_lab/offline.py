"""지정된 응답을 재생하는 검증용 경계. 자연어 이해와 실제 검색 품질은 검사하지 않습니다."""
from langchain_core.embeddings import Embeddings
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from pydantic import Field


class TopicEmbeddings(Embeddings):
    def embed_query(self, text):
        return [float(any(word in text for word in group))
                for group in [("취소", "출고"), ("반품", "수령"), ("도착", "배송"), ("보증",)]] + [0.01]

    def embed_documents(self, texts):
        return [self.embed_query(text) for text in texts]


class ScriptedModel(BaseChatModel):
    responses: list = Field(default_factory=list)
    seen: list = Field(default_factory=list)
    calls: int = 0

    @property
    def _llm_type(self):
        return "scripted-integration-check"

    def bind_tools(self, tools, **kwargs):
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        self.seen.append(list(messages))
        self.calls += 1
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return ChatResult(generations=[ChatGeneration(message=response)])


def call(name, args, identifier):
    return AIMessage(content="", tool_calls=[{"name": name, "args": args, "id": identifier, "type": "tool_call"}])


def demo_model():
    return ScriptedModel(responses=[
        call("lookup_order", {"order_id": "A-102"}, "order-1"),
        call("search_policy", {"query": "출고 전 취소"}, "policy-1"),
        AIMessage(content="A-102는 배송 준비 상태로 취소를 신청할 수 있습니다. 실제 취소는 실행하지 않았습니다. [POL-CANCEL]"),
    ])
