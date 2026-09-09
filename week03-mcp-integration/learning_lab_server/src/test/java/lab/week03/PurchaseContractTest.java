package lab.week03;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static lab.week03.Json.*;
import static lab.week03.DraftStore.*;

class PurchaseContractTest {
    final CatalogService catalog=new CatalogService();
    @Test void productAndStockAreSeparateFromSuccess() {
        assertEquals(3500,catalog.getProduct("NOTE-01").price_krw());
        assertFalse(catalog.getProduct("PEN-02").in_stock());
        assertThrows(IllegalArgumentException.class,()->catalog.getProduct("UNKNOWN"));
    }
    @ParameterizedTest @CsvSource({"3500,true,1","3500,false,2","1500,true,0","1500,false,1","1499,false,0","0,false,0"})
    void inclusiveFilterAndEmptyResults(long maximum,boolean stock,int count) {
        assertEquals(count,catalog.findProducts(maximum,stock).size());
    }
    @Test void productsAndResultsCannotMutateCatalog() {
        assertThrows(UnsupportedOperationException.class,()->catalog.findProducts(9000,false).clear());
        assertEquals(3500,catalog.getProduct("NOTE-01").price_krw());
    }
    static CatalogService.PurchaseReview quote(long quantity) {
        return new CatalogService().reviewPurchase(List.of(new CatalogService.PurchaseItem("NOTE-01",quantity),new CatalogService.PurchaseItem("PEN-02",1)),8000);
    }
    @Test void purchaseContainsPricesStockAndBudgetExcess() {
        var review=quote(2);
        assertEquals(8500,review.total_krw()); assertEquals(500,review.over_budget_krw());
        assertEquals(List.of("PEN-02"),review.out_of_stock_product_ids());
        assertEquals(7000,review.items().get(0).subtotal_krw());
        assertFalse(review.items().get(1).in_stock());
    }
    @ParameterizedTest @CsvSource({"9000,false,0","8500,false,0","8000,true,500","0,true,8500"})
    void budgetsTreatEqualityAsAllowed(long budget,boolean over,long excess) {
        var review=catalog.reviewPurchase(List.of(new CatalogService.PurchaseItem("NOTE-01",2),new CatalogService.PurchaseItem("PEN-02",1)),budget);
        assertEquals(over,review.over_budget()); assertEquals(excess,review.over_budget_krw());
    }
    @Test void repeatedIdsRemainSeparateButUnavailableIdsAreUnique() {
        var review=catalog.reviewPurchase(List.of(new CatalogService.PurchaseItem("PEN-02",2),new CatalogService.PurchaseItem("NOTE-01",1),new CatalogService.PurchaseItem("PEN-02",1)),9000);
        assertEquals(3,review.items().size()); assertEquals(List.of("PEN-02"),review.out_of_stock_product_ids());
        assertEquals(8000,review.total_krw());
    }
    @Test void unknownIdsFailWholeRequestInEitherPosition() {
        for(var items:List.of(List.of(new CatalogService.PurchaseItem("UNKNOWN",1),new CatalogService.PurchaseItem("NOTE-01",1)),
                List.of(new CatalogService.PurchaseItem("NOTE-01",1),new CatalogService.PurchaseItem("UNKNOWN",1))))
            assertThrows(IllegalArgumentException.class,()->catalog.reviewPurchase(items,8000));
    }
    static Stream<Object> invalidIntegers() { return Stream.of("8000",8000.0,true,false,null,-1,new java.math.BigInteger("999999999999999999999")); }
    @ParameterizedTest @MethodSource("invalidIntegers")
    void externalAmountsAreNotCoerced(Object value) {
        assertThrows(IllegalArgumentException.class,()->integer(object("budget_krw",value),"budget_krw",0));
    }
    @Test void invalidItemStructuresAndFieldsAreRejected() {
        for(var input:List.of(object("items",List.of()),object("items","wrong"),object("items",List.of(object("product_id","NOTE-01","quantity",0))),
                object("items",List.of(object("product_id","NOTE-01","quantity",1,"price_krw",1))),object("items",List.of(object("quantity",1))),
                object("items",List.of(object("product_id",1,"quantity",1)))))
            assertThrows(IllegalArgumentException.class,()->InspectorServer.items(input));
    }
    @Test void arithmeticOverflowFailsInsteadOfReturningCorruptMoney() {
        assertThrows(ArithmeticException.class,()->catalog.reviewPurchase(List.of(new CatalogService.PurchaseItem("NOTE-01",Long.MAX_VALUE)),0));
    }
}
