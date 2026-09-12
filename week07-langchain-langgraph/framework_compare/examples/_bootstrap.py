"""IDE에서 파일을 직접 실행할 때 프로젝트 패키지를 찾습니다."""
import sys
from pathlib import Path

project_root = str(Path(__file__).resolve().parents[1])
if project_root not in sys.path:
    sys.path.insert(0, project_root)
