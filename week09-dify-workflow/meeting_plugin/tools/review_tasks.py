from collections.abc import Generator
from typing import Any
import json

from dify_plugin import Tool
from dify_plugin.entities.tool import ToolInvokeMessage
from business import review


class ReviewTasksTool(Tool):
    def _invoke(self, tool_parameters: dict[str, Any]) -> Generator[ToolInvokeMessage, None, None]:
        result = review(tool_parameters.get("memo"), tool_parameters.get("extraction_json"))
        yield self.create_json_message(result)
        yield self.create_variable_message("status", result["status"])
        yield self.create_variable_message("summary", result["summary"])
        yield self.create_variable_message("payload", json.dumps(result, ensure_ascii=False))
