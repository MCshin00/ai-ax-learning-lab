"""Inspector-only entry point for previewing and saving purchase drafts."""
from pathlib import Path
from typing import Annotated

from mcp.server import MCPServer
from mcp.types import ToolAnnotations
from pydantic import Field

from .changes import ChangePreview, ChangeResult, DraftChanges
from .drafts import DEFAULT_DRAFT_ROOT, DraftPreview, DraftSaved, DraftStore
from .server import PurchaseItem, create_catalog_server, review_purchase


def create_inspector_server(root: Path = DEFAULT_DRAFT_ROOT) -> MCPServer:
    app = create_catalog_server("learning-catalog-inspector")
    store = DraftStore(root)

    @app.tool(
        annotations=ToolAnnotations(
            readOnlyHint=True, destructiveHint=False,
            idempotentHint=False, openWorldHint=False,
        ),
        structured_output=True,
    )
    def preview_purchase_draft(
        request_id: Annotated[str, Field(
            strict=True, description="Stable lowercase request ID, e.g. purchase-001. Not a path.",
        )],
        items: Annotated[list[PurchaseItem], Field(min_length=1)],
        budget_krw: Annotated[int, Field(ge=0, strict=True)],
    ) -> DraftPreview:
        """Preview the exact quote and destination without creating a draft file.

        The human operator must review this output in Inspector before saving.
        Another preview for the same request ID invalidates its previous preview.
        Prices are fixed at preview time. Unknown products fail the entire preview.
        """
        quote = review_purchase(items, budget_krw)
        return store.preview(request_id, quote)

    @app.tool(
        annotations=ToolAnnotations(
            readOnlyHint=False, destructiveHint=False,
            idempotentHint=True, openWorldHint=False,
        ),
        structured_output=True,
    )
    def save_purchase_draft(
        preview_id: Annotated[str, Field(
            strict=True, description="ID of the preview the human just reviewed in Inspector.",
        )],
    ) -> DraftSaved:
        """Save the reviewed snapshot as a new local JSON draft.

        Invoke directly in Inspector only after reviewing the preview's contents
        and path. A preview ID identifies content; it is not proof of approval.
        Matching retries reuse the same file. Different or edited files conflict.
        """
        return store.save(preview_id)


    changes = DraftChanges(store)

    @app.tool(
        annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False,
                                    idempotentHint=False, openWorldHint=False),
        structured_output=True,
    )
    def preview_draft_edit(
        draft_id: Annotated[str, Field(strict=True, description="ID of the existing draft.")],
        operation_id: Annotated[str, Field(strict=True, description="Stable edit request ID, e.g. edit-001.")],
        items: Annotated[list[PurchaseItem], Field(min_length=1)],
        budget_krw: Annotated[int, Field(ge=0, strict=True)],
    ) -> ChangePreview:
        """Show before/after contents without writing files. Read both before applying.

        Preserve extra draft fields, including manual notes. A new preview for
        this draft invalidates its previous edit/undo preview. Same operation ID
        must keep the same content. Prices are fixed at preview time.
        """
        return changes.preview_edit(draft_id, operation_id, review_purchase(items, budget_krw))

    @app.tool(
        annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=True,
                                    idempotentHint=True, openWorldHint=False),
        structured_output=True,
    )
    def apply_draft_change(
        preview_id: Annotated[str, Field(strict=True)],
    ) -> ChangeResult:
        """Apply an edit or undo only after the human reviews the Inspector preview.

        Compare file version, journal the exact change, then replace the file.
        This modifies an existing local draft. A preview ID is not proof of
        approval. A repeated operation returns its recorded result without
        replaying it over later edits.
        """
        return changes.apply(preview_id)

    @app.tool(
        annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False,
                                    idempotentHint=True, openWorldHint=False),
        structured_output=True,
    )
    def get_draft_operation(
        operation_id: Annotated[str, Field(strict=True)],
    ) -> ChangeResult:
        """Read a durable operation result after a lost response or restart.

        'applied' describes historical completion. current_matches says whether
        that operation still describes the current file. A prepared operation
        needs an explicit resume call; this read never publishes a file.
        """
        return changes.get_operation(operation_id)

    @app.tool(
        annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=True,
                                    idempotentHint=True, openWorldHint=False),
        structured_output=True,
    )
    def resume_draft_operation(
        operation_id: Annotated[str, Field(strict=True)],
    ) -> ChangeResult:
        """Resume an already journaled save after inspecting its status.

        Completes only the exact before/after change from the earlier apply.
        Unknown IDs cannot authorize a new change. A conflicting file is kept.
        Applied operations return their recorded result without rewriting.
        """
        return changes.resume(operation_id)

    @app.tool(
        annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False,
                                    idempotentHint=False, openWorldHint=False),
        structured_output=True,
    )
    def preview_draft_undo(
        target_operation_id: Annotated[str, Field(strict=True, description="Applied edit to undo.")],
        operation_id: Annotated[str, Field(strict=True, description="Stable cancellation ID, e.g. undo-001.")],
    ) -> ChangePreview:
        """Preview restoring the exact file from before the latest applied edit.

        Refuse if a later operation or external file change exists. The human
        must review this restoration before applying its preview ID. Undo
        restores one edit, not the entire draft's history; it does not delete it.
        """
        return changes.preview_undo(target_operation_id, operation_id)

    @app.tool(
        annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False,
                                    idempotentHint=True, openWorldHint=False),
        structured_output=True,
    )
    def get_draft_history(
        draft_id: Annotated[str, Field(strict=True)],
    ) -> list[ChangeResult]:
        """List durable edit and undo results in order, without modifying files."""
        return changes.history(draft_id)

    return app


def main() -> None:
    create_inspector_server().run()


if __name__ == "__main__":
    main()
