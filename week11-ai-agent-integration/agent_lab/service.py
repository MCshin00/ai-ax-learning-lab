"""로컬 API와 작은 입력 화면. 같은 Consultation.reply 계약을 호출합니다."""
import argparse
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from consultation_demo import DEMO_REQUEST, ReplayApp, build_live

PAGE = '''<!doctype html><html lang="ko"><meta charset="utf-8"><title>주문 상담</title>
<h1>주문 상담</h1><p>답변의 조건과 출처를 함께 확인하세요. MODE</p>
<form id="form"><p><label>대화 ID <input id="conversation" value="customer-1" required></label></p>
<p><label>문의 <textarea id="request" required>REQUEST</textarea></label></p>
<button>상담하기</button></form><pre id="output"></pre>
<script>document.getElementById('form').onsubmit=async e=>{e.preventDefault();
const out=document.getElementById('output');out.textContent='처리 중';
try{const response=await fetch('/consultations',{method:'POST',headers:{'Content-Type':'application/json'},
body:JSON.stringify({conversation_id:document.getElementById('conversation').value,
request:document.getElementById('request').value})});
out.textContent=JSON.stringify(await response.json(),null,2);}catch(e){out.textContent='연결에 실패했습니다.';}};</script></html>'''


def dispatch(app, payload):
    if not isinstance(payload, dict) or set(payload) != {"conversation_id", "request"}:
        return 400, {"error": "대화 ID와 문의를 전달하세요."}
    try:
        result = app.reply(payload["conversation_id"], payload["request"])
    except ValueError:
        return 400, {"error": "입력 형식·길이 또는 실행 모드의 제공 문의를 확인하세요."}
    code = 502 if result["status"] in ("unavailable", "invalid_output") else 200
    return code, result


def make_server(app, *, port=0, live=False):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def send(self, code, payload, content_type="application/json; charset=utf-8"):
            body = payload.encode("utf-8") if isinstance(payload, str) else json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.path != "/":
                return self.send(404, {"error": "없는 경로입니다."})
            self.send(200, PAGE.replace("REQUEST", DEMO_REQUEST).replace("MODE",
                "실제 모델 연결" if live else "고정 응답 예시: 제공 문의로 API 연결을 확인합니다."), "text/html; charset=utf-8")

        def do_POST(self):
            if self.path != "/consultations":
                return self.send(404, {"error": "없는 경로입니다."})
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 20000:
                    return self.send(400, {"error": "요청 길이를 확인하세요."})
                self.connection.settimeout(10)
                payload = json.loads(self.rfile.read(length))
            except (ValueError, UnicodeError, TimeoutError):
                return self.send(400, {"error": "JSON 요청을 확인하세요."})
            code, result = dispatch(app, payload)
            self.send(code, result)
    # 학습용 단일 요청 서버: 메모리 대화의 동시 갱신을 직렬화합니다.
    return HTTPServer(("127.0.0.1", port), Handler)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--port", type=int, default=0)
    args = parser.parse_args()
    app = build_live() if args.live else ReplayApp()
    with make_server(app, port=args.port, live=args.live) as server:
        print(f"브라우저에서 http://127.0.0.1:{server.server_port} 를 여세요.", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
