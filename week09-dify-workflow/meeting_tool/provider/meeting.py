from typing import Any
from dify_plugin import ToolProvider


class MeetingProvider(ToolProvider):
    def _validate_credentials(self, credentials: dict[str, Any]) -> None:
        # 제공 로컬 업무 함수는 외부 서비스 자격 증명을 사용하지 않습니다.
        return None
