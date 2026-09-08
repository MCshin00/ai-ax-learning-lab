def priority_label(priority: str) -> str:
    """Return a display label; unknown values remain visible."""
    return {"P0": "긴급", "P1": "높음", "P2": "보통", "P3": "낮음"}.get(priority, priority)
