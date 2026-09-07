"""Checks the supplied example, not Codex or Inspector integration."""
import unittest
from mcp import Client
from mcp.types import TextContent, TextResourceContents
from learning_lab_mcp.server import CATALOG, find_products, mcp


class CatalogTests(unittest.IsolatedAsyncioTestCase):
    async def test_product_data_and_unavailable_stock(self):
        async with Client(mcp, raise_exceptions=True) as client:
            notebook = await client.call_tool("get_product", {"product_id": "NOTE-01"})
            pen = await client.call_tool("get_product", {"product_id": "PEN-02"})
        self.assertFalse(notebook.is_error)
        self.assertEqual(3000, notebook.structured_content["price_krw"])
        self.assertFalse(pen.structured_content["in_stock"])

    async def test_unknown_id_is_error(self):
        async with Client(mcp, raise_exceptions=True) as client:
            result = await client.call_tool("get_product", {"product_id": "UNKNOWN"})
        self.assertTrue(result.is_error)
        self.assertIn("Unknown product ID", " ".join(
            block.text for block in result.content if isinstance(block, TextContent)))

    async def test_resource_and_prompt(self):
        async with Client(mcp, raise_exceptions=True) as client:
            resource = await client.read_resource("catalog://help")
            prompt = await client.get_prompt("explain_product", {"product_id": "NOTE-01"})
        self.assertTrue(any("주문은 지원하지" in item.text
                            for item in resource.contents if isinstance(item, TextResourceContents)))
        self.assertTrue(any("NOTE-01" in item.content.text for item in prompt.messages
                            if isinstance(item.content, TextContent)))

    async def test_find_products_schema(self):
        async with Client(mcp, raise_exceptions=True) as client:
            tools = (await client.list_tools()).tools
        tool = next(tool for tool in tools if tool.name == "find_products")
        budget = tool.input_schema["properties"]["max_price_krw"]
        stock = tool.input_schema["properties"]["in_stock_only"]
        self.assertEqual("integer", budget["type"])
        self.assertEqual(0, budget["minimum"])
        self.assertEqual(["max_price_krw"], tool.input_schema["required"])
        self.assertEqual("boolean", stock["type"])
        self.assertIs(True, stock["default"])
        self.assertEqual("array", tool.output_schema["properties"]["result"]["type"])
        self.assertTrue(tool.annotations.read_only_hint)

    async def test_find_products_filters_and_empty_results(self):
        products = {
            "NOTE-01": {"product_id": "NOTE-01", "name": "연습용 노트",
                        "price_krw": 3000, "in_stock": True},
            "PEN-02": {"product_id": "PEN-02", "name": "연습용 펜",
                       "price_krw": 1500, "in_stock": False},
        }
        cases = [
            ({"max_price_krw": 3000}, ["NOTE-01"]),
            ({"max_price_krw": 3000, "in_stock_only": False}, ["NOTE-01", "PEN-02"]),
            ({"max_price_krw": 2000, "in_stock_only": True}, []),
            ({"max_price_krw": 2000, "in_stock_only": False}, ["PEN-02"]),
            ({"max_price_krw": 1500, "in_stock_only": False}, ["PEN-02"]),
            ({"max_price_krw": 1499, "in_stock_only": False}, []),
            ({"max_price_krw": 0, "in_stock_only": False}, []),
        ]
        async with Client(mcp, raise_exceptions=True) as client:
            for arguments, expected_ids in cases:
                with self.subTest(arguments=arguments):
                    result = await client.call_tool("find_products", arguments)
                    self.assertFalse(result.is_error)
                    self.assertEqual(
                        {"result": [products[product_id] for product_id in expected_ids]},
                        result.structured_content,
                    )

    async def test_find_products_rejects_invalid_arguments(self):
        cases = [({}, "max_price_krw")]
        cases.extend(({"max_price_krw": value}, "max_price_krw")
                     for value in (-1, 0.5, 3000.0, "3000", True, None))
        cases.extend(({"max_price_krw": 3000, "in_stock_only": value}, "in_stock_only")
                     for value in ("false", 0, None))
        async with Client(mcp, raise_exceptions=True) as client:
            for arguments, invalid_field in cases:
                with self.subTest(arguments=arguments):
                    result = await client.call_tool("find_products", arguments)
                    self.assertTrue(result.is_error)
                    self.assertIsNone(result.structured_content)
                    self.assertIn(invalid_field, " ".join(
                        block.text for block in result.content if isinstance(block, TextContent)))

    def test_find_products_returns_copies_without_changing_catalog(self):
        before = {key: product.model_dump() for key, product in CATALOG.items()}
        matches = find_products(3000)
        self.assertIsNot(matches[0], CATALOG["NOTE-01"])
        matches[0].price_krw = 1
        matches[0].in_stock = False
        self.assertEqual(before, {key: product.model_dump() for key, product in CATALOG.items()})


if __name__ == "__main__":
    unittest.main()
