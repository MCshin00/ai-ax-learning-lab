from priorities import priority_label
from titles import normalize_title


def render_ticket(title: str, priority: str) -> str:
    return f"[{priority_label(priority)}] {normalize_title(title)}"


if __name__ == "__main__":
    print(render_ticket("  로그인   오류  ", "P0"))
