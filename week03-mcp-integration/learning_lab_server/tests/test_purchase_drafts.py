"""Draft workflow checks using temporary files, SDK calls and real stdio."""
import asyncio
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

from mcp import Client
from mcp.client.stdio import StdioServerParameters, stdio_client

from learning_lab_mcp.drafts import DraftError, DraftStore
from learning_lab_mcp.inspector import create_inspector_server
from learning_lab_mcp.server import CATALOG, PurchaseItem, mcp, review_purchase


class DraftTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / "drafts"
        self.app = create_inspector_server(self.root)
        self.arguments = {
            "request_id": "purchase-001",
            "items": [
                {"product_id": "NOTE-01", "quantity": 2},
                {"product_id": "PEN-02", "quantity": 1},
            ],
            "budget_krw": 8000,
        }

    async def preview(self, client, **changes):
        result = await client.call_tool(
            "preview_purchase_draft", self.arguments | changes,
        )
        self.assertFalse(result.is_error, result)
        return result.structured_content

    async def save(self, client, preview):
        return await client.call_tool(
            "save_purchase_draft", {"preview_id": preview["preview_id"]},
        )

    def assert_error(self, result, code):
        self.assertTrue(result.is_error, result)
        self.assertIsNone(result.structured_content)
        self.assertIn(code, " ".join(getattr(item, "text", "") for item in result.content))

    async def test_default_server_cannot_list_or_call_draft_tools(self):
        async with Client(mcp) as client:
            self.assertEqual(
                {"get_product", "find_products", "review_purchase"},
                {tool.name for tool in (await client.list_tools()).tools},
            )
            for name in ("preview_purchase_draft", "save_purchase_draft"):
                result = await client.call_tool(name, {"preview_id": "invented"})
                self.assertTrue(result.is_error)
        async with Client(self.app) as client:
            tools = {tool.name: tool for tool in (await client.list_tools()).tools}
        self.assertEqual(11, len(tools))
        self.assertFalse(tools["save_purchase_draft"].annotations.read_only_hint)
        self.assertTrue(tools["save_purchase_draft"].annotations.idempotent_hint)
        self.assertEqual(
            ["preview_id"], tools["save_purchase_draft"].input_schema["required"],
        )

    async def test_preview_shows_complete_quote_and_path_without_creating_files(self):
        async with Client(self.app) as client:
            preview = await self.preview(client)
        self.assertEqual(str(self.root / "purchase-001.json"), preview["path"])
        self.assertEqual("preview_snapshot", preview["pricing_policy"])
        self.assertTrue(preview["estimate_complete"])
        quote = preview["quote"]
        self.assertEqual(8500, quote["total_krw"])
        self.assertEqual(500, quote["over_budget_krw"])
        self.assertEqual(["PEN-02"], quote["out_of_stock_product_ids"])
        # Ending without saving models a declined or unconfirmed preview.
        self.assertFalse(self.root.exists())

    async def test_save_preserves_preview_even_if_catalog_or_returned_data_changes(self):
        async with Client(self.app) as client:
            preview = await self.preview(client)
            preview["quote"]["items"][0]["price_krw"] = 1
            with patch.object(CATALOG["NOTE-01"], "price_krw", 4000):
                result = await self.save(client, preview)
        self.assertFalse(result.is_error, result)
        self.assertEqual("saved", result.structured_content["status"])
        saved = json.loads((self.root / "purchase-001.json").read_text(encoding="utf-8"))
        self.assertEqual(3500, saved["quote"]["items"][0]["price_krw"])
        self.assertEqual(8500, saved["quote"]["total_krw"])
        self.assertEqual("purchase-001", saved["request_id"])
        self.assertEqual(1, len(list(self.root.iterdir())))

    async def test_new_preview_invalidates_previous_id_and_saves_new_quantity(self):
        async with Client(self.app) as client:
            old = await self.preview(client)
            new = await self.preview(client, items=[
                {"product_id": "NOTE-01", "quantity": 1},
                {"product_id": "PEN-02", "quantity": 1},
            ])
            self.assert_error(await self.save(client, old), "INVALID_PREVIEW")
            self.assertFalse(self.root.exists())
            result = await self.save(client, new)
        self.assertFalse(result.is_error, result)
        self.assertEqual(5000, result.structured_content["total_krw"])

    async def test_same_request_and_content_reuses_identical_file(self):
        async with Client(self.app) as client:
            preview = await self.preview(client)
            first = await self.save(client, preview)
            destination = self.root / "purchase-001.json"
            original = destination.read_bytes()
            first_stat = destination.stat()
            again = await self.save(client, preview)
            fresh = await self.preview(client)
            fresh_result = await self.save(client, fresh)
        for result in (again, fresh_result):
            self.assertFalse(result.is_error, result)
            self.assertEqual("already_saved", result.structured_content["status"])
            self.assertEqual(first.structured_content["path"], result.structured_content["path"])
        self.assertEqual(original, destination.read_bytes())
        self.assertEqual(first_stat.st_mtime_ns, destination.stat().st_mtime_ns)
        self.assertEqual(1, len(list(self.root.iterdir())))

    async def test_different_content_same_id_and_edited_file_conflict(self):
        async with Client(self.app) as client:
            preview = await self.preview(client)
            await self.save(client, preview)
            destination = self.root / "purchase-001.json"
            original = destination.read_bytes()
            changed = await self.preview(client, budget_krw=9000)
            self.assert_error(await self.save(client, changed), "DRAFT_CONFLICT")
            self.assertEqual(original, destination.read_bytes())
            fresh = await self.preview(client)
            edited = original + b"\n"
            destination.write_bytes(edited)
            self.assert_error(await self.save(client, fresh), "DRAFT_CONFLICT")
            self.assertEqual(edited, destination.read_bytes())

    async def test_destination_owned_by_other_request_is_preserved(self):
        async with Client(self.app) as client:
            preview = await self.preview(client)
            await self.save(client, preview)
            foreign = (self.root / "purchase-001.json").read_bytes()
            destination = self.root / "purchase-002.json"
            destination.write_bytes(foreign)
            other = await self.preview(client, request_id="purchase-002")
            self.assert_error(await self.save(client, other), "DRAFT_CONFLICT")
        self.assertEqual(foreign, destination.read_bytes())

    async def test_invalid_request_ids_cannot_escape_root(self):
        async with Client(self.app) as client:
            for request_id in ("", "../outside", "a/b", "a\\b", "/outside",
                               "C:outside", "UPPER", "con", "lpt1", ".", "a" * 65):
                with self.subTest(request_id=request_id):
                    result = await client.call_tool(
                        "preview_purchase_draft", self.arguments | {"request_id": request_id},
                    )
                    self.assert_error(result, "INVALID_REQUEST_ID")
        self.assertFalse(self.root.exists())
        self.assertEqual([], list(Path(self.temporary.name).iterdir()))

    async def test_invalid_inputs_and_unknown_products_have_no_savable_preview(self):
        bad = [
            {"items": []},
            {"items": [{"product_id": "NOTE-01", "quantity": 0}]},
            {"items": [{"product_id": "NOTE-01", "quantity": 1, "price_krw": 1}]},
            {"items": [{"product_id": "NOTE-01", "quantity": 2},
                       {"product_id": "UNKNOWN", "quantity": 1}]},
            {"budget_krw": -1},
            {"budget_krw": "8000"},
        ]
        async with Client(self.app) as client:
            for changes in bad:
                with self.subTest(changes=changes):
                    result = await client.call_tool(
                        "preview_purchase_draft", self.arguments | changes,
                    )
                    self.assertTrue(result.is_error, result)
                    self.assertIsNone(result.structured_content)
            result = await client.call_tool("save_purchase_draft", {"preview_id": "invented"})
            self.assert_error(result, "INVALID_PREVIEW")
        self.assertFalse(self.root.exists())

    async def test_publication_failure_preserves_existing_files_and_allows_retry(self):
        self.root.mkdir()
        unrelated = self.root / "existing.json"
        unrelated.write_bytes(b"existing draft")
        async with Client(self.app) as client:
            preview = await self.preview(client)
            with patch("learning_lab_mcp.drafts.os.link", side_effect=PermissionError("test failure")):
                self.assert_error(await self.save(client, preview), "SAVE_FAILED")
            self.assertEqual(["existing.json"], [path.name for path in self.root.iterdir()])
            self.assertEqual(b"existing draft", unrelated.read_bytes())
            result = await self.save(client, preview)
        self.assertFalse(result.is_error, result)
        self.assertEqual(8500, result.structured_content["total_krw"])

    async def test_flush_failure_cannot_publish_partial_file(self):
        async with Client(self.app) as client:
            preview = await self.preview(client)
            with patch("learning_lab_mcp.drafts.os.fsync", side_effect=OSError("disk failure")):
                self.assert_error(await self.save(client, preview), "SAVE_FAILED")
        self.assertEqual([], list(self.root.iterdir()))

    async def test_file_appearing_during_publication_is_not_overwritten(self):
        def collide(source, destination):
            Path(destination).write_bytes(b"another draft")
            raise FileExistsError("occupied")
        async with Client(self.app) as client:
            preview = await self.preview(client)
            with patch("learning_lab_mcp.drafts.os.link", side_effect=collide):
                self.assert_error(await self.save(client, preview), "DRAFT_CONFLICT")
        self.assertEqual(b"another draft", (self.root / "purchase-001.json").read_bytes())
        self.assertEqual(1, len(list(self.root.iterdir())))

    async def test_cleanup_failure_reports_committed_draft_without_hiding_cleanup(self):
        unlink = Path.unlink
        def fail_pending(path, *args, **kwargs):
            if path.name.startswith(".pending-"):
                raise PermissionError("cleanup failure")
            return unlink(path, *args, **kwargs)
        async with Client(self.app) as client:
            preview = await self.preview(client)
            with patch.object(Path, "unlink", fail_pending):
                result = await self.save(client, preview)
        self.assertFalse(result.is_error, result)
        self.assertTrue(result.structured_content["cleanup_pending"])
        document = json.loads((self.root / "purchase-001.json").read_text(encoding="utf-8"))
        self.assertEqual(8500, document["quote"]["total_krw"])

    def test_symlinked_root_is_rejected(self):
        outside = Path(self.temporary.name) / "outside"
        outside.mkdir()
        try:
            self.root.symlink_to(outside, target_is_directory=True)
        except OSError as error:
            self.skipTest(f"Symlink creation unavailable: {error}")
        quote = review_purchase([PurchaseItem(product_id="NOTE-01", quantity=1)], 8000)
        with self.assertRaisesRegex(DraftError, "UNSAFE_STORAGE_PATH"):
            DraftStore(self.root).preview("purchase-001", quote)
        self.assertEqual([], list(outside.iterdir()))

    async def test_new_store_requires_new_preview_and_can_recognize_identical_saved_content(self):
        async with Client(self.app) as client:
            old = await self.preview(client)
            await self.save(client, old)
        async with Client(create_inspector_server(self.root)) as client:
            self.assert_error(await self.save(client, old), "INVALID_PREVIEW")
            fresh = await self.preview(client)
            result = await self.save(client, fresh)
        self.assertFalse(result.is_error, result)
        self.assertEqual("already_saved", result.structured_content["status"])


class DraftStdioTests(unittest.IsolatedAsyncioTestCase):
    async def test_default_entrypoint_has_only_catalog_tools(self):
        parameters = StdioServerParameters(
            command=sys.executable,
            args=["-B", "-m", "learning_lab_mcp.server"],
        )
        async with Client(stdio_client(parameters), read_timeout_seconds=15) as client:
            names = {tool.name for tool in (await client.list_tools()).tools}
            result = await client.call_tool("save_purchase_draft", {"preview_id": "invented"})
            quote = await client.call_tool("review_purchase", {
                "items": [{"product_id": "NOTE-01", "quantity": 2},
                          {"product_id": "PEN-02", "quantity": 1}],
                "budget_krw": 8000,
            })
        self.assertEqual({"get_product", "find_products", "review_purchase"}, names)
        self.assertTrue(result.is_error)
        self.assertFalse(quote.is_error)
        self.assertEqual(8500, quote.structured_content["total_krw"])

    async def test_inspector_factory_over_stdio_saves_retries_and_detects_conflict(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "drafts"
            parameters = StdioServerParameters(
                command=sys.executable,
                args=["-B", "-c",
                      "import sys; from pathlib import Path; "
                      "from learning_lab_mcp.inspector import create_inspector_server; "
                      "create_inspector_server(Path(sys.argv[1])).run()",
                      str(root)],
            )
            arguments = {
                "request_id": "stdio-001",
                "items": [{"product_id": "NOTE-01", "quantity": 2},
                          {"product_id": "PEN-02", "quantity": 1}],
                "budget_krw": 8000,
            }
            async with Client(stdio_client(parameters), read_timeout_seconds=15) as client:
                names = {tool.name for tool in (await client.list_tools()).tools}
                self.assertIn("save_purchase_draft", names)
                preview = await client.call_tool("preview_purchase_draft", arguments)
                self.assertFalse(preview.is_error, preview)
                self.assertFalse(root.exists())
                token = {"preview_id": preview.structured_content["preview_id"]}
                saved = await client.call_tool("save_purchase_draft", token)
                self.assertFalse(saved.is_error, saved)
                retry = await client.call_tool("save_purchase_draft", token)
                self.assertEqual("already_saved", retry.structured_content["status"])
                original = (root / "stdio-001.json").read_bytes()
                changed = await client.call_tool(
                    "preview_purchase_draft", arguments | {"budget_krw": 9000},
                )
                conflict = await client.call_tool("save_purchase_draft", {
                    "preview_id": changed.structured_content["preview_id"],
                })
                self.assertTrue(conflict.is_error)
                self.assertEqual(original, (root / "stdio-001.json").read_bytes())
            document = json.loads(original)
            self.assertEqual(8500, document["quote"]["total_krw"])
            self.assertEqual(500, document["quote"]["over_budget_krw"])
            self.assertEqual(["stdio-001.json"], [path.name for path in root.iterdir()])


if __name__ == "__main__":
    unittest.main()
