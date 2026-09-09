package lab.week03;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import java.nio.file.Path;
import java.util.*;
import static lab.week03.Json.*;
import static lab.week03.CatalogServer.*;
import static lab.week03.DraftStore.*;

/** Inspector entry point exposes writes only where a person reviews and invokes them. */
public final class InspectorServer {
    private InspectorServer() {}
    static final Map<String,Object> POSITIVE=Map.of("type","integer","minimum",1,"maximum",Long.MAX_VALUE);
    static final Map<String,Object> NONNEGATIVE=Map.of("type","integer","minimum",0,"maximum",Long.MAX_VALUE);
    static final Map<String,Object> ITEMS=Map.of("type","array","minItems",1,"items",schema(Json.object("product_id",STRING,"quantity",POSITIVE),"product_id","quantity"));
    static final Map<String,Object> BOOLEAN=Map.of("type","boolean");
    static Map<String,Object> choices(String... values) { return object("type","string","enum",List.of(values)); }
    static Map<String,Object> array(Map<String,Object> item) { return object("type","array","items",item); }
    static Map<String,Object> listSchema(Map<String,Object> item) { return schema(Map.of("result",array(item)),"result"); }
    static final Map<String,Object> LINE_SCHEMA=schema(object("product_id",STRING,"name",STRING,"price_krw",NONNEGATIVE,
        "in_stock",BOOLEAN,"quantity",POSITIVE,"subtotal_krw",NONNEGATIVE),"product_id","name","price_krw","in_stock","quantity","subtotal_krw");
    static final Map<String,Object> REVIEW_SCHEMA=schema(object("items",array(LINE_SCHEMA),"budget_krw",NONNEGATIVE,"total_krw",NONNEGATIVE,
        "over_budget",BOOLEAN,"over_budget_krw",NONNEGATIVE,"out_of_stock_product_ids",array(STRING)),
        "items","budget_krw","total_krw","over_budget","over_budget_krw","out_of_stock_product_ids");
    static final Map<String,Object> PREVIEW_SCHEMA=schema(object("preview_id",STRING,"request_id",STRING,"path",STRING,
        "pricing_policy",choices("preview_snapshot"),"estimate_complete",Map.of("const",true),"quote",REVIEW_SCHEMA),
        "preview_id","request_id","path","pricing_policy","estimate_complete","quote");
    static final Map<String,Object> SAVED_SCHEMA=schema(object("status",choices("saved","already_saved"),"request_id",STRING,"path",STRING,
        "total_krw",NONNEGATIVE,"cleanup_pending",BOOLEAN),"status","request_id","path","total_krw","cleanup_pending");
    static final Map<String,Object> NULLABLE_ID=Map.of("type",List.of("string","null"));
    static final Map<String,Object> CHANGE_PREVIEW_SCHEMA=schema(object("preview_id",STRING,"operation_id",STRING,"draft_id",STRING,
        "action",choices("edit","undo"),"target_operation_id",NULLABLE_ID,"path",STRING,"base_version",STRING,
        "before",Map.of("type","object"),"after",Map.of("type","object"),"operation_state",choices("new","prepared","applied","conflict")),
        "preview_id","operation_id","draft_id","action","target_operation_id","path","base_version","before","after","operation_state");
    static final Map<String,Object> CHANGE_SCHEMA=schema(object("operation_id",STRING,"draft_id",STRING,"action",choices("edit","undo"),
        "target_operation_id",NULLABLE_ID,"status",choices("prepared","applied","already_applied","conflict"),"path",STRING,
        "total_krw",NONNEGATIVE,"current_matches",BOOLEAN,"cleanup_pending",BOOLEAN),
        "operation_id","draft_id","action","target_operation_id","status","path","total_krw","current_matches","cleanup_pending");
    static List<CatalogService.PurchaseItem> items(Map<String,Object> input) {
        if(!(input.get("items") instanceof List<?> list) || list.isEmpty()) throw failure("INVALID_INPUT: items must be a non-empty list.");
        var items=new ArrayList<CatalogService.PurchaseItem>();
        for(Object value:list) {
            var item=map(value); keys(item,"product_id","quantity");
            items.add(new CatalogService.PurchaseItem(string(item,"product_id"),integer(item,"quantity",1)));
        }
        return items;
    }
    static Map<String,Object> resultList(Object result) { return object("result",result); }
    static List<SyncToolSpecification> extraCatalogTools(CatalogService catalog) {
        return List.of(
            tool("find_products","Find fictional products at or below the budget in catalog order; optionally require stock. Empty matches return an empty result. No purchase occurs.",
                schema(object("max_price_krw",NONNEGATIVE,"in_stock_only",object("type","boolean","default",true)),"max_price_krw"),listSchema(PRODUCT_SCHEMA),true,false,true,input->{
                    keys(input,"max_price_krw","in_stock_only");
                    Object stock=input.getOrDefault("in_stock_only",true);
                    if(!(stock instanceof Boolean)) throw failure("INVALID_INPUT: in_stock_only must be boolean.");
                    return resultList(catalog.findProducts(integer(input,"max_price_krw",0),(Boolean)stock));
                }),
            tool("review_purchase","Estimate every requested line at catalog prices including out-of-stock items. Unknown IDs fail the entire request. Repeated IDs stay separate; stock IDs appear once. Budget equality is allowed. No order occurs.",
                schema(object("items",ITEMS,"budget_krw",NONNEGATIVE),"items","budget_krw"),REVIEW_SCHEMA,true,false,true,input->{
                    keys(input,"items","budget_krw");
                    return catalog.reviewPurchase(items(input),integer(input,"budget_krw",0));
                }));
    }
    static List<SyncToolSpecification> draftTools(CatalogService catalog,DraftStore store) {
        var changes=new DraftChanges(store);
        return List.of(
            tool("preview_purchase_draft","Show the exact complete quote and destination without writing a file. The human must review before saving. A new preview replaces the previous preview for this request; prices are fixed at preview time.",
                schema(object("request_id",STRING,"items",ITEMS,"budget_krw",NONNEGATIVE),"request_id","items","budget_krw"),PREVIEW_SCHEMA,true,false,false,input->{
                    keys(input,"request_id","items","budget_krw");
                    return store.preview(string(input,"request_id"),catalog.reviewPurchase(items(input),integer(input,"budget_krw",0)));
                }),
            tool("save_purchase_draft","Invoke directly in Inspector only after human review. Save a new local draft; matching retries reuse it and different/edited files conflict. A preview ID identifies content, not approval.",
                schema(Map.of("preview_id",STRING),"preview_id"),SAVED_SCHEMA,false,false,true,input->{
                    keys(input,"preview_id"); return store.save(string(input,"preview_id"));
                }),
            tool("preview_draft_edit","Show before/after without writing; preserve notes and other fields. Review before applying. Same operation ID must identify the same edit; new preview invalidates the draft's previous edit/undo preview.",
                schema(object("draft_id",STRING,"operation_id",STRING,"items",ITEMS,"budget_krw",NONNEGATIVE),"draft_id","operation_id","items","budget_krw"),CHANGE_PREVIEW_SCHEMA,true,false,false,input->{
                    keys(input,"draft_id","operation_id","items","budget_krw");
                    return changes.previewEdit(string(input,"draft_id"),string(input,"operation_id"),catalog.reviewPurchase(items(input),integer(input,"budget_krw",0)));
                }),
            tool("apply_draft_change","Apply only after human review in Inspector. Compare file version, journal the exact edit/undo, then replace. Repeated operations return history without replay over later edits; preview ID is not approval.",
                schema(Map.of("preview_id",STRING),"preview_id"),CHANGE_SCHEMA,false,true,true,input->{
                    keys(input,"preview_id");return changes.apply(string(input,"preview_id"));
                }),
            tool("get_draft_operation","Read durable status after restart or lost response. Applied is historical; current_matches describes the current file. Prepared requires explicit resume; this read never publishes.",
                schema(Map.of("operation_id",STRING),"operation_id"),CHANGE_SCHEMA,true,false,true,input->{
                    keys(input,"operation_id");return changes.getOperation(string(input,"operation_id"));
                }),
            tool("resume_draft_operation","Resume only an already journaled request after inspecting its status. Unknown IDs cannot create changes. Conflicting files are preserved; applied operations are not rewritten.",
                schema(Map.of("operation_id",STRING),"operation_id"),CHANGE_SCHEMA,false,true,true,input->{
                    keys(input,"operation_id");return changes.resume(string(input,"operation_id"));
                }),
            tool("preview_draft_undo","Preview exact restoration before the latest applied edit. Refuse later operations or external file changes. Human review is required before apply. This restores one edit and does not delete the draft.",
                schema(object("target_operation_id",STRING,"operation_id",STRING),"target_operation_id","operation_id"),CHANGE_PREVIEW_SCHEMA,true,false,false,input->{
                    keys(input,"target_operation_id","operation_id");return changes.previewUndo(string(input,"target_operation_id"),string(input,"operation_id"));
                }),
            tool("get_draft_history","Read durable edit and undo results in order without modifying files.",
                schema(Map.of("draft_id",STRING),"draft_id"),listSchema(CHANGE_SCHEMA),true,false,true,input->{
                    keys(input,"draft_id");return resultList(changes.history(string(input,"draft_id")));
                })
        );
    }
    public static McpSyncServer create(StdioServerTransportProvider transport,Path root) {
        var catalog=new CatalogService();
        var tools=new ArrayList<>(CatalogServer.catalogTools(catalog));
        tools.addAll(draftTools(catalog,new DraftStore(root)));
        return CatalogServer.create(transport,catalog,tools);
    }
    public static void main(String[] args) {
        // The launcher selects the storage root; tool inputs supply IDs, not paths.
        Path root=args.length==0?Path.of("..",".local","drafts-java"):Path.of(args[0]);
        create(new StdioServerTransportProvider(Json.MAPPER),root);
    }
}
