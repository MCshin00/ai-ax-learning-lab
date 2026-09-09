import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from refund import refund_amount


class RefundTests(unittest.TestCase):
    def test_regular_refund(self):
        self.assertEqual(8000, refund_amount(10000, 2000))

    def test_refund_never_becomes_negative(self):
        self.assertEqual(0, refund_amount(1000, 2000))

    def test_rejects_negative_inputs(self):
        for paid, fee in [(-1, 0), (1, -1)]:
            with self.subTest(paid=paid, fee=fee), self.assertRaises(ValueError):
                refund_amount(paid, fee)


if __name__ == "__main__":
    unittest.main()
