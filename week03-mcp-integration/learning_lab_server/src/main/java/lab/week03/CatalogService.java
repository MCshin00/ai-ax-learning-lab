package lab.week03;

import java.util.*;

/** Catalog validation and purchase calculations independent of MCP transport. */
public final class CatalogService {
    public record Product(String product_id, String name, long price_krw, boolean in_stock) {}
    public record PurchaseItem(String product_id, long quantity) {
        public PurchaseItem {
            if (product_id == null || quantity < 1) throw new IllegalArgumentException("INVALID_INPUT: a product ID and positive integer quantity are required.");
        }
    }
    public record PurchaseLine(String product_id, String name, long price_krw, boolean in_stock, long quantity, long subtotal_krw) {}
    public record PurchaseReview(List<PurchaseLine> items, long budget_krw, long total_krw, boolean over_budget,
            long over_budget_krw, List<String> out_of_stock_product_ids) {
        public PurchaseReview { items = List.copyOf(items); out_of_stock_product_ids = List.copyOf(out_of_stock_product_ids); }
    }
    private final Map<String, Product> catalog = new LinkedHashMap<>();
    public CatalogService() {
        catalog.put("NOTE-01", new Product("NOTE-01", "연습용 노트", 3500, true));
        catalog.put("PEN-02", new Product("PEN-02", "연습용 펜", 1500, false));
    }
    public Product getProduct(String id) {
        Product product = catalog.get(id);
        if (product == null) throw new IllegalArgumentException("Unknown product ID. Available: NOTE-01, PEN-02.");
        return product;
    }
    public List<Product> findProducts(long maximum, boolean inStockOnly) {
        if (maximum < 0) throw new IllegalArgumentException("INVALID_INPUT: maximum price must be non-negative.");
        return catalog.values().stream().filter(p -> p.price_krw() <= maximum && (!inStockOnly || p.in_stock())).toList();
    }
    public PurchaseReview reviewPurchase(List<PurchaseItem> items, long budget) {
        if (budget < 0 || items == null || items.isEmpty()) throw new IllegalArgumentException("INVALID_INPUT: non-empty items and non-negative budget are required.");
        var unknown = new LinkedHashSet<String>();
        for (var item : items) if (!catalog.containsKey(item.product_id())) unknown.add(item.product_id());
        if (!unknown.isEmpty()) throw new IllegalArgumentException("Unknown product ID(s): " + String.join(", ", unknown) + ". No purchase estimate was calculated.");
        var lines = new ArrayList<PurchaseLine>();
        var unavailable = new LinkedHashSet<String>();
        long total = 0;
        for (var item : items) {
            var p = getProduct(item.product_id());
            long subtotal = Math.multiplyExact(p.price_krw(), item.quantity());
            total = Math.addExact(total, subtotal);
            lines.add(new PurchaseLine(p.product_id(), p.name(), p.price_krw(), p.in_stock(), item.quantity(), subtotal));
            if (!p.in_stock()) unavailable.add(p.product_id());
        }
        return new PurchaseReview(lines, budget, total, total > budget, Math.max(total - budget, 0), new ArrayList<>(unavailable));
    }
    public String catalogHelp() { return "연습용 고정 자료입니다. NOTE-01, PEN-02만 조회할 수 있고 주문은 지원하지 않습니다."; }
    public String explainProduct(String id) { getProduct(id); return "get_product로 " + id + "를 조회하고 가격과 재고를 설명하세요. 주문하지 마세요."; }
}
