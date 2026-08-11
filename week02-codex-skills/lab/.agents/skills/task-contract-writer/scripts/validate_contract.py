from __future__ import annotations

import argparse
import json
from pathlib import Path

REQUIRED = [
    "Goal", "Context", "Allowed paths", "Forbidden changes",
    "Acceptance criteria", "Required verification", "Stop conditions", "Handoff",
]


def validate(path: Path) -> dict[str, object]:
    """TODO: 각 ## heading의 존재·중복·빈 본문을 검사한다."""
    if not path.is_file():
        return {"valid": False, "errors": [f"File not found: {path}"]}
    return {"valid": False, "errors": ["TODO: implement contract validation"]}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", type=Path)
    args = parser.parse_args()
    result = validate(args.path)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result.get("valid") else 1


if __name__ == "__main__":
    raise SystemExit(main())
