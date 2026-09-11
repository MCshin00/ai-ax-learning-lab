"""공유 업무 함수를 Direct, 실제 LangChain, 실제 LangGraph로 연결합니다."""
from langchain_core.runnables import RunnableBranch, RunnableLambda
from langgraph.graph import END, START, StateGraph

from business import State, after_lookup, after_prepare, ask, lookup, prepare, unavailable


def build_direct(draft):
    def run(state):
        state = prepare(state)
        if not state["order_id"]:
            return ask(state)
        state = lookup(state)
        if state["status"] != "FOUND":
            return unavailable(state)
        return draft(state)
    return run


def build_langchain(draft):
    found_or_missing = RunnableBranch(
        (lambda state: state["status"] == "FOUND", RunnableLambda(draft)),
        RunnableLambda(unavailable),
    )
    chain = RunnableLambda(prepare) | RunnableBranch(
        (lambda state: not state["order_id"], RunnableLambda(ask)),
        RunnableLambda(lookup) | found_or_missing,
    )
    return chain.invoke


def build_langgraph(draft):
    graph = StateGraph(State)
    for name, action in {"prepare": prepare, "ask": ask, "lookup": lookup,
                         "unavailable": unavailable, "draft": draft}.items():
        graph.add_node(name, action)
    graph.add_edge(START, "prepare")
    graph.add_conditional_edges("prepare", after_prepare, {"lookup": "lookup", "ask": "ask"})
    graph.add_conditional_edges("lookup", after_lookup,
                                {"draft": "draft", "unavailable": "unavailable"})
    for name in ("ask", "unavailable", "draft"):
        graph.add_edge(name, END)
    return graph.compile().invoke


BUILDERS = {"direct": build_direct, "langchain": build_langchain, "langgraph": build_langgraph}
