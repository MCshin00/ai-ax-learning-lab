# 7주차 — LangChain·LangGraph의 실행 책임과 상태 관리

배송 문의의 코드와 실제 결과로 값 전달, 도구 실행, 문의 보관과 재개를 비교한다. Day 1~3은 고정 답변·모의 모델, Day 4~5는 실제 Gemini 응답을 근거로 한다.

## Day 1 — 값 전달과 상태 갱신

### 고정 조회의 분기

[examples/run.py](framework_compare/examples/run.py)의 세 방식은 [comparisons/business.py](framework_compare/comparisons/business.py)의 같은 주문 자료·업무 함수·고정 생성기를 사용한다. [comparisons/flows.py](framework_compare/comparisons/flows.py)가 이 부품을 직접 함수 호출, LangChain Runnable, LangGraph 노드·간선으로 연결한다.

| 주문 번호 | 세 방식에서 확인한 경로 | 결과 |
|---|---|---|
| `O-100` | `prepare → lookup → draft` | `ANSWERED`, 배송 준비 안내 |
| 빈 번호 | `prepare → ask` | `NEEDS_INPUT`, 주문 번호 질문 |
| `O-999` | `prepare → lookup → unavailable` | `NOT_FOUND`, 주문 부재 안내 |

`prepare`는 번호의 공백을 정리하고 조회 결과와 답변을 초기화한다. 정상 입력의 상태 변화를 코드로 따라가면 다음과 같다.

| 위치 | 주문 번호·문의의 역할 | 조회 사실·처리 결과 |
|---|---|---|
| 입력 | `O-100`, `배송 상태를 알려 주세요.` | 아직 조회 사실 없음 |
| `prepare` 뒤 | 입력 번호와 문의 유지 | `order=None`, `answer=""`, `status=RECEIVED` |
| `lookup` 뒤 | 번호를 실제 조회의 키로 사용 | `order={order_id: O-100, shipping: 배송 준비}`, `status=FOUND` |
| `draft` 뒤 | 조회된 `order`를 생성 함수에 전달 | 배송 준비 안내, `status=ANSWERED` |

배송 상태는 문의에서 나온 값이 아니라 `ORDERS`를 조회한 결과다. `draft`는 이 사실을 받아 문장을 만들고, 비어 있지 않은 답변이면 `ANSWERED`를 반환한다.

LangGraph 연결에서 빈 번호는 `after_prepare`의 조건에 따라 `ask`로 이동한다. 조회할 대상을 정할 수 없어 조회를 건너뛰고 번호 질문을 반환한다. `O-999`는 번호가 있으므로 조회하지만, 자료가 없어 `NOT_FOUND`가 되고 `after_lookup`이 `unavailable`로 보낸다. 이 두 경로는 생성 함수를 호출하지 않아 `generation=NONE`이며, 정상 조회만 `FIXED_OFFLINE`이다. 질문을 반환하는 것과 원래 문의를 보관해 다음 호출로 잇는 것은 별도의 기능이다.

Direct는 이 조건을 함수 본문의 `if`로, Runnable은 `RunnableBranch`로, LangGraph는 조건 간선으로 연결한다. 모두 같은 업무 조건을 적용하므로 같은 결과가 나왔다. 짧고 정해진 절차는 Direct의 함수와 조건문으로 충분히 표현할 수 있다.

### 부분 반환과 명시적 병합

[examples/state_updates.py](framework_compare/examples/state_updates.py)에 `order_id=O-100`, `issue=배송 예정일 문의`를 넣었다. 조회 함수가 `order`·`status`·`path`만 반환하는 경우와, 기존 입력까지 명시적으로 합쳐 반환하는 경우를 비교했다.

| 방식 | 부분 반환: 생성 단계·최종 결과의 문의 | 명시적 병합: 생성 단계·최종 결과의 문의 |
|---|---|---|
| Direct | 모두 `null` | 모두 원래 문의 유지 |
| LangChain Runnable | 모두 `null` | 모두 원래 문의 유지 |
| LangGraph | 모두 원래 문의 유지 | 모두 원래 문의 유지 |

문의가 사라진 위치는 조회 함수의 반환 경계다. `partial_lookup`은 전체 조회 결과에서 다음 항목만 골라 반환한다.

```python
return {key: result[key] for key in ("order", "status", "path")}
```

Direct의 `state = lookup_step(state)`는 이 반환값을 다음 단계의 입력으로 사용한다. Runnable의 `RunnableLambda(lookup_step) | found_or_missing`도 왼쪽 반환값을 오른쪽에 넘긴다. 반환값에 `issue`가 없으므로 생성 단계에서도 문의가 없다. 출력의 `null`은 문의를 의도적으로 `None`으로 수정했다는 뜻이 아니라, 없는 항목을 `state.get("issue")`로 읽은 결과다.

LangGraph는 노드의 반환값을 보관된 상태에 반영한다. 이번 상태의 기본 갱신 규칙에서는 반환한 `order`·`status`·`path`를 바꾸고, 반환하지 않은 `issue`는 유지한다. 따라서 같은 부분 반환을 받아도 생성 노드에는 원래 문의가 남는다.

Direct와 Runnable에서는 보존할 입력을 반환값에 포함하는 방법으로 해결했다.

```python
return {**state, **partial_lookup(state)}
```

이 표현은 기존 값을 먼저 가져오고, 같은 항목은 이번 조회 결과로 바꾸겠다는 뜻이다. 문법 자체보다 **다음 단계에 무엇을 전달할지 명시하는 책임**이 중요하다. 실제 명시적 병합 결과에서는 세 방식 모두 생성 단계와 최종 결과에 문의가 남았다.

모든 경우에 `ANSWERED`가 나온 이유는 `fixed_answer`가 조회 사실만 사용하고 문의를 읽지 않기 때문이다. 그래서 `examples/state_updates.py`는 문장 외에 `issue_at_draft`와 `issue_in_result`를 관찰한다. **답변 생성 성공과 필요한 입력의 보존은 별개의 확인 대상**이며, 이번처럼 병합 한 번으로 해결되는 요구만으로 LangGraph의 도입을 결정할 근거는 충분하지 않다.

## Day 2 — LangChain 에이전트 구성과 번호 보충

### 도구 루프 비교에서 확인한 책임

[examples/tool_loop.py](framework_compare/examples/tool_loop.py)에서는 같은 모의 모델과 조회 도구를 Direct와 LangChain에 연결했다. 두 주문 문의에서 `lookup-0`과 `lookup-1`이 각각 `O-100`, `O-200`의 요청·결과에 연결되고, 두 배송 사실을 받은 뒤 최종 답변이 나왔다. 단일 주문은 조회 한 건, 번호 누락은 조회 없이 질문, `O-999`는 조회 결과 `null` 뒤 부재 안내로 처리됐다.

도구 요청은 함수 실행 결과가 아니라 “이 도구를 이 인자로 실행해 달라”는 모델 응답의 데이터다. 두 주문 사례에서는 하나의 응답에 두 요청이 있었고, 각 조회 결과를 받은 다음 응답에 최종 안내가 나왔다.

```text
모의 모델 응답: lookup-0으로 O-100 조회, lookup-1로 O-200 조회 요청
  → 등록된 도구를 실제 호출
  → 각각의 ToolMessage에 도구 호출 ID와 조회 결과 기록
  → 도구 결과를 포함한 메시지로 모델 재호출
  → 더 실행할 도구 요청이 없으면 답변 반환
```

Direct의 `build_direct_agent`에서는 `response.tool_calls`를 검사하고, 등록 도구를 실행한 결과를 `messages`에 추가한다. 반복문의 다음 회차가 그 결과를 포함해 모델을 다시 호출한다. LangChain의 `create_agent`는 이 실행 루프를 구성한다. 개발자는 모델·도구·지침·호출 한도를 제공한다.

두 방식 모두 공통 모델·도구 어댑터를 사용한다. Direct 역시 일반화한 루프를 재사용할 수 있으므로, 비교할 것은 라이브러리 유무나 매번 코드를 새로 작성하는지보다 공통 실행 루프를 누가 제공하고 유지하는가다.

### 현재 구현과 설계 선택

[shipping/agent.py](framework_compare/shipping/agent.py)의 `ShippingAgent`는 문의와 주문 번호 목록을 별도로 받는다. 예를 들어 문의는 `배송 상태와 도착 날짜가 궁금합니다.`, 번호는 `["O-100", "O-200"]`다. 모델에 맡긴 일은 입력에 포함된 번호의 도구 요청과 조회 후 안내 생성이다.

조회 함수는 [shipping/components.py](framework_compare/shipping/components.py), 도구·업무 상태·middleware는 [shipping/agent.py](framework_compare/shipping/agent.py)의 `shipping_tool`·`ShippingState`·`ShippingPolicy`를 재사용한다. [shipping/support.py](framework_compare/shipping/support.py)는 입력 검사·메시지 구성·모의 모델을 제공한다. `ShippingAgent`는 같은 모듈의 도구·상태·정책을 실제 `create_agent`에 연결하고 시작·보충·공개 결과 접점을 구성했다.

| 선택 | 이유와 코드의 책임 |
|---|---|
| 주문 하나를 조회하는 도구 | 제공 조회 함수를 여러 번호에 재사용한다. 모델이 요청한 번호가 현재 `order_ids`에 있는지는 도구가 검사한다. |
| 메시지와 업무 상태 분리 | `issue`는 원래 문의, `order_ids`는 현재 대상, `orders`는 실제 조회 사실이다. 모델의 문장만으로 조회 여부를 판단하지 않는다. |
| 번호별 조회 결과 병합 | 각 도구가 반환한 일부 사실을 `merge_orders`로 합쳐 복수 주문의 결과를 모두 보관한다. |
| Middleware에 업무 정책 배치 | 번호 누락·빈 보충 종료는 모델 호출 전에, 조회 완료 여부는 답변 공개 전에 검사한다. |
| 체크포인트와 요청 ID | 호출이 끝난 뒤에도 원래 문의를 보관하고, 같은 문의에 번호만 보충한다. |

### 도구 결과가 상태와 모델 메시지로 나뉘는 이유

`shipping_tool`이 공개하는 도구 이름은 `lookup_shipping`이다. 모델이 전달하는 인자는 `order_id`이고, `ToolRuntime`은 런타임이 주입한다. 도구는 `runtime.state["order_ids"]`에서 현재 처리 대상을 읽고, 그 목록에 있는 번호인지 확인한 뒤 실제 조회 함수를 호출한다.

조회 뒤에는 다음 갱신을 반환한다.

```python
facts = {order_id: lookup(order_id)}
return Command(update={
    "orders": facts,
    "messages": [ToolMessage(
        content=json.dumps(facts, ensure_ascii=False),
        tool_call_id=runtime.tool_call_id,
    )],
})
```

`O-200`을 조회했다면 `facts`에는 `{"O-200": {"order_id": "O-200", "shipping": "배송 중"}}`이 들어간다. 같은 사실을 `orders`와 메시지 양쪽에 남기는 것은 두 소비자가 다르기 때문이다.

| 전달 위치 | 누가 무엇에 사용하는가 |
|---|---|
| `orders` | 도구가 실제로 조회한 업무 사실. Middleware가 필요한 번호의 조회 완료 여부를 검사하고 공개 결과에 사용한다. |
| `ToolMessage` | 모델에 돌려주는 도구 응답. 모델이 조회 사실을 읽고 다음 응답을 만들 때 사용한다. |

`ShippingState.orders`의 `merge_orders`는 기존 번호별 결과와 새 결과를 합친다. `O-100` 도구가 반환한 일부 결과와 `O-200` 도구가 반환한 일부 결과가 같은 상태에 모두 남은 이유다. 결과를 교체하는 기본 규칙만 사용한다면 일부 결과를 하나의 묶음으로 모아 반환하는 등 다른 구성이 필요하다.

여기에는 두 종류의 식별자가 있다. `request_id=A/B`와 대응하는 `thread_id`는 원래 문의를 찾는 값이다. `shipping-0`·`shipping-1`과 `tool_call_id`는 도구 호출과 응답을 연결하는 값이다. 이 예제의 도구 호출 ID는 서로 다른 문의에서도 같은 이름이 나올 수 있으므로, 문의 식별자로 대신 사용할 수 없다.

Day 1의 Runnable 값 전달과 달리 이번 에이전트는 상태·갱신 규칙·체크포인트를 사용하는 구성이다. LangChain에서도 문의와 조회 사실을 보관할 수 있다.

### 조회 결과와 그 의미

아래는 `ShippingAgent.start`의 모의 실행 결과다. 복수 주문은 기본 실행의 콘솔 결과이며, 나머지는 구현 검증에서 확인한 분기다.

| 번호 입력 | 공개 상태 | 실제 공개 답변 |
|---|---|---|
| `["O-100"]` | `ANSWERED` | `O-100: 배송 준비` |
| `["O-100", "O-200"]` | `ANSWERED` | `O-100: 배송 준비 / O-200: 배송 중` |
| `["O-999"]` | `NOT_FOUND` | `O-999: 해당 주문을 찾지 못했습니다.` |
| `["O-100", "O-999"]` | `PARTIAL` | `O-100: 배송 준비 / O-999: 해당 주문을 찾지 못했습니다.` |

복수 주문의 콘솔 출력에서 `shipping-0`은 `O-100`, `shipping-1`은 `O-200`에 대응했고, 각 `result_for`가 대응하는 도구 호출 ID를 가리켰다. 도구 결과의 `status=success`는 호출 성공을 뜻한다. 두 결과는 업무 상태 `orders`에 배송 준비·배송 중으로 함께 남았고, 공개 답변에도 반영됐다.

`attempts=0`은 번호 보충이 없었다는 뜻이며, 모델 호출 횟수가 아니다. `status=ANSWERED`, `next=[]`, `interrupted=false`는 답변을 준비하고 이번 턴을 종료한 결과다. 문의에는 도착 날짜도 있었지만 답변은 배송 상태만 표현했다. 자료에 없는 날짜가 추가되지는 않았으나, `ANSWERED`만으로 문의의 모든 요구를 충분히 해결했다고 판단할 수는 없다.

`orders`의 키 존재 여부와 값은 서로 다른 의미다. 예를 들어 아래 두 상태는 같지 않다.

```text
orders = {}                 → 아직 조회한 주문이 없음
orders = {"O-999": null}    → O-999를 조회했지만 자료가 없음
```

`ShippingPolicy`는 `set(orders)`와 `set(order_ids)`를 비교해 현재 번호를 모두 조회했는지 확인한다. 조회가 끝났고 모든 값이 부재이면 `before_model`이 부재 문구를 만들고 턴을 종료한다. 일부는 사실이고 일부는 부재이면 모델이 두 결과를 함께 받아 안내하고, `result_status`가 `PARTIAL`로 구분한다. 따라서 `PARTIAL`은 도구 실행 오류가 아니라 일부 자료를 찾지 못한 업무 결과다.

조회 없이 `내일 도착합니다.`라고 답하는 모의 응답에서는 내부 메시지와 달리 공개 `answer`가 `필요한 조회가 끝나지 않아 답변을 보류했습니다.`로 바뀌었다. `ShippingPolicy.after_model`이 필요한 조회의 완료 여부를 검사한 결과다. 답변의 문장 자체를 사실 검사해서 바꾼 것이 아니라, 조회 근거가 없어서 공개를 보류했다.

`view`는 업무 상태의 `answer`를 공개한다. 내부 마지막 메시지를 그대로 보여 주면 이 공개 정책을 우회하므로 메시지와 공개 답변을 구분해야 한다. 조회가 모두 끝난 뒤 모델이 근거 없는 내용을 덧붙이는 경우까지 이 검사만으로 차단되는 것은 아니다. 이번 정책이 확인하는 조건과 문장 품질의 판단은 다르다.

### 원래 문의를 보관하고 번호만 보충하는 과정

번호 보충의 실행 진입점은 [examples/day2_supplement.py](framework_compare/examples/day2_supplement.py)이며, 같은 `ShippingAgent`의 `start`와 `supplement`를 호출한다. 핵심은 **원래 문의를 모델이 복원하게 하는 대신, 프로그램이 저장한 값을 현재 입력에 다시 넣는 것**이다. 요청 B의 실제 입력을 따라가면 그 경계가 드러난다.

#### 처음 입력을 업무 상태로 저장하기

시작 호출은 `agent.start("B", "현재 배송 상태 문의", [])`다. `initial_state`는 입력받은 문의를 `issue`에 넣고, 번호 목록·조회 사실·보충 횟수·상태·답변을 초기화한다. 다음은 초기 상태에서 설명에 필요한 항목만 추린 것이다.

```python
{
    "issue": "현재 배송 상태 문의",
    "order_ids": [],
    "orders": {},
    "attempts": 0,
    "status": "RECEIVED",
    "answer": "",
}
```

`issue`는 모델이 요약한 문장이 아니라 처음 입력받은 문자열이다. `ShippingAgent.config("B")`는 `{"configurable": {"thread_id": "B"}}`를 만든다. `start`가 이 설정과 입력 상태를 `app.invoke`에 전달하면, 에이전트에 연결된 `InMemorySaver`가 실행 상태를 요청별로 보관한다. 메모리 저장소는 LangChain 런타임에 연결된 것이며 모델 내부의 기억 공간이 아니다.

번호가 없으면 `ShippingPolicy.before_model`이 `WAITING`과 번호 질문을 만들고 `jump_to="end"`로 이번 턴을 끝낸다. 그래서 대기 출력에는 문의가 남고 `next=[]`, `interrupted=false`가 나타난다. 이 경로는 모델을 호출하지 않는다. 질문을 `AIMessage` 형태로 보관해도, 이번 질문의 작성 주체는 모델이 아니라 middleware의 코드다.

#### 요청 ID로 문의를 찾아 보충값과 합치기

번호 보충 호출은 `agent.supplement("B", ["O-200"])`다. 호출자는 원래 문의를 다시 전달하지 않는다. `supplement`는 B의 상태가 번호 대기인지 확인하고, 이전 보충 횟수에 1을 더해 공통 입력 구성 함수 `_next_turn`에 전달한다.

```python
return self._next_turn(request_id, order_ids, previous.values["attempts"] + 1)
```

`_next_turn`은 저장된 문의와 새 번호로 입력을 만든다.

```python
previous = self.snapshot(request_id).values
state = initial_state(previous["issue"], order_ids)
state["attempts"] = attempts
```

`snapshot`은 `app.get_state(self.config(request_id))`로 B의 체크포인트를 읽는다. 이어 `previous["issue"]`에서 `현재 배송 상태 문의`를 꺼내 새 번호 `["O-200"]`와 합친다. 저장된 문의의 내용이나 연결 대상을 모델에게 추측하게 하는 단계가 없다.

```text
저장된 B의 issue: "현재 배송 상태 문의"
새 보충 입력:     ["O-200"]
                  ↓ 코드가 조합
이번 턴의 issue:  "현재 배송 상태 문의"
이번 턴의 번호:   ["O-200"]
이번 보충 횟수:   1
```

A를 보충하면 같은 코드가 A의 상태를 읽는다. 어느 문의의 보충인지는 호출자가 전달한 요청 ID와 상태 조회로 결정된다. 요청 ID를 올바르게 연결하는 책임은 앱에 있다.

#### 업무 상태에서 모델 입력을 구성하기

업무 상태가 모델에게 전부 자동 전달되는 것은 아니다. [shipping/support.py](framework_compare/shipping/support.py)의 `model_input`이 보낼 항목을 고른다.

```python
payload = {"issue": state["issue"], "order_ids": state["order_ids"]}
return HumanMessage(content=json.dumps(payload, ensure_ascii=False))
```

위 코드는 이번 호출처럼 `with_facts=False`인 경로에서 실행되는 메시지 구성이다. B의 보충 입력을 적용하면 모델에 전달하는 현재 문의 메시지는 다음 내용이 된다.

```json
{
  "issue": "현재 배송 상태 문의",
  "order_ids": ["O-200"]
}
```

모델은 `O-200`만 받는 것이 아니라 프로그램이 원래 문의를 붙여 만든 입력을 받는다. 업무 지침은 `create_agent`의 `system_prompt`로 연결돼 있고, 조회 후 사실은 앞서 설명한 `ToolMessage`로 전달된다. `thread_id`는 상태를 찾는 설정이며 위 문의 메시지에 포함되지 않는다.

#### 이전 메시지를 교체해도 문의가 유지되는 이유

`supplement`가 호출한 `_next_turn`은 새 턴의 입력을 다음처럼 전달한다.

```python
self.app.invoke({
    **state,
    "orders": Overwrite({}),
    "messages": [RemoveMessage(id=REMOVE_ALL_MESSAGES), model_input(state)],
}, self.config(request_id))
```

현재 실행에 사용할 메시지 목록에서 이전 메시지를 제거하고, 원래 문의와 새 번호로 만든 메시지를 넣는다. 문의는 이미 별도 `issue`에서 가져왔으므로 이전 질문 메시지가 없어져도 다시 제공할 수 있다. 이는 저장된 모든 체크포인트 이력을 삭제한다는 뜻이 아니라, 이번 턴에 사용할 메시지 구성을 교체하는 동작이다.

`orders`는 기존 결과와 합치는 갱신 규칙을 사용한다. 따라서 새 사실 집합으로 초기화하겠다는 뜻은 `Overwrite({})`로 명시한다. 번호 대기 중에는 사실이 이미 비어 있지만, 메시지와 사실을 어떤 계약으로 초기화하는지는 코드에 드러난다.

이번 업무는 원래 문의 하나와 번호 보충으로 구성돼 있어 이 선택이 가능하다. 여러 턴의 자유 대화 자체가 업무 근거라면 필요한 메시지를 유지하거나 요약하는 다른 구성이 필요하다. **모델의 기억에 의존하지 않는다는 것은 과거 내용이 필요 없다는 뜻이 아니라, 필요한 과거 내용을 프로그램이 보관하고 현재 입력으로 다시 제공한다는 뜻**이다. 현재 `InMemorySaver`는 해당 인스턴스를 사용하는 프로세스 안에서 보관하므로, 종료 뒤 복원까지 필요하면 지속 저장소를 선택해야 한다.

#### 실제 보충 결과와 상태값의 의미

| 입력 순서 | 실제 결과 |
|---|---|
| A: 배송 예정일 문의, 번호 없음 | 원래 문의를 보관하고 번호 질문 |
| B: 현재 배송 상태 문의, 번호 없음 | A와 별도로 보관하고 번호 질문 |
| B에 `O-200` 보충 | B의 문의 유지, 배송 중 안내 |
| A에 `O-100` 보충 | A의 문의 유지, 배송 준비 안내 |

두 요청의 문의와 번호가 섞이지 않은 결과는 요청 ID로 상태를 찾아 새 입력을 구성하는 코드와 대응한다. 보충 스크립트는 B·A 순서로 입력을 전달했다.

각 요청의 `attempts`는 대기 때 `0`, 보충 후 `1`이었다. 두 요청을 합산한 횟수가 아니라 요청별 보충 횟수다. 완료 후에도 `next=[]`, `interrupted=false`이므로 이 값들만으로 업무상 대기와 완료를 구분할 수는 없다. 그 차이는 `WAITING → ANSWERED`와 번호·조회 사실·답변의 변화에 드러난다.

#### 빈 보충 종료와 모델 호출 한도

빈 보충의 종료 동작은 [tests/test_day2_agent.py](framework_compare/tests/test_day2_agent.py)의 자동 실행에서 확인했다. 첫 빈 보충은 `attempts=1 / WAITING`, 두 번째는 `attempts=2 / UNRESOLVED`와 번호 확인 안내로 끝났다. 입력이 여전히 비어 있으므로 middleware가 모델과 조회 도구를 호출하기 전에 처리한다.

`attempts`는 보충을 받은 횟수이고, 유효한 번호가 들어오면 조회로 진행한다. `max_empty_replies=2`는 번호가 계속 없는 경우의 종료 기준이다. 이 수치는 업무 정책의 선택이며 프레임워크의 필수 횟수가 아니다.

`max_model_calls=4`는 한 턴 안에서 모델–도구 루프가 계속 반복되는 것을 제한한다. 재질문은 여러 턴에 걸친 업무 상태이고, 모델 호출 한도는 한 턴의 실행 제어이므로 따로 둔다. 같은 “횟수 제한”이라도 무엇을 세고 언제 초기화하는지를 구분해야 한다.

### 다른 방식과의 선택 기준

| 방식 | 같은 업무를 구성하는 방법과 선택 조건 |
|---|---|
| Direct | 모델–도구 루프와 요청별 상태·보충·공개 정책을 직접 연결한다. 짧고 정해진 조회라면 충분히 단순하며, 공통 루프의 유지도 앱이 맡는다. |
| LangChain | 제공 에이전트 루프에 도구·상태·middleware를 연결한다. 이번 Day 2에서는 이 확장 지점에 업무 규칙을 배치하는 구성을 선택했다. 도구가 늘고 다음 도구 선택이 유동적이면 루프 재사용의 가치가 커진다. |
| LangGraph | 번호 확인·조회·생성·대기·종료를 노드와 경로로 구성한다. 반드시 지킬 순서와 중단 지점이 늘면 정책의 위치를 명시하기 쉽다. 같은 요구에 대한 구성은 Day 3에서 비교한다. |

현재 주문 번호와 조회 함수는 이미 정해져 있으므로 모델에게 조회 요청을 맡기는 것이 가장 효율적이라고 단정할 수는 없다. LangChain 에이전트도 LangGraph를 기반으로 하며, 두 기술은 함께 사용할 수 있다.

## Day 3 — LangGraph의 업무 경로와 대기·실패 재개

### 같은 요구를 명시적 경로로 구성한 이유

Day 2와 같은 문의·번호 목록·주문 자료를 사용하고, 빈 보충이 두 번 이어지면 미해결로 종료하는 정책을 적용한다. 번호 확인·조회·생성·대기·종료를 경로로 나누고 조회와 생성을 별도 노드에 두는 구성을 선택했다. 조회 대상이 입력에서 이미 정해지므로 코드가 조회를 진행하고 모델에는 조회 사실을 바탕으로 안내를 만드는 역할을 맡긴다.

[shipping/graph.py](framework_compare/shipping/graph.py)의 `ShippingGraph`는 같은 모듈의 `build_shipping_graph`를 호출한다. 상태·업무 노드·간선·체크포인트는 이 builder에 있고, 요청 ID 검사와 시작·보충·재시도·공개 결과 접점은 `ShippingGraph`에 연결했다.

복수 주문 조회·역순 번호 보충·생성 실패 재시도의 결과는 `examples/day3_graph.py`, `examples/day3_supplement.py`, `examples/day3_retry.py`의 실제 콘솔 출력을 근거로 정리했다. 부재·빈 보충 종료는 구현 검증에서 확인한 보조 근거다. 모두 `SCRIPTED_MODEL`로 실행한 결과다.

| 위치 | 책임과 다음 경로 |
|---|---|
| `prepare` | 번호가 있으면 `lookup`, 없으면 `collect` |
| `collect` | 번호를 기다리며 중단. 유효한 보충은 `lookup`, 빈 보충은 한도 전까지 다시 `collect`, 한도 도달은 종료 |
| `lookup` | 현재 번호를 모두 조회. 찾은 주문이 있으면 `draft`, 전부 부재이면 `unavailable` |
| `draft` | 원래 문의·현재 번호·조회 사실로 안내 생성. 성공하면 종료 |
| `unavailable` | 모델 호출 없이 부재 안내 후 종료 |

노드를 나누는 기준은 함수 수가 아니라 실패 후 다시 실행할 책임이다. 조회와 생성을 한 노드에 넣으면 생성 실패로 그 노드를 다시 실행할 때 조회도 반복될 수 있다. 분리하면 성공한 조회 뒤에 저장된 사실을 사용해 생성부터 재시도할 수 있다.

### 조회 사실이 모델에 도달하는 경로

`examples/day3_graph.py`에서 실행한 입력은 문의 `배송 상태와 도착 날짜가 궁금합니다.`와 번호 `["O-100", "O-200"]`였다. 출력의 `orders`에는 O-100의 배송 준비와 O-200의 배송 중이 함께 남았고, 공개 답변은 `O-100: 배송 준비 / O-200: 배송 중`이었다. `ANSWERED`, `next=[]`, `interrupted=false`, `question=null`로 대기 없이 종료됐다. `attempts=0`은 처음부터 번호가 있어 보충 입력을 받지 않았다는 뜻이다.

이 입력을 코드의 조건 간선에 대응시키면 `prepare → lookup → draft` 경로다.

`lookup` 노드의 함수 `fetch`는 다음처럼 현재 번호 전체를 조회한다.

```python
orders = {value: lookup(value) for value in state["order_ids"]}
return {"orders": orders, "status": "READY"}
```

Day 2에서는 각 도구 호출이 일부 결과를 반환해 `merge_orders`로 모았다. 여기서는 한 노드가 전체 결과를 반환하므로 `orders`의 기본 교체 규칙을 사용한다. 차이는 프레임워크가 아니라 결과를 만드는 단위에 있다. 조회를 여러 병렬 노드로 나누는 요구라면 결과를 모을 갱신 규칙이 다시 필요하다.

`draft`는 다음 메시지를 모델에 전달한다.

```python
model.invoke([
    SystemMessage(content=ANSWER_POLICY),
    model_input(state, with_facts=True),
])
```

`with_facts=True`이므로 현재 문의 메시지에는 `issue`·`order_ids`뿐 아니라 `orders`도 들어간다. Day 2는 모델이 도구를 요청하고 `ToolMessage`로 사실을 받았다. 이번 구성은 코드가 먼저 조회하고 그 결과를 문의와 함께 전달한다. 모델에 도구 선택을 맡기는 단계가 없어도 같은 고정 안내가 나왔다. 도착 날짜는 자료에도 출력에도 없으며, 이 결과만으로 실제 모델의 문장 품질을 판단할 수는 없다.

부재 경로의 실행에서도 `O-999`는 `NOT_FOUND`와 부재 안내로 끝났고, `O-100`과 `O-999`를 함께 넣으면 배송 준비와 부재 안내를 담은 `PARTIAL`이 나왔다. `orders`에 번호 키와 `None`이 남는 것은 조회했지만 자료가 없다는 의미다. 전부 부재이면 `unavailable`에서, 일부를 찾았으면 `draft`에서 답변을 만든다.

### 원래 문의와 중단 위치를 함께 보관하기

[examples/day3_supplement.py](framework_compare/examples/day3_supplement.py)는 A·B를 번호 없이 시작하고 B에 `O-200`, A에 `O-100`을 역순으로 보충한다.

| 실행 지점 | 원래 문의·현재 번호 | 확인한 상태와 다음 위치 |
|---|---|---|
| A 시작 | 배송 예정일 문의, 번호 없음 | `WAITING`, `next=["collect"]`, `interrupted=true` |
| B 시작 | 현재 배송 상태 문의, 번호 없음 | `WAITING`, `next=["collect"]`, `interrupted=true` |
| B 보충 | 현재 배송 상태 문의, `O-200` | 배송 중 안내, `ANSWERED`, `next=[]` |
| A 보충 | 배송 예정일 문의, `O-100` | 배송 준비 안내, `ANSWERED`, `next=[]` |

A·B의 대기 출력에는 각각 자신의 문의를 담은 `question`이 있었다. B를 먼저 재개해도 B에는 `현재 배송 상태 문의`와 O-200이, 이후 A에는 `배송 예정일 문의`와 O-100이 연결됐다. 두 요청 모두 보충 후 `attempts=1`, `interrupted=false`, `question=null`로 끝났다. 횟수는 요청별로 보관되므로 B를 처리했다고 A의 횟수가 올라가지 않는다. 역순 보충 결과는 입력 순서에 기대지 않고 요청 ID로 상태를 찾는 구성과 일치했다.

시작 입력은 `initial_state(issue, order_ids)`로 만들고, 요청 ID를 `thread_id`에 넣어 실행한다. `collect`는 아래 호출에서 실행을 중단한다.

```python
supplied = interrupt({
    "question": "주문 번호를 알려 주세요.",
    "issue": state["issue"],
})
```

`InMemorySaver`가 연결된 런타임은 그 요청의 상태와 중단 정보를 보관한다. `ShippingGraph.view`는 실제 snapshot의 다음 위치와 interrupt 내용을 읽어 `next`·`interrupted`·`question`으로 공개한다. 대기 중 공개 `answer`도 이 질문에서 가져온다. 생성 모델이 답한 문장이 아니며, 저장 상태의 생성 답변 항목은 아직 비어 있다.

번호 보충 호출은 다음과 같다.

```python
self.app.invoke(
    Command(resume=normalize_ids(order_ids)), self.config(request_id)
)
```

B의 보충에서는 B의 실행을 찾아 `collect` 노드를 처음부터 다시 실행하고, 해당 `interrupt`가 `["O-200"]`을 반환한다. 그 값을 현재 번호로 반영하면 조건 간선이 조회로 보낸다. `collect`는 원래 문의를 갱신하지 않으므로 `issue`는 저장된 상태에 남는다. 이후 `draft`가 그 문의와 조회 사실을 모델 입력에 넣는다. 문의 보존을 모델에게 맡기는 단계는 없다.

Day 2의 `supplement`는 저장된 문의를 읽어 새 턴의 입력을 구성했다. 여기서는 번호만 중단 지점에 전달하고 저장된 그래프 상태를 이어 사용한다. 둘 다 문의를 보관할 수 있지만 다음 입력을 연결하는 접점이 다르다.

| 대기 시 실제 출력 | Day 2 LangChain | Day 3 LangGraph |
|---|---|---|
| 업무 상태 | `WAITING` | `WAITING` |
| 다음 위치 | `[]` | `["collect"]` |
| 중단 여부 | `false` | `true` |

같은 `WAITING`이라도 Day 2는 턴을 끝내고 다음 입력을 받을 상태이고, Day 3는 그래프의 실행 위치가 남은 상태다. 프레임워크의 차이는 최종 배송 문장보다 이 대기 위치와 재개 호출에서 드러난다.

### 빈 보충을 다시 기다리거나 종료하기

빈 보충은 번호를 추가로 받는 호출에도 `[]`처럼 번호가 없는 입력이 전달된 경우다. 아무 호출 없이 기다리는 시간은 횟수에 포함되지 않는다. `examples/day3_supplement.py`의 `--empty` 분기로 확인한 결과는 다음과 같다.

```text
번호 없이 시작   → attempts=0, WAITING,    next=["collect"], interrupted=true
첫 빈 보충       → attempts=1, WAITING,    next=["collect"], interrupted=true
두 번째 빈 보충  → attempts=2, UNRESOLVED, next=[],          interrupted=false
```

`collect`는 `interrupt`가 보충값을 반환한 뒤 `attempts`를 올린다. 첫 중단에서는 보충값이 없으므로 아직 횟수를 올리지 않는다. 유효한 번호이면 횟수에 관계없이 조회로 진행하고, 여전히 번호가 없을 때만 한도와 비교해 대기 또는 종료를 결정한다. 빈 보충이 한도에 도달하면 `주문 번호 확인이 필요합니다.`로 끝난다.

Day 2의 `ShippingPolicy.before_model`도 같은 번호·횟수 조건을 검사했다. 차이는 Day 2에서 `jump_to="end"`로 현재 턴을 끝내는 반면, 여기서는 조건 간선이 `collect`를 다시 선택해 중단하거나 `END`로 종료한다는 것이다. 이 경로에서는 두 구성 모두 모델이나 조회 도구를 호출할 이유가 없다.

### 생성 실패 뒤 조회를 반복하지 않고 재시도하기

[examples/day3_retry.py](framework_compare/examples/day3_retry.py)는 `WorkflowModel(fail_draft_once=True)`를 사용해 조회가 끝난 뒤 첫 안내 생성만 실패시킨다. 출력의 `MODEL_UNAVAILABLE`은 이 제공 사례가 의도적으로 발생시키는 모의 오류다.

| 관찰 항목 | 생성 실패 직후 | 재시도 성공 뒤 |
|---|---|---|
| 원래 문의 | 배송 문의 | 배송 문의 |
| 조회 사실 | `O-100: 배송 준비` | 같은 사실 유지 |
| 공개 답변 | 빈 문자열 | `O-100: 배송 준비` |
| 업무 상태 | `READY` | `ANSWERED` |
| 다음 실행 위치 | `["draft"]` | `[]` |
| 실제 조회 호출 이력 | `["O-100"]` | `["O-100"]` |

실패 직후에도 `READY`인 이유는 마지막으로 성공한 `lookup`이 기록한 상태가 남았기 때문이다. `draft`가 예외로 끝나면 성공 반환값은 상태에 반영되지 않는다. 따라서 업무 상태만으로 성공·실패를 모두 해석하지 않고, 발생한 예외와 남은 실행 위치를 함께 본다. `interrupted=false`여도 실패한 단계는 남을 수 있다. 번호를 기다리는 interrupt와 생성 예외는 다르다.

`retry`는 대기 interrupt가 없고 실행 위치가 남아 있는지 확인한 뒤 `app.invoke(None, config)`를 호출한다. 새 문의를 `START`에 넣는 대신 실패한 실행을 잇는다. 재시도 전후의 `lookup_calls`가 모두 `["O-100"]`이므로 `draft`가 보관된 조회 사실로 생성만 다시 실행했다는 것을 확인할 수 있다. `attempts=0`은 생성 재시도가 번호 보충 횟수에 포함되지 않기 때문이다.

재시도 시점은 `examples/day3_retry.py`가 예외를 받은 뒤 `graph.retry("A")`를 명시적으로 호출해 결정한다. 앱은 언제 재시도할지를 정하고, 런타임은 저장된 상태와 실행 위치로 이어갈 처리를 결정한다. 이 실행에서는 같은 프로세스 안에서 실패 후 바로 재시도했다.

이 선택은 보관된 조회 사실을 재사용해도 되는 동안에 적합하다. 재시도 사이에 배송 상태가 바뀔 수 있어 최신성이 필요하다면 생성만 반복하는 대신 재조회 경로를 선택해야 한다. 노드는 재실행될 수 있으므로 외부 쓰기처럼 중복되면 안 되는 작업이 추가될 때도 실행 경계를 다시 판단해야 한다.

### 다른 구성의 책임과 선택 조건

[examples/waiting_compare.py](framework_compare/examples/waiting_compare.py)의 단일 주문 비교에서도 Direct와 LangGraph 모두 A·B를 역순 보충해 문의를 유지했다. Direct는 `sessions[request_id]`와 다음 위치 표식을 직접 관리했고, LangGraph는 실제 체크포인트와 interrupt에서 다음 위치를 읽었다. 이 결과는 단순한 문의 보관만으로 그래프가 필수는 아니라는 근거다.

| 방식 | 같은 요구를 구성하는 방법 | 이번 선택과의 관계 |
|---|---|---|
| Direct | 요청별 상태와 재질문 횟수를 저장하고, 조회·생성·실패 후 다시 실행할 단계를 직접 연결 | 짧은 고정 조회라면 간단하다. 대기와 실패 위치의 보관·재개 코드도 앱이 관리한다. |
| Day 2 LangChain 구성 | 도구·상태·middleware를 제공 에이전트 루프에 연결하고 보충은 새 턴으로 처리 | 도구 선택이 유동적인 요구에 적합하다. 이번 입력은 조회 대상이 정해져 있어 모델의 도구 선택이 꼭 필요하지는 않다. |
| Day 3 LangGraph 구성 | 코드의 조회와 모델 생성을 분리하고 조건 간선·interrupt·체크포인트로 업무 경로를 연결 | 반드시 조회한 뒤 생성하고, 번호 대기와 생성 실패의 재개 위치를 코드에 명시하는 데 적합하다. |

LangChain의 기반 런타임에서도 실패 재개는 가능하며, 제공 예제의 관련 검사에서 조회를 반복하지 않는 복구가 확인된다. 이번 구성은 업무 단계와 재실행 경계를 노드·간선에 직접 대응시켜 정책의 위치를 읽기 쉽다는 장점이 있다.

## Day 4 — Gemini로 실제 도구 요청과 답변 비교

### 실행 파일에서 모델·업무 코드로 이어지는 경로

[examples/day4_langchain.py](framework_compare/examples/day4_langchain.py)의 `run("langchain")`은 [shipping/live.py](framework_compare/shipping/live.py)의 공통 실행 함수를 호출한다. 이 함수가 [shipping/model_boundary.py](framework_compare/shipping/model_boundary.py)의 `live_model()`로 만든 Gemini 모델과 조회 함수를 기존 `ShippingAgent`에 연결한다. `run("langgraph")`는 같은 접점에 `ShippingGraph`를 연결한다. 문자열 인자는 모델 이름이 아니라 실행할 구성을 선택한다.

복수 주문 실행에서는 `run_case(app, "lookup")`이 문의 `배송 상태와 도착 날짜가 궁금합니다.`와 번호 `["O-100", "O-200"]`으로 `app.start`를 호출했다. LangChain은 모델에 조회 도구를 제공하고, LangGraph는 조회 노드가 만든 사실을 생성 모델에 전달한다.

Gemini의 콘텐츠 블록 응답은 `ShippingGemini`가 `message.text`로 공개 텍스트를 추출해 기존 배송 코드의 문자열 답변 계약에 맞춘다. 도구 요청 메시지는 원래 내용·호출 ID·서명을 유지해 다음 모델 호출에 사용한다. 가짜 SDK 응답을 사용한 연결 검사에서는 서명의 왕복 보존과 최종 텍스트 변환을 확인했다. [LangChain Gemini 연결 문서](https://docs.langchain.com/oss/python/integrations/chat/google_generative_ai)

### 실제 조회 요청과 결과의 연결

LangChain의 실제 도구 요청·결과는 다음과 같았다.

| 모델의 요청 | 연결된 도구 결과 | 조회 사실 |
|---|---|---|
| `call_724178`: `lookup_shipping(order_id="O-100")` | `result_for=call_724178`, 호출 성공 | O-100: 배송 준비 |
| `call_724179`: `lookup_shipping(order_id="O-200")` | `result_for=call_724179`, 호출 성공 | O-200: 배송 중 |

모델은 입력의 두 번호를 하나의 도구 요청 메시지에서 요청했다. 각 도구는 실제 조회 사실을 업무 상태 `orders`에 반영하고 `ToolMessage`로 모델에게 돌려줬다. `ShippingPolicy.after_model`이 두 번호의 조회 완료를 검사한 뒤 생성된 안내를 공개했다.

LangGraph는 `lookup` 노드에서 두 번호를 조회한 뒤 `draft`에 원래 문의·번호·조회 사실을 전달했다. 두 실행 모두 `orders`에 배송 준비·배송 중이 남았고, `lookup_calls=["O-100", "O-200"]`로 각 주문의 로컬 조회가 확인됐다. `lookup_calls`는 주문 조회 이력이며 모델 API 호출 횟수가 아니다.

### 실제 답변과 처리 책임 비교

| 비교 항목 | LangChain | LangGraph |
|---|---|---|
| O-100·O-200 안내 | 배송 준비·배송 중 | 배송 준비·배송 중 |
| 도착 날짜의 실제 안내 | “현재 조회된 정보에는 구체적인 도착 날짜가 포함되어 있지 않아 확인되지 않습니다.” | “도착 날짜는 제공된 정보에 포함되어 있지 않아 확인할 수 없습니다.” |
| 조회를 결정하는 곳 | 모델의 도구 요청 | 코드의 조회 노드 |
| 종료 결과 | `ANSWERED`, `next=[]` | `ANSWERED`, `next=[]` |

두 답변 모두 조회된 배송 상태와 일치했고, 없는 도착 날짜를 만들지 않았다. 모의 모델이 배송 상태만 나열했던 것과 달리, 실제 응답은 도착 날짜 요구에도 정보가 없다는 설명을 덧붙였다.

조회 완료는 LangChain의 middleware와 LangGraph의 경로가 관리한다. 도착 날짜를 확정하지 않은 것은 실제 생성 문장에서 확인한 결과다. 같은 배송 안내를 얻었지만 조회를 모델이 요청하도록 구성했는지, 코드가 먼저 실행하도록 구성했는지가 다르다.

### 부분 부재 — 조회 완료와 주문 존재의 구분

[shipping/live.py](framework_compare/shipping/live.py)의 `partial` 사례로 같은 문의에 `O-100`, `O-999`를 전달했다. 두 구성 모두 `orders`에 O-100의 배송 준비와 `O-999: null`을 저장했고, `lookup_calls`에는 두 번호가 각각 한 번 남았다.

| 비교 항목 | LangChain 실제 결과 | LangGraph 실제 결과 |
|---|---|---|
| O-100 | “배송 준비” | “배송 상태는 배송 준비 중입니다.” |
| O-999 | “배송 정보가 조회되지 않습니다.” | “조회된 주문/배송 정보가 없습니다.” |
| 도착 날짜 | “제공된 자료에 포함되어 있지 않아 확인되지 않습니다.” | “제공된 자료에 없어 확인할 수 없습니다.” |
| 처리 결과 | `PARTIAL`, `next=[]` | `PARTIAL`, `next=[]` |

LangChain은 `call_2116899`로 O-100을, `call_2116900`으로 O-999를 요청했다. 각 결과의 `result_for`가 해당 ID와 일치했고, O-999도 도구 실행 상태는 `success`였다. 이는 조회 함수가 정상적으로 실행돼 해당 번호의 자료가 없다는 결과를 반환했다는 뜻이다. 도구 실행의 성공과 주문 존재 여부는 서로 다른 판단이다.

`orders`에서 번호 자체가 빠진 경우는 아직 필요한 조회가 끝나지 않은 상태이고, `O-999: null`은 그 번호를 조회했지만 자료가 없는 상태다. [ShippingPolicy.after_model](framework_compare/shipping/agent.py)은 요청 번호와 조회 결과의 키 집합을 대조하므로 이번에는 조회 완료로 판단한다. 이어 [result_status](framework_compare/shipping/support.py)가 두 결과 중 하나에만 자료가 있음을 계산해 `PARTIAL`을 반환한다. 이 상태값은 모델이 문장으로 결정하지 않는다.

LangGraph는 [조회·생성 경로](framework_compare/shipping/graph.py)에서 두 번호를 코드로 조회하고, 일부 자료가 있으므로 `draft`로 진행한다. 생성 입력에 배송 준비와 부재 결과를 함께 넣으며, 결과 상태에는 같은 `result_status` 규칙을 적용한다. 두 구성은 조회를 시작하는 책임이 다르지만, 이번 부분 부재의 업무 판정은 같은 코드 규칙을 사용한다.

`PARTIAL`은 입력 대기나 실행 실패가 아니라 요청한 주문 중 일부만 찾았다는 결과다.

## Day 5 — 번호 정정과 이전 근거의 무효화

### 같은 문의에서 번호만 달라질 때 보존할 정보

배송 문의에 O-100으로 답한 뒤 번호가 O-200, O-999로 정정되는 상황을 다룬다. 요청 ID와 원래 문의는 유지하지만, `order_ids → orders → answer`의 의존 관계 때문에 번호를 바꾸면 조회 사실과 답변도 다시 계산해야 한다. 번호만 바꾸고 이전 결과를 남기면 O-200의 문의에 O-100의 배송 준비를 근거로 답할 수 있다.

선택한 정책은 완료한 요청에 정정을 허용하고, 원래 문의를 저장된 `issue`에서 가져와 새 번호와 함께 처리하는 것이다. 이전 조회 사실·답변은 비우며, 번호 보충 횟수는 0으로 초기화한다. 입력 대기 중이면 기존 `supplement`를 사용하고, 실패로 실행 위치가 남아 있으면 완료 요청의 정정으로 처리하지 않는다.

`attempts`는 번호가 없어 대기한 뒤 보충 입력을 제출한 횟수다. 최초 질문은 0회이며 빈 보충과 유효한 번호 제출 모두 횟수를 늘린다. 빈 보충은 한 번이면 다시 기다리고 두 번이면 확인 필요로 종료하며, 유효한 번호를 받으면 조회로 진행한다. 정정은 새 번호의 처리이므로 이전 보충 횟수가 새 처리의 기회를 줄이지 않도록 0부터 시작한다. 요청 전체에 누적 제한이 필요한 업무라면 이 초기화 정책을 바꿔야 한다.

### LangChain — 병합 상태와 모델 문맥을 함께 교체

[ShippingAgent](framework_compare/shipping/agent.py)의 보충 처리에서 새 턴을 구성하는 부분을 `_next_turn`으로 분리했다. `supplement`는 이전 횟수에 1을 더해 호출하고, 추가한 `correct`는 완료 여부를 확인한 뒤 0을 전달한다. 원래 문의를 꺼내고 현재 번호의 입력을 만드는 처리는 두 기능이 공유한다.

`orders`의 병합 규칙은 복수 조회 도구의 결과를 모으는 데 적합하지만, 정정에서는 이전 번호가 남는 원인이 된다. 빈 사전을 병합해도 기존 항목이 삭제되지 않으므로 `Overwrite({})`로 현재 조회 사실을 비운다. 이전 답변은 초기 상태의 빈 `answer`로 교체한다.

업무 상태를 비워도 메시지에는 이전 도구 결과와 답변이 남을 수 있다. `RemoveMessage(id=REMOVE_ALL_MESSAGES)`로 이전 메시지를 제거하고 저장된 문의와 새 번호로 `model_input`을 만든다. 원래 문의의 보존은 모델의 기억에 의존하지 않는다. 다음 모델 호출에는 현재 문의와 번호를 전달하고, 새 도구 결과로 답하게 한다. 전체 대화를 지우는 것은 이번처럼 문의와 현재 번호만으로 처리할 수 있는 업무의 선택이다. 대화 경과가 필요한 상담이라면 이력을 따로 보관하거나 유효한 문맥을 선택해서 전달해야 한다.

### LangGraph — 완료 요청을 새 입력으로 다시 실행

[ShippingGraph.correct](framework_compare/shipping/graph.py)는 같은 요청 ID에 `initial_state(저장된 문의, 새 번호)`를 전달해 그래프를 다시 실행한다. 현재 그래프의 조회 상태는 교체 규칙이므로 빈 `orders`가 이전 결과를 지운다. 기존 조회·생성 노드는 새 번호로 사실과 답변을 다시 계산하며 그대로 재사용한다.

번호 보충은 `collect`에서 대기한 실행에 `Command(resume=...)`로 입력을 돌려준다. 완료된 요청에는 그 대기 지점이 없으므로 정정은 START부터 새 입력을 처리한다. 이전 조회 노드 뒤에서 생성만 재개하면 옛 사실을 사용하므로, 번호가 달라진 이번 요구에는 적합하지 않다.

Direct로도 같은 요구를 구현할 수 있다. 요청별 저장소에서 문의를 유지하고 번호·조회 사실·답변을 교체한 뒤 조회와 생성을 다시 호출하면 된다. LangChain은 상태 병합과 메시지 관리가 변경의 핵심이고, LangGraph는 완료 요청의 재시작 입력이 핵심이다.

### 실제 정정 결과와 이전 정보의 제거

[examples/day5_langchain.py](framework_compare/examples/day5_langchain.py)와 [examples/day5_langgraph.py](framework_compare/examples/day5_langgraph.py)에서 실제 모델을 연결해 `correction` 사례를 실행했다. [공통 실행 함수](framework_compare/shipping/live.py)가 [제공 정정 사례](framework_compare/shipping/scenarios.py)를 각 학습 구현에 적용하며, 같은 요청 A에서 원래 문의 `배송 문의`를 유지한 채 번호만 순서대로 바꾼다.

| 단계 | 두 구성의 현재 조회 사실 | LangChain 실제 답변 | LangGraph 실제 답변 |
|---|---|---|---|
| O-100 시작 | O-100: 배송 준비 | “주문 번호 O-100의 배송 상태는 '배송 준비'입니다.” | “조회하신 주문 번호 O-100의 배송 상태는 '배송 준비' 단계입니다.” |
| O-200 정정 | O-200: 배송 중 | “주문 번호 O-200의 배송 상태는 '배송 중'입니다.” | “조회하신 주문번호 O-200의 배송 상태는 '배송 중'입니다.” |
| O-999 정정 | O-999: `null` | “O-999: 해당 주문을 찾지 못했습니다.” | “O-999: 해당 주문을 찾지 못했습니다.” |

각 단계의 `orders`에는 현재 번호 하나만 남았다. O-200 정정 후 O-100 항목이 사라졌고, 마지막에는 O-999의 부재 결과만 남아 이전 배송 안내가 재사용되지 않았다. 두 구성 모두 앞 두 단계는 `ANSWERED`, 마지막은 `NOT_FOUND`로 끝났다. 원래 문의는 세 단계 내내 같았으며, `next=[]`로 각 처리가 완료됐다.

LangChain의 `tool_events`도 각 단계의 현재 주문 요청과 결과만 보여 줬다. O-200 단계에서는 `call_1435939`의 요청과 같은 ID에 연결된 O-200 결과가 있었고, O-100 도구 이력은 현재 턴에 남지 않았다. 이는 `_next_turn`이 병합되는 조회 상태와 이전 메시지를 함께 교체하는 구조에 대응한다. 전체 현재 메시지에서 옛 주문 정보가 제거되는 동작은 모의 검사에서도 확인했다.

반면 `lookup_calls`는 마지막에 `["O-100", "O-200", "O-999"]`로 누적됐다. 이 목록은 실행 파일이 조회 함수 호출을 관찰하기 위해 보관한 이력이다. 모델에 현재 근거로 주는 `orders`와 역할이 다르므로, 이력에 과거 번호가 있는 것과 현재 조회 상태에 과거 사실이 남는 것을 구분해야 한다. 이번 결과에서는 각 번호의 재조회가 확인됐고 현재 사실은 올바르게 교체됐다.

O-999의 답변이 같은 고정 문장인 이유는 전부 부재일 때 생성 모델을 다시 호출하지 않는 정책 때문이다. LangChain은 모델이 요청한 도구 조회 뒤 `ShippingPolicy.before_model`에서 종료하고, LangGraph는 코드 조회 뒤 `unavailable` 노드에서 종료한다. 실제 출력에서도 LangChain은 O-999의 도구 요청·부재 결과를 남겼고, 두 구성의 최종 답변은 코드의 부재 안내 문장과 일치했다.

정정 뒤에도 `attempts=0`인 것은 번호 정정을 보충 횟수로 세지 않는 정책과 일치한다. 이번 실제 입력에는 번호 보충이 없었다. 보충으로 증가한 횟수가 정정 때 0으로 돌아가는 동작과 이후 빈 보충 종료는 모의 검사로 확인했으며, 기존 요청별 보충 동작도 유지됐다.

### 모델의 역할에 따른 호출 횟수

정상적인 정정 흐름의 모델 호출은 LangChain이 O-100에 2회, O-200에 2회, O-999에 1회이고 LangGraph는 각각 1회, 1회, 0회다. LangChain은 도구 요청과 답변 생성에 모델을 사용하고, LangGraph는 코드가 조회한 뒤 답변만 생성한다. 전부 부재인 경우 두 구성 모두 코드가 안내를 만들어 생성 호출을 생략한다.
