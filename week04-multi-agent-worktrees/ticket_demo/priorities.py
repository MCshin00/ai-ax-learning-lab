def priority_label(priority: str) -> str:
    """Baseline: P1/P2/P3 labels; unknown values remain visible."""
    return {"P1": "높음", "P2": "보통", "P3": "낮음"}.get(priority, priority)
