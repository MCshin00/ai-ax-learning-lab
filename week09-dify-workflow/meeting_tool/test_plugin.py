import json
import unittest
from pathlib import Path

from unittest.mock import patch

# 오프라인 검사에서 인증 파일 로더가 호출되면 즉시 실패시킵니다.
for loader in ("dotenv.load_dotenv", "dotenv.dotenv_values", "dotenv.find_dotenv",
               "dotenv.main.DotEnv._get_stream"):
    guard = patch(loader, side_effect=AssertionError("오프라인 검사에서 dotenv 접근 금지"))
    guard.start()
    unittest.addModuleCleanup(guard.stop)

from dify_plugin import DifyPluginEnv
from dify_plugin.core.plugin_registration import PluginRegistration
from meeting_review import review
from tools.review_tasks import ReviewTasksTool


class PluginTest(unittest.TestCase):
    def test_sdk_loads_provider_tool_and_assets(self):
        # 검증은 환경·dotenv 설정 공급자를 호출하지 않는 가짜 SDK 설정을 사용합니다.
        registration = PluginRegistration(DifyPluginEnv.model_construct())
        self.assertEqual("meeting_review_lab", registration.configuration.name)
        self.assertEqual("review_tasks", registration.tools_configuration[0].tools[0].identity.name)
        self.assertTrue(registration.files)

    def test_supplied_cases_and_sdk_variables(self):
        cases = json.loads(Path("../workflow_materials/cases.json").read_text(encoding="utf-8"))
        for case in cases:
            with self.subTest(case=case["id"]):
                params = {"memo": case["memo"], "extraction_json": json.dumps(case["extraction"], ensure_ascii=False)}
                result = review(**params)
                self.assertEqual(case["expected_status"], result["status"])
                if case["id"] == "mixed":
                    self.assertEqual(["ready", "review"], [task["status"] for task in result["tasks"]])
                    self.assertEqual(["민수"], result["tasks"][0]["owners"])
                    self.assertEqual(["담당자 미정", "기한 미정"], result["tasks"][1]["reasons"])
                messages = list(ReviewTasksTool.from_credentials({}).invoke(params))
                variables = {message.message.variable_name: message.message.variable_value
                             for message in messages if message.type.value == "variable"}
                self.assertEqual(result["status"], variables["status"])
                self.assertEqual(result["summary"], variables["summary"])
                self.assertEqual(result, json.loads(variables["payload"]))

    def test_malformed_output_is_not_successful_empty_result(self):
        self.assertEqual("review", review("회의 메모", '{"tasks":[]}')["status"])
        self.assertEqual("invalid", review("회의 메모", '{"tasks":[null]}')["status"])
        self.assertEqual("invalid", review("회의 메모", "not json")["status"])
        self.assertEqual("invalid", review("회의 메모", '{"tasks":{}}')["status"])

    def test_validator_cannot_recover_a_dropped_conflicting_name(self):
        memo = "민수가 금요일에 초안을 공유한다. 지수도 초안 공유 담당자라고 기록됐다."
        dropped = {"tasks": [{"title": "초안 공유", "owners": ["민수"], "due": "금요일",
                              "evidence": ["민수가 금요일에 초안을 공유한다."], "conflicts": []}]}
        # 이 통과는 의미 정확성의 증명이 아닙니다. 교재가 설명하는 검사의 한계입니다.
        self.assertEqual("ready", review(memo, json.dumps(dropped))["status"])


if __name__ == "__main__":
    unittest.main()
