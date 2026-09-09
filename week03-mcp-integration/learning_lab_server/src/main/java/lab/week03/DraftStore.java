package lab.week03;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;
import static lab.week03.Json.*;

/** One writer process; previews are snapshots, not evidence of human approval. */
public class DraftStore {
    final Path root;
    final Object lock = new Object();
    private record Snapshot(String requestId, byte[] payload, long total) {}
    private final Map<String, Snapshot> previews = new HashMap<>();
    private final Map<String, String> latest = new HashMap<>();

    public DraftStore(Path root) { this.root = root.toAbsolutePath().normalize(); }
    static IllegalArgumentException failure(String message) { return new IllegalArgumentException(message); }
    static String token() { return UUID.randomUUID().toString().replace("-", ""); }
    static BasicFileAttributes checked(Path path) throws IOException {
        if (!Files.exists(path, NOFOLLOW_LINKS)) return null;
        var info = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS);
        // Windows junctions are reparse points represented as "other" entries by NIO.
        if (info.isSymbolicLink() || info.isOther() || FileIdentity.isReparsePoint(path)) throw failure("UNSAFE_STORAGE_PATH: links and junctions are not allowed.");
        return info;
    }
    void checkRoot() throws IOException {
        for (Path p = root; p != null; p = p.getParent()) {
            var info = checked(p);
            if (info != null && !info.isDirectory()) throw failure("UNSAFE_STORAGE_PATH: storage root must be a directory.");
        }
    }
    Path destination(String id) throws IOException {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,63}") || id.matches("con|prn|aux|nul|com[1-9]|lpt[1-9]"))
            throw failure("INVALID_REQUEST_ID: use 1-64 lowercase letters, digits, '-' or '_'; avoid device names.");
        checkRoot();
        return root.resolve(id + ".json");
    }
    static byte[] encode(Object document) {
        return (Json.text(canonical(document)) + "\n").getBytes(StandardCharsets.UTF_8);
    }
    private static Object canonical(Object value) {
        if (value instanceof Map<?,?> map) {
            var sorted = new TreeMap<String,Object>();
            map.forEach((k,v) -> sorted.put((String)k, canonical(v)));
            return sorted;
        }
        if (value instanceof List<?> list) return list.stream().map(DraftStore::canonical).toList();
        return value;
    }
    static Map<String,Object> quote(CatalogService.PurchaseReview quote) { return Json.parse(encode(quote)); }
    static Map<String,Object> document(byte[] raw, String id) {
        try {
            var document = Json.parse(raw);
            if (!Objects.equals(document.get("request_id"), id) || !Objects.equals(document.get("pricing_policy"), "preview_snapshot")
                || !Boolean.TRUE.equals(document.get("estimate_complete")) || integer(document,"schema_version",1) != 1)
                throw failure("unsupported document");
            var q = map(document.get("quote"));
            long budget = integer(q, "budget_krw", 0), total = integer(q,"total_krw",0);
            if (!(q.get("over_budget") instanceof Boolean) || !(q.get("items") instanceof List<?> lines)
                || !(q.get("out_of_stock_product_ids") instanceof List<?> ids)) throw failure("invalid quote");
            integer(q,"over_budget_krw",0);
            for (Object line : lines) {
                var item = map(line);
                string(item,"product_id"); string(item,"name");
                integer(item,"price_krw",0); integer(item,"quantity",1); integer(item,"subtotal_krw",0);
                if (!(item.get("in_stock") instanceof Boolean)) throw failure("invalid stock");
            }
            for (Object product : ids) if (!(product instanceof String)) throw failure("invalid IDs");
            return document;
        } catch (RuntimeException error) { throw failure("INVALID_DRAFT: expected a complete purchase draft with the matching ID."); }
    }
    @SuppressWarnings("unchecked")
    static Map<String,Object> map(Object value) {
        if (!(value instanceof Map<?,?>)) throw failure("INVALID_INPUT: expected an object.");
        return (Map<String,Object>)value;
    }
    static long integer(Map<String,Object> input, String key, long minimum) {
        Object value = input.get(key);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof java.math.BigInteger))
            throw failure("INVALID_INPUT: " + key + " must be an integer.");
        long number;
        try { number = value instanceof java.math.BigInteger big ? big.longValueExact() : ((Number)value).longValue(); }
        catch (ArithmeticException error) { throw failure("INVALID_INPUT: " + key + " is outside the supported integer range."); }
        if (number < minimum) throw failure("INVALID_INPUT: " + key + " is below its minimum.");
        return number;
    }
    public Map<String,Object> preview(String id, CatalogService.PurchaseReview quote) {
        synchronized (lock) {
            try {
                Path path = destination(id);
                byte[] payload = encode(object("schema_version",1, "request_id",id, "pricing_policy","preview_snapshot", "estimate_complete",true,"quote",quote(quote)));
                String token = token(), previous = latest.put(id,token);
                if (previous != null) previews.remove(previous);
                previews.put(token,new Snapshot(id,payload,quote.total_krw()));
                return object("preview_id",token,"request_id",id,"path",path.toString(),"pricing_policy","preview_snapshot","estimate_complete",true,"quote",quote(quote));
            } catch (IOException error) { throw failure("SAVE_FAILED: cannot inspect storage."); }
        }
    }
    private Map<String,Object> existing(Path path, Snapshot snapshot) throws IOException {
        var info = checked(path);
        if (info == null) return null;
        if (!info.isRegularFile() || !Arrays.equals(Files.readAllBytes(path),snapshot.payload()))
            throw failure("DRAFT_CONFLICT: destination contains different or edited content.");
        return saved("already_saved", path,snapshot,false);
    }
    private Map<String,Object> saved(String status,Path path,Snapshot snapshot,boolean cleanup) {
        return object("status",status,"request_id",snapshot.requestId(),"path",path.toString(),"total_krw",snapshot.total(),"cleanup_pending",cleanup);
    }
    protected void flush(Path path, byte[] payload) throws IOException {
        try (var stream=FileChannel.open(path,WRITE,TRUNCATE_EXISTING)) {
            var buffer=ByteBuffer.wrap(payload);
            while(buffer.hasRemaining()) stream.write(buffer);
            stream.force(true);
        }
    }
    protected void publish(Path temporary,Path destination) throws IOException { Files.createLink(destination,temporary); }
    protected void cleanup(Path temporary) throws IOException { Files.deleteIfExists(temporary); }
    public Map<String,Object> save(String token) {
        synchronized (lock) {
            var snapshot = previews.get(token);
            if (snapshot == null) throw failure("INVALID_PREVIEW: preview again after replacement or restart.");
            Path temporary = null;
            boolean cleanupPending = false;
            try {
                Path path=destination(snapshot.requestId());
                var existing=existing(path,snapshot);
                if(existing!=null) return existing;
                Files.createDirectories(root); checkRoot();
                temporary=Files.createTempFile(root,".pending-",".tmp");
                try {
                    flush(temporary,snapshot.payload());
                    try { publish(temporary,path); }
                    catch(FileAlreadyExistsException collision) {
                        existing=existing(path,snapshot);
                        if(existing==null) throw failure("SAVE_FAILED: destination changed during publication.");
                        return existing;
                    }
                } finally {
                    try { cleanup(temporary); }
                    catch(IOException error) { cleanupPending=true; }
                    temporary=null;
                }
                return saved("saved",path,snapshot,cleanupPending);
            } catch(IOException error) { throw failure("SAVE_FAILED: publication failed; inspect the destination before retrying."); }
            finally { if(temporary!=null) try { cleanup(temporary); } catch(IOException ignored) {} }
        }
    }
}
