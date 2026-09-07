"""Purchase review contract checks through the SDK's in-memory MCP connection."""
import unittest

from mcp import Client
from mcp.types import TextContent

from learning_lab_mcp.server import CATALOG, mcp


class PurchaseReviewTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.catalog_before = {key: product.model_dump() for key, product in CATALOG.items()}
        self.items = [
            {"product_id": "NOTE-01", "quantity": 2},
            {"product_id": "PEN-02", "quantity": 1},
        ]

    def tearDown(self):
        self.assertEqual(
            self.catalog_before, {key: product.model_dump() for key, product in CATALOG.items()}
        )

    def assert_input_error(self, result, message):
        self.assertTrue(result.is_error)
        self.assertIsNone(result.structured_content)
        self.assertIn(message, " ".join(
            block.text for block in result.content if isinstance(block, TextContent)
        ))

    async def test_schema_and_existing_tools(self):
        async with Client(mcp, raise_exceptions=True) as client:
            tools = (await client.list_tools()).tools
        self.assertTrue({"get_product", "find_products", "review_purchase"}.issubset(
            {tool.name for tool in tools}
        ))
        tool = next(tool for tool in tools if tool.name == "review_purchase")
        schema = tool.input_schema
        self.assertEqual({"items", "budget_krw"}, set(schema["required"]))
        items = schema["properties"]["items"]
        self.assertEqual("array", items["type"])
        self.assertEqual(1, items["minItems"])
        item = items["items"]
        if "$ref" in item:
            item = schema["$defs"][item["$ref"].rsplit("/", 1)[1]]
        self.assertEqual({"product_id", "quantity"}, set(item["required"]))
        self.assertEqual("string", item["properties"]["product_id"]["type"])
        self.assertEqual("integer", item["properties"]["quantity"]["type"])
        self.assertEqual(1, item["properties"]["quantity"]["minimum"])
        budget = schema["properties"]["budget_krw"]
        self.assertEqual("integer", budget["type"])
        self.assertEqual(0, budget["minimum"])
        self.assertEqual("object", tool.output_schema["type"])
        self.assertTrue(tool.annotations.read_only_hint)
        self.assertFalse(tool.annotations.destructive_hint)

    async def test_requested_example_includes_out_of_stock_price(self):
        async with Client(mcp, raise_exceptions=True) as client:
            result = await client.call_tool("review_purchase", {
                "items": self.items, "budget_krw": 8000,
            })
        self.assertFalse(result.is_error)
        self.assertEqual({
            "items": [
                {"product_id": "NOTE-01", "name": "연습용 노트", "price_krw": 3000,
                 "in_stock": True, "quantity": 2, "subtotal_krw": 6000},
                {"product_id": "PEN-02", "name": "연습용 펜", "price_krw": 1500,
                 "in_stock": False, "quantity": 1, "subtotal_krw": 1500},
            ],
            "budget_krw": 8000,
            "total_krw": 7500,
            "over_budget": False,
            "over_budget_krw": 0,
            "out_of_stock_product_ids": ["PEN-02"],
        }, result.structured_content)

    async def test_equal_lower_and_zero_budgets(self):
        async with Client(mcp, raise_exceptions=True) as client:
            for budget, over_budget, excess in ((7500, False, 0), (7499, True, 1), (0, True, 7500)):
                with self.subTest(budget=budget):
                    result = await client.call_tool("review_purchase", {
                        "items": self.items, "budget_krw": budget,
                    })
                    self.assertFalse(result.is_error)
                    self.assertEqual(7500, result.structured_content["total_krw"])
                    self.assertIs(over_budget, result.structured_content["over_budget"])
                    self.assertEqual(excess, result.structured_content["over_budget_krw"])

    async def test_other_quantities_and_stock_lists(self):
        cases = [
            ([{"product_id": "NOTE-01", "quantity": 3}], [9000], 9000, []),
            ([{"product_id": "PEN-02", "quantity": 2},
              {"product_id": "PEN-02", "quantity": 1}], [3000, 1500], 4500, ["PEN-02"]),
        ]
        async with Client(mcp, raise_exceptions=True) as client:
            for items, subtotals, total, unavailable in cases:
                with self.subTest(items=items):
                    result = await client.call_tool("review_purchase", {
                        "items": items, "budget_krw": 10000,
                    })
                    self.assertFalse(result.is_error)
                    data = result.structured_content
                    self.assertEqual(items, [
                        {"product_id": line["product_id"], "quantity": line["quantity"]}
                        for line in data["items"]
                    ])
                    self.assertEqual(subtotals, [line["subtotal_krw"] for line in data["items"]])
                    self.assertEqual(total, data["total_krw"])
                    self.assertEqual(unavailable, data["out_of_stock_product_ids"])
                    for line in data["items"]:
                        self.assertEqual(CATALOG[line["product_id"]].price_krw, line["price_krw"])

    async def test_unknown_id_fails_entire_request_in_any_position(self):
        valid = {"product_id": "NOTE-01", "quantity": 2}
        unknown = {"product_id": "UNKNOWN", "quantity": 1}
        async with Client(mcp, raise_exceptions=True) as client:
            for items in ([valid, unknown], [unknown, valid], [unknown]):
                with self.subTest(items=items):
                    result = await client.call_tool("review_purchase", {
                        "items": items, "budget_krw": 8000,
                    })
                    self.assert_input_error(result, "Unknown product ID")
                    self.assertIn("UNKNOWN", " ".join(
                        block.text for block in result.content if isinstance(block, TextContent)
                    ))

    async def test_rejects_invalid_quantities_and_budgets(self):
        cases = [
            ({"items": [{"product_id": "NOTE-01", "quantity": value}], "budget_krw": 8000},
             "quantity")
            for value in (0, -1, 1.5, 1.0, "1", True, False, None)
        ]
        cases.extend(
            ({"items": self.items, "budget_krw": value}, "budget_krw")
            for value in (-1, 0.5, 8000.0, "8000", True, False, None)
        )
        # A valid first line must not hide an invalid later line.
        cases.append(({"items": [self.items[0], {"product_id": "PEN-02", "quantity": 0}],
                       "budget_krw": 8000}, "quantity"))
        async with Client(mcp, raise_exceptions=True) as client:
            for arguments, field in cases:
                with self.subTest(arguments=arguments):
                    result = await client.call_tool("review_purchase", arguments)
                    self.assert_input_error(result, field)

    async def test_rejects_empty_missing_and_malformed_items(self):
        cases = [
            ({"budget_krw": 8000}, "items"),
            ({"items": self.items}, "budget_krw"),
        ]
        cases.extend(({"items": value, "budget_krw": 8000}, "items")
                     for value in ([], None, {}, [None], ["NOTE-01"]))
        cases.extend(({"items": [value], "budget_krw": 8000}, field) for value, field in (
            ({"quantity": 1}, "product_id"),
            ({"product_id": "NOTE-01"}, "quantity"),
            ({"product_id": 123, "quantity": 1}, "product_id"),
            ({"product_id": None, "quantity": 1}, "product_id"),
            ({"product_id": "NOTE-01", "quantity": 1, "price_krw": 1}, "price_krw"),
        ))
        async with Client(mcp, raise_exceptions=True) as client:
            for arguments, field in cases:
                with self.subTest(arguments=arguments):
                    result = await client.call_tool("review_purchase", arguments)
                    self.assert_input_error(result, field)


if __name__ == "__main__":
    unittest.main()
