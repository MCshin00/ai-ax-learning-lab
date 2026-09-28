"""Java 앱에 구조화 접수와 LangChain 검색 에이전트를 제공하는 로컬 연결."""
import argparse
import json
from http.server import BaseHTTPRequestHandler, HTTPServer

from langchain.agents import create_agent
from langchain.agents.structured_output import ToolStrategy, StructuredOutputError
from langchain.tools import tool
from openai import APIError

from ai_contracts import (Intake, ConsultationDraft, Budget, BudgetExceeded,
                         SharedBudget, INTAKE_POLICY, DRAFT_POLICY, PolicyEvidence)
from business import PolicySearch, load_policies


class ModelWorker:
    def __init__(self, model_factory, evidence, *, mode):
        self.model_factory, self.evidence, self.mode = model_factory, evidence, mode

    def execute(self, request):
        if not isinstance(request, dict) or request.get('operation') not in ('intake', 'draft'):
            raise ValueError('operation은 intake 또는 draft입니다.')
        remaining = request.get('remaining_calls')
        if type(remaining) is not int or not 0 <= remaining <= 6:
            raise ValueError('남은 호출 횟수는 0~6 정수입니다.')
        payload = request.get('payload')
        if not isinstance(payload, dict):
            raise ValueError('payload 객체가 필요합니다.')
        operation = request['operation']
        budget = Budget(remaining)
        evidence = {hit['source_id']: dict(hit) for hit in payload.get('evidence', [])}
        trace = []

        @tool
        def search_policy(query: str) -> dict:
            """문의에 필요한 정책 본문과 출처를 검색합니다."""
            try:
                found = self.evidence(query)
            except (TimeoutError, ConnectionError, APIError):
                found = {'status': 'unavailable', 'matches': []}
            trace.append({'stage': 'search', 'query': query, 'result': found})
            for hit in found.get('matches', []):
                if payload.get('initial_context') == 'full':
                    hit = self.evidence.expand(hit['source_id']) or hit
                if evidence.get(hit['source_id'], {}).get('scope') != 'full':
                    evidence[hit['source_id']] = hit
            return {**found, 'matches': [evidence[hit['source_id']] for hit in found.get('matches', [])]}

        result = {'status': 'ok', 'value': None, 'mode': self.mode}
        try:
            model = self.model_factory(operation, payload)
            agent = create_agent(model, tools=[] if operation == 'intake' else [search_policy],
                system_prompt=INTAKE_POLICY if operation == 'intake' else DRAFT_POLICY,
                response_format=ToolStrategy(Intake if operation == 'intake' else ConsultationDraft,
                                             handle_errors=False),
                middleware=[SharedBudget(budget)])
            state = agent.invoke({'messages': [{'role': 'user', 'content': json.dumps(payload, ensure_ascii=False)}]},
                                 {'recursion_limit': 30})
            result['value'] = state['structured_response'].model_dump()
        except BudgetExceeded:
            result['status'] = 'limit_reached'
        except (TimeoutError, ConnectionError, APIError):
            result['status'] = 'unavailable'
        except (StructuredOutputError, ValueError, KeyError):
            result['status'] = 'invalid_output'
        return {**result, 'model_calls': budget.calls, 'sources': list(evidence.values()), 'trace': trace}


def scripted_model(operation, payload):
    """정해진 입력에 준비된 응답을 연결합니다. 자연어 모델을 대신 평가하지 않습니다."""
    from offline import ScriptedModel, call
    fixtures = {
        'A-102 상태가 궁금해요.': {'intent': 'status', 'order_ids': ['A-102']},
        'A-103 상태가 궁금해요.': {'intent': 'status', 'order_ids': ['A-103']},
        'A-102를 취소할 수 있나요? 신청 경로도 알려 주세요.': {'intent': 'cancel', 'order_ids': ['A-102']},
        '반품하고 싶어요': {'intent': 'return', 'order_ids': []},
        'A-104예요': {'intent': 'return', 'order_ids': ['A-104']},
        '받은 지 이틀이고 사용하지 않았어요': {'intent': 'return', 'order_ids': ['A-104'],
                                         'received_days': {'A-104': 2}, 'used': {'A-104': False}},
        'A-103으로 정정할게요': {'intent': 'cancel', 'order_ids': ['A-103']},
        'A-999, A-102, A-103의 상태를 각각 알려 주세요.': {'intent': 'status', 'order_ids': ['A-999', 'A-102', 'A-103']},
    }
    if operation == 'intake':
        value = fixtures.get(payload.get('request'))
        if value is None:
            raise ValueError('고정 응답 모드의 제공 입력을 사용하세요.')
        return ScriptedModel(responses=[call('Intake', value, 'intake')])
    responses, items = [], []
    intent = payload['intake']['intent']
    if intent != 'status' and not payload.get('evidence'):
        responses.append(call('search_policy', {'query': '취소 출고' if intent == 'cancel' else '반품 수령'}, 'search'))
    for order in payload['orders']:
        facts = order.get('facts') or {}
        source = [] if intent == 'status' or not facts else [
            'POL-CANCEL' if intent == 'cancel' and not facts['shipped'] else 'POL-RETURN']
        answer = facts.get('status', '주문을 확인하지 못했습니다.')
        if source:
            answer += '. 정책 조건을 확인하고 주문 상세에서 신청 경로를 확인하세요.'
        items.append({'order_id': order['order_id'], 'answer': answer, 'source_ids': source})
    responses.append(call('ConsultationDraft', {'items': items}, 'draft'))
    return ScriptedModel(responses=responses)


def build_worker(live=False):
    if live:
        from model_boundary import live_dependencies
        model, embeddings = live_dependencies()
        factory = lambda operation, payload: model
    else:
        from offline import TopicEmbeddings
        embeddings, factory = TopicEmbeddings(), scripted_model
    return ModelWorker(factory, PolicyEvidence(PolicySearch(embeddings, load_policies()), load_policies()),
                       mode='LIVE' if live else 'SCRIPTED_DEMO')


def make_server(worker, port=0):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_POST(self):
            code = 200
            try:
                if self.path != '/model':
                    raise ValueError('모델 요청은 /model로 전달합니다.')
                size = int(self.headers.get('Content-Length', '0'))
                if not 0 < size <= 100_000:
                    raise ValueError('요청 길이를 확인하세요.')
                self.connection.settimeout(10)
                result = worker.execute(json.loads(self.rfile.read(size)))
            except (ValueError, KeyError, TypeError, TimeoutError):
                code, result = 400, {'error': '요청의 작업·항목·타입을 확인하세요.'}
            body = json.dumps(result, ensure_ascii=False).encode('utf-8')
            self.send_response(code)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
    return HTTPServer(('127.0.0.1', port), Handler)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--live', action='store_true')
    parser.add_argument('--port', type=int, default=0)
    args = parser.parse_args()
    try:
        worker = build_worker(args.live)
    except Exception:
        print('모델·검색 준비에 실패했습니다. IDE의 연결 설정과 네트워크를 확인하세요.')
        return
    with make_server(worker, args.port) as server:
        print(f'{worker.mode}: http://127.0.0.1:{server.server_port}', flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == '__main__':
    main()
