"""Working read-only MCP example: fictional data, no files or external services."""
from mcp.server import MCPServer
from mcp.types import ToolAnnotations
from pydantic import BaseModel

mcp = MCPServer("learning-catalog", instructions="Fictional product catalog for local practice.")


class Product(BaseModel):
    product_id: str
    name: str
    price_krw: int
    in_stock: bool


CATALOG = {
    "NOTE-01": Product(product_id="NOTE-01", name="연습용 노트", price_krw=3000, in_stock=True),
    "PEN-02": Product(product_id="PEN-02", name="연습용 펜", price_krw=1500, in_stock=False),
}


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False,
                                    idempotentHint=True, openWorldHint=False),
          structured_output=True)
def get_product(product_id: str) -> Product:
    """Look up a fictional product by exact ID: NOTE-01 or PEN-02. No purchase occurs."""
    if product_id not in CATALOG:
        raise ValueError("Unknown product ID. Available: NOTE-01, PEN-02.")
    return CATALOG[product_id].model_copy()


@mcp.resource("catalog://help", mime_type="text/plain")
def catalog_help() -> str:
    return "연습용 고정 자료입니다. NOTE-01, PEN-02만 조회할 수 있고 주문은 지원하지 않습니다."


@mcp.prompt()
def explain_product(product_id: str) -> str:
    """Prepare a request to describe one product using the catalog."""
    if product_id not in CATALOG:
        raise ValueError("Unknown product ID. Available: NOTE-01, PEN-02.")
    return f"get_product로 {product_id}를 조회하고 가격과 재고를 설명하세요. 주문하지 마세요."


def main() -> None:
    mcp.run()


if __name__ == "__main__":
    main()
