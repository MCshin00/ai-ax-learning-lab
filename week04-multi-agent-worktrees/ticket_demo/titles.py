def normalize_title(title: str) -> str:
    """Baseline: trim the ends; internal whitespace is preserved."""
    return title.strip()
