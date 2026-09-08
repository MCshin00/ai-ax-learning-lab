import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app import render_ticket


class TicketTests(unittest.TestCase):
    def test_existing_labels_and_trim(self):
        self.assertEqual("[높음] 로그인 오류", render_ticket(" 로그인 오류 ", "P1"))

    def test_unknown_priority_is_visible(self):
        self.assertEqual("[CUSTOM] 요청", render_ticket("요청", "CUSTOM"))


if __name__ == "__main__":
    unittest.main()
