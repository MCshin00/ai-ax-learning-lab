"""Python만으로 역할 배치를 비교하는 참고 구현. 기본 학습 앱은 Java Consultation입니다."""
from copy import deepcopy
import json
import re
from langchain.agents import create_agent
from langchain.agents.structured_output import ToolStrategy, StructuredOutputError
from langchain.tools import tool
from openai import APIError
from ai_contracts import (Intake, DraftItem, ConsultationDraft, BudgetExceeded, Budget,
                          SharedBudget, INTAKE_POLICY, DRAFT_POLICY, PolicyEvidence)


class Consultation:
    def __init__(self, model, lookup, evidence, *, max_model_calls=6, repair=True,
                 initial_context="excerpt"):
        if initial_context not in ("excerpt", "full"):
            raise ValueError("문맥 정책을 확인하세요.")
        self.model, self.lookup, self.evidence = model, lookup, evidence
        self.max_model_calls, self.repair = max_model_calls, repair
        self.initial_context = initial_context
        self.sessions = {}

    def structured(self, schema, prompt, payload, budget, tools=()):
        agent = create_agent(self.model, tools=list(tools), system_prompt=prompt,
                             response_format=ToolStrategy(schema, handle_errors=False),
                             middleware=[SharedBudget(budget)])
        state = agent.invoke({"messages": [{"role": "user", "content":
                              json.dumps(payload, ensure_ascii=False)}]},
                             {"recursion_limit": 30})
        return state["structured_response"]

    def reply(self, conversation_id, request):
        if not isinstance(conversation_id, str) or not conversation_id.strip() or not isinstance(request, str) or not request.strip():
            raise ValueError("대화 식별자와 문의가 필요합니다.")
        if len(conversation_id) > 100 or len(request) > 4000:
            raise ValueError("식별자 또는 문의가 너무 깁니다.")
        previous = deepcopy(self.sessions.get(conversation_id, {}))
        explicit = list(dict.fromkeys(re.findall(r"(?<![A-Z0-9_-])A-\d{3}(?![A-Z0-9_-])", request.upper())))
        if previous.get("intake") and explicit and set(explicit) != set(previous["intake"]["order_ids"]):
            previous["intake"]["received_days"] = {}
            previous["intake"]["used"] = {}
        budget = Budget(self.max_model_calls)
        result = {"conversation_id": conversation_id, "status": "needs_review", "items": [],
                  "trace": [], "revisions": 0, "model_calls": 0}
        try:
            intake = self.structured(Intake, INTAKE_POLICY,
                {"previous": previous.get("intake"), "request": request}, budget)
            allowed = explicit or previous.get("intake", {}).get("order_ids", [])
            # 현재 발언의 번호가 있으면 조회 대상을 코드에서 확정합니다.
            if explicit:
                intake.order_ids = explicit
            elif any(oid not in allowed for oid in intake.order_ids):
                raise ValueError("입력에 없는 주문 번호입니다.")
            intake.order_ids = list(dict.fromkeys(intake.order_ids))
            if len(intake.order_ids) > 5:
                raise ValueError("주문은 한 번에 5개까지 확인합니다.")
            intake.received_days = {k: v for k, v in intake.received_days.items()
                                    if k in intake.order_ids and v >= 0}
            intake.used = {k: v for k, v in intake.used.items() if k in intake.order_ids}
            # 사용자 보충 사실도 주문별로 보관합니다.
            if explicit and set(explicit) != set(previous.get("intake", {}).get("order_ids", [])):
                intake.received_days = {k: v for k, v in intake.received_days.items() if k in explicit}
                intake.used = {k: v for k, v in intake.used.items() if k in explicit}
            self.sessions[conversation_id] = {"intake": intake.model_dump()}
            result["intake"] = intake.model_dump()
            if intake.intent == "unsupported":
                result.update(status="needs_review", question="제공 자료로 판단할 수 없습니다. 담당자에게 확인하세요.")
                return result
            if not intake.order_ids:
                result.update(status="needs_input", question="주문 번호를 알려 주세요.")
                return result
            for oid in intake.order_ids:
                try:
                    fact = self.lookup(oid)
                except (TimeoutError, ConnectionError):
                    fact = {"status": "unavailable", "order_id": oid, "facts": None}
                result["trace"].append({"stage": "lookup", "order_id": oid, "result": fact})
                result["items"].append({"order_id": oid, "lookup_status": fact["status"],
                    "facts": fact.get("facts"), "status": "pending", "answer": "", "sources": [],
                    "question": "", "next_action": "review"})
            evidence = {}
            @tool
            def search_policy(query: str) -> dict:
                """문의와 관련된 정책 발췌와 출처를 검색합니다."""
                try:
                    found = self.evidence(query)
                except (TimeoutError, ConnectionError, APIError):
                    found = {"status": "unavailable", "matches": []}
                result["trace"].append({"stage": "search", "query": query, "result": found})
                for hit in found.get("matches", []):
                    if self.initial_context == "full":
                        hit = self.evidence.expand(hit["source_id"]) or hit
                    if evidence.get(hit["source_id"], {}).get("scope") != "full":
                        evidence[hit["source_id"]] = hit
                return {**found, "matches": [evidence[h["source_id"]] for h in found.get("matches", [])]}

            payload = {"request": request, "intake": intake.model_dump(), "orders": result["items"]}
            for attempt in range(2 if self.repair else 1):
                draft = self.structured(ConsultationDraft, DRAFT_POLICY,
                    {**payload, "evidence": list(evidence.values()), "feedback": result.get("feedback", [])},
                    budget, [search_policy])
                feedback = self.check(draft, intake, result["items"], evidence)
                result["trace"].append({"stage": "check", "attempt": attempt,
                                        "draft": draft.model_dump(), "feedback": feedback})
                result["feedback"] = feedback
                self.finish(draft, intake, result, evidence)
                if not feedback or attempt == 1 or not self.repair:
                    break
                # 근거 자체가 없으면 문맥 확장으로 해결할 수 없습니다.
                if not evidence:
                    break
                # 한 번만 문맥을 보완하고 같은 원래 문의로 다시 생성·검사합니다.
                for sid in list(evidence):
                    full = self.evidence.expand(sid)
                    if full:
                        evidence[sid] = full
                result["revisions"] = 1
                result["trace"].append({"stage": "expand", "sources": list(evidence.values())})
        except BudgetExceeded:
            result["status"] = "limit_reached"
        except (TimeoutError, ConnectionError, APIError):
            result["status"] = "unavailable"
        except (StructuredOutputError, ValueError, KeyError):
            result["status"] = "invalid_output"
        finally:
            result["model_calls"] = budget.calls
            for item in result["items"]:
                if item["status"] == "pending":
                    item.update(status="needs_review", question="처리를 완료하지 못했습니다. 확인된 사실과 남은 처리를 검토하세요.")
            result["actions"] = [{"order_id": i["order_id"], "action": i["next_action"]} for i in result["items"]]
        return result

    @staticmethod
    def required_source(intake, item):
        if intake.intent == "status" or item["lookup_status"] != "found":
            return None
        return "POL-CANCEL" if intake.intent == "cancel" and not item["facts"]["shipped"] else "POL-RETURN"

    def check(self, draft, intake, items, evidence):
        feedback = []
        ids = [d.order_id for d in draft.items]
        if sorted(ids) != sorted(intake.order_ids):
            feedback.append("요청한 주문마다 결과 하나를 반환하세요.")
        for item in items:
            row = next((d for d in draft.items if d.order_id == item["order_id"]), None)
            sid = self.required_source(intake, item)
            if row and any(s not in evidence for s in row.source_ids):
                feedback.append(f"{item['order_id']}: 검색되지 않은 출처입니다.")
            if sid and (not row or sid not in row.source_ids or evidence.get(sid, {}).get("scope") != "full"):
                feedback.append(f"{item['order_id']}: {sid}의 조건과 신청 경로를 포함한 전체 근거가 필요합니다.")
        return feedback

    def finish(self, draft, intake, result, evidence):
        for item in result["items"]:
            item.update(answer="", sources=[], question="", next_action="review")
            matches = [d for d in draft.items if d.order_id == item["order_id"]]
            if item["lookup_status"] != "found":
                item.update(status=item["lookup_status"], question="주문 번호 또는 조회 서비스 상태를 확인하세요.")
                continue
            if len(matches) != 1:
                item.update(status="needs_review", question="주문별 결과가 누락되거나 중복됐습니다.")
                continue
            row = matches[0]
            sid = self.required_source(intake, item)
            if any(s not in evidence for s in row.source_ids) or (sid and
                (sid not in row.source_ids or evidence.get(sid, {}).get("scope") != "full")):
                item.update(status="needs_review", question="정책 조건과 신청 경로의 근거를 확인해야 합니다.")
                continue
            if sid == "POL-RETURN" and item["facts"]["status"] == "배송 완료" and (
                    item["order_id"] not in intake.received_days or item["order_id"] not in intake.used):
                item.update(status="needs_input", next_action="ask", question="수령 후 며칠이 지났고 상품을 사용했나요?")
                continue
            item.update(status="ready", next_action="display", answer=row.answer,
                        sources=[evidence[s] for s in dict.fromkeys(row.source_ids)])
        statuses = {i["status"] for i in result["items"]}
        result["status"] = "ready" if statuses == {"ready"} else (
            "partial" if "ready" in statuses else "needs_input" if "needs_input" in statuses else "needs_review")
