def normalize_title(title: str) -> str:
    """Normalize whitespace and show a placeholder for whitespace-only titles."""
    normalized = " ".join(title.split())
    return normalized or "(제목 없음)"
