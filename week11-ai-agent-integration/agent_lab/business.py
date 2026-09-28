"""제공 자료의 조회·검색. 모델의 대화 정책과 독립된 업무 경계."""
import json
import re
from pathlib import Path

from langchain_core.documents import Document
from langchain_core.vectorstores import InMemoryVectorStore

DATA = Path(__file__).resolve().parent / "data"


def load_orders():
    # 지정한 공유 예제만 읽습니다. 디렉터리 탐색이나 설정 파일 로더는 없습니다.
    rows = json.loads((DATA / "orders.json").read_text(encoding="utf-8"))
    return {row["order_id"]: row for row in rows}


def load_policies():
    return json.loads((DATA / "policies.json").read_text(encoding="utf-8"))


class OrderLookup:
    def __init__(self, orders):
        self.orders = orders

    def __call__(self, order_id):
        order_id = order_id.strip().upper()
        if not re.fullmatch(r"A-\d{3}", order_id):
            return {"status": "invalid_input", "order_id": order_id}
        facts = self.orders.get(order_id)
        return {"status": "found" if facts else "not_found", "order_id": order_id,
                "facts": dict(facts) if facts else None}


class PolicySearch:
    def __init__(self, embeddings, policies, *, min_score=0.3):
        self.min_score = min_score
        self.store = InMemoryVectorStore(embeddings)
        if policies:
            self.store.add_documents([
                Document(page_content=row["text"], metadata={
                    "source_id": row["source_id"], "title": row["title"]})
                for row in policies
            ])

    def __call__(self, query):
        if not query.strip():
            return {"status": "invalid_input", "matches": []}
        hits = self.store.similarity_search_with_score(query, k=2)
        matches = [{**doc.metadata, "text": doc.page_content, "score": round(float(score), 4)}
                   for doc, score in hits if score >= self.min_score]
        return {"status": "found" if matches else "no_evidence", "matches": matches}
