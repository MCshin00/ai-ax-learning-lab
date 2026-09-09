from priorities import priority_label
from titles import normalize_title


def render_ticket(title: str, priority: str) -> str:
    display_title = normalize_title(title)
    if priority == "P0" and not title.strip():
        display_title = "담당자 확인 필요"
    return f"[{priority_label(priority)}] {display_title}"


if __name__ == "__main__":
    print(render_ticket("  로그인   오류  ", "P0"))
