import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from priorities import priority_label


class PriorityTests(unittest.TestCase):
    def test_p0_returns_urgent_label_without_brackets(self):
        self.assertEqual("긴급", priority_label("P0"))

    def test_p1_keeps_high_label(self):
        self.assertEqual("높음", priority_label("P1"))

    def test_p2_keeps_normal_label(self):
        self.assertEqual("보통", priority_label("P2"))

    def test_p3_keeps_low_label(self):
        self.assertEqual("낮음", priority_label("P3"))

    def test_unknown_priority_keeps_original_value(self):
        self.assertEqual("CUSTOM", priority_label("CUSTOM"))


if __name__ == "__main__":
    unittest.main()
