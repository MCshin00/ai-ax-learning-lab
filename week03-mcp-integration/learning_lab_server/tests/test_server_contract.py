"""Checks the supplied example, not Codex or Inspector integration."""
import unittest
from mcp import Client
from mcp.types import TextContent, TextResourceContents
from learning_lab_mcp.server import mcp


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


if __name__ == "__main__":
    unittest.main()
