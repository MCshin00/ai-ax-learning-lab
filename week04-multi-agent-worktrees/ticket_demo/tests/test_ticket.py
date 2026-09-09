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

    def test_p0_normalizes_title_in_final_display(self):
        self.assertEqual("[긴급] 로그인 오류", render_ticket("  로그인   오류  ", "P0"))

    def test_whitespace_only_p0_requires_owner_check(self):
        for title in ("   ", " \t\n "):
            with self.subTest(title=title):
                self.assertEqual("[긴급] 담당자 확인 필요", render_ticket(title, "P0"))

    def test_whitespace_only_nonurgent_title_keeps_placeholder(self):
        for priority, label in (("P1", "높음"), ("P2", "보통"), ("P3", "낮음"), ("CUSTOM", "CUSTOM")):
            with self.subTest(priority=priority):
                self.assertEqual(f"[{label}] (제목 없음)", render_ticket("   ", priority))

    def test_literal_placeholder_is_preserved_for_p0(self):
        self.assertEqual("[긴급] (제목 없음)", render_ticket("(제목 없음)", "P0"))


if __name__ == "__main__":
    unittest.main()
