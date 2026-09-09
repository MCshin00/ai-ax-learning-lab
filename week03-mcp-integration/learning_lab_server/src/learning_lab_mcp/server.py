"""Working read-only MCP example: fictional data, no files or external services."""
from typing import Annotated

from mcp.server import MCPServer
from mcp.types import ToolAnnotations
from pydantic import BaseModel, ConfigDict, Field

class Product(BaseModel):
    product_id: str
    name: str
    price_krw: int
    in_stock: bool


class PurchaseItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    product_id: str = Field(strict=True, description="Exact catalog product ID.")
    quantity: int = Field(ge=1, strict=True, description="Quantity; a positive integer.")


class PurchaseLine(Product):
    """Catalog product with its current unit price, requested quantity and subtotal."""

    quantity: int
    subtotal_krw: int


class PurchaseReview(BaseModel):
    items: list[PurchaseLine]
    budget_krw: int
    total_krw: int
    over_budget: bool
    over_budget_krw: int
    out_of_stock_product_ids: list[str]


CATALOG = {
    "NOTE-01": Product(product_id="NOTE-01", name="연습용 노트", price_krw=3500, in_stock=True),
    "PEN-02": Product(product_id="PEN-02", name="연습용 펜", price_krw=1500, in_stock=False),
}


def get_product(product_id: str) -> Product:
    """Look up a fictional product by exact ID: NOTE-01 or PEN-02. No purchase occurs."""
    if product_id not in CATALOG:
        raise ValueError("Unknown product ID. Available: NOTE-01, PEN-02.")
    return CATALOG[product_id].model_copy()


def find_products(
    max_price_krw: Annotated[int, Field(
        ge=0, strict=True, description="Maximum price in KRW, inclusive; a non-negative integer.")],
    in_stock_only: Annotated[bool, Field(
        strict=True, description="If true, exclude out-of-stock products. Defaults to true.")] = True,
) -> list[Product]:
    """Find fictional products at or below the budget, optionally requiring stock.

    Return matching products in catalog order, or an empty list if none match.
    No purchase or inventory change occurs.
    """
    return [
        product.model_copy()
        for product in CATALOG.values()
        if product.price_krw <= max_price_krw
        and (not in_stock_only or product.in_stock)
    ]


def review_purchase(
    items: Annotated[list[PurchaseItem], Field(
        min_length=1, description="Non-empty list of product IDs and positive integer quantities.")],
    budget_krw: Annotated[int, Field(
        ge=0, strict=True, description="Budget in KRW; a non-negative integer.")],
) -> PurchaseReview:
    """Estimate a purchase using current catalog prices, including out-of-stock items.

    Return each item's unit price (price_krw), quantity, subtotal and stock status,
    the total, whether it exceeds the budget, the excess amount and out-of-stock IDs.
    Equality with the budget is not an excess. Any unknown ID fails the entire
    request without a partial estimate. Repeated IDs remain separate input lines;
    out-of-stock IDs appear once in input order. No order or inventory change occurs.
    """
    unknown_ids = list(dict.fromkeys(
        item.product_id for item in items if item.product_id not in CATALOG
    ))
    if unknown_ids:
        raise ValueError(
            f"Unknown product ID(s): {', '.join(unknown_ids)}. No purchase estimate was calculated."
        )

    lines = []
    for item in items:
        product = CATALOG[item.product_id]
        lines.append(PurchaseLine(
            **product.model_dump(),
            quantity=item.quantity,
            subtotal_krw=product.price_krw * item.quantity,
        ))

    total_krw = sum(line.subtotal_krw for line in lines)
    return PurchaseReview(
        items=lines,
        budget_krw=budget_krw,
        total_krw=total_krw,
        over_budget=total_krw > budget_krw,
        over_budget_krw=max(total_krw - budget_krw, 0),
        out_of_stock_product_ids=list(dict.fromkeys(
            line.product_id for line in lines if not line.in_stock
        )),
    )


def catalog_help() -> str:
    return "연습용 고정 자료입니다. NOTE-01, PEN-02만 조회할 수 있고 주문은 지원하지 않습니다."


def explain_product(product_id: str) -> str:
    """Prepare a request to describe one product using the catalog."""
    if product_id not in CATALOG:
        raise ValueError("Unknown product ID. Available: NOTE-01, PEN-02.")
    return f"get_product로 {product_id}를 조회하고 가격과 재고를 설명하세요. 주문하지 마세요."


def create_catalog_server(name: str = "learning-catalog") -> MCPServer:
    """Register the shared catalog features for the selected entry point."""
    app = MCPServer(name, instructions="Fictional product catalog for local practice.")
    for function in (get_product, find_products, review_purchase):
        app.tool(
            annotations=ToolAnnotations(
                readOnlyHint=True, destructiveHint=False,
                idempotentHint=True, openWorldHint=False,
            ),
            structured_output=True,
        )(function)
    app.resource("catalog://help", mime_type="text/plain")(catalog_help)
    app.prompt()(explain_product)
    return app


mcp = create_catalog_server()


def main() -> None:
    mcp.run()


if __name__ == "__main__":
    main()
