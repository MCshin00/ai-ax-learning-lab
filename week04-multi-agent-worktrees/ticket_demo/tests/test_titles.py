import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from titles import normalize_title


class TitleTests(unittest.TestCase):
    def test_normal_title_is_preserved(self):
        self.assertEqual("로그인 오류", normalize_title("로그인 오류"))

    def test_leading_trailing_and_repeated_spaces_are_normalized(self):
        self.assertEqual("로그인 오류", normalize_title("  로그인   오류  "))

    def test_tabs_and_newlines_are_normalized(self):
        self.assertEqual("로그인 오류 문의", normalize_title("\t로그인\t오류\n문의 \n"))

    def test_spaces_only_title_uses_placeholder(self):
        self.assertEqual("(제목 없음)", normalize_title("   "))

    def test_mixed_whitespace_only_title_uses_placeholder(self):
        self.assertEqual("(제목 없음)", normalize_title(" \t\n "))


if __name__ == "__main__":
    unittest.main()
