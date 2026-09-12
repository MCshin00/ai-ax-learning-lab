"""실제 설정·SDK 호출·대기 없이 동기 Gemini 요청 간격을 확인합니다."""
import contextlib
import io
import unittest
from unittest.mock import patch

from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult

import shipping.model_boundary as boundary


class PacingTest(unittest.TestCase):
    def test_spacing_includes_first_and_failed_request_and_elapsed_time(self):
        now = [0.0]
        starts = []
        delays = []

        def sleep(seconds):
            delays.append(seconds)
            now[0] += seconds

        def generate(*args, **kwargs):
            starts.append(now[0])
            if len(starts) == 4:
                raise RuntimeError("simulated failure")
            now[0] += 2.0
            return ChatResult(generations=[ChatGeneration(
                message=AIMessage(content=[{"type": "text", "text": "배송 준비"}])
            )])

        # SDK 클라이언트 초기화도 생략하고 호출 경계만 모의 응답으로 검증합니다.
        model = boundary.ShippingGemini.model_construct()
        with patch.object(boundary, "_LAST_REQUEST_START", None), \
                patch.object(boundary.time, "monotonic", side_effect=lambda: now[0]), \
                patch.object(boundary.time, "sleep", side_effect=sleep), \
                patch.object(boundary.ChatGoogleGenerativeAI, "_generate", side_effect=generate), \
                contextlib.redirect_stdout(io.StringIO()):
            result = model._generate([])
            self.assertEqual("배송 준비", result.generations[0].message.content)
            model._generate([])
            now[0] += 20.0
            model._generate([])
            with self.assertRaisesRegex(RuntimeError, "simulated failure"):
                model._generate([])
            model._generate([])

        self.assertEqual([13.0, 26.0, 48.0, 61.0, 74.0], starts)
        self.assertEqual([13.0, 11.0, 11.0, 13.0], delays)


if __name__ == "__main__":
    unittest.main()
