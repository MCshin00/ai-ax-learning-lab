package lab.week03;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import static lab.week03.Json.*;
import static lab.week03.DraftStore.*;

/**
 * Version-checked local draft edits. Durable intent precedes file publication.
 * One writer server; external editor saves must finish before apply/resume.
 */
public class DraftChanges {
    private final DraftStore store;
    private record Version(byte[] payload,String mtime,String inode) {}
    private record Change(String operationId,String draftId,String action,String targetId,Version before,byte[] after,long baseHead) {}
    private final Map<String,Change> previews = new HashMap<>();
    private final Map<String,String> latest = new HashMap<>();
    private static final String SCHEMA = """
        CREATE TABLE IF NOT EXISTS changes (
          sequence INTEGER PRIMARY KEY AUTOINCREMENT, operation_id TEXT NOT NULL UNIQUE,
          draft_id TEXT NOT NULL, action TEXT NOT NULL CHECK(action IN ('edit','undo')),
          target_operation_id TEXT, state TEXT NOT NULL CHECK(state IN ('prepared','applied','conflict')),
          before_bytes BLOB NOT NULL, before_mtime TEXT NOT NULL, before_inode TEXT NOT NULL,
          after_bytes BLOB NOT NULL, staged_mtime TEXT, staged_inode TEXT, after_mtime TEXT, after_inode TEXT,
          base_head INTEGER NOT NULL, staging_name TEXT NOT NULL, cleanup_pending INTEGER NOT NULL DEFAULT 0
        )
        """;
    public DraftChanges(DraftStore store) { this.store=store; }
    private Version read(String id) throws IOException {
        Path path=store.destination(id);
        var first=checked(path);
        if(first==null) throw failure("DRAFT_NOT_FOUND: create a draft before editing.");
        if(!first.isRegularFile()) throw failure("INVALID_DRAFT: expected a regular file.");
        String firstIdentity=FileIdentity.key(path,first);
        byte[] bytes=Files.readAllBytes(path);
        var last=checked(path);
        if(last==null || !sameAttributes(first,last) || !firstIdentity.equals(FileIdentity.key(path,last))) throw failure("VERSION_CONFLICT: file changed while being read.");
        return version(path,bytes,last);
    }
    private static Version version(Path path,byte[] payload,BasicFileAttributes info) throws IOException {

        return new Version(payload,info.lastModifiedTime().toString(),FileIdentity.key(path,info));
    }
    private static boolean sameAttributes(BasicFileAttributes a,BasicFileAttributes b) {
        return a.size()==b.size() && a.lastModifiedTime().equals(b.lastModifiedTime()) && Objects.equals(a.fileKey(),b.fileKey());
    }
    private static boolean same(Version a,Version b) {
        return Arrays.equals(a.payload(),b.payload()) && a.mtime().equals(b.mtime()) && a.inode().equals(b.inode());
    }
    private Path metadata(boolean create) throws IOException {
        store.checkRoot();
        Path folder=store.root.resolve(".history");
        var info=checked(folder);
        if(info==null) { if(!create) return null; Files.createDirectory(folder); info=checked(folder); }
        if(info==null || !info.isDirectory()) throw failure("UNSAFE_STORAGE_PATH: history must be a directory.");
        for(String suffix:List.of("","-journal","-wal","-shm")) {
            var file=checked(folder.resolve("changes.sqlite3"+suffix));
            if(file!=null && !file.isRegularFile()) throw failure("UNSAFE_STORAGE_PATH: history must use regular files.");
        }
        return folder;
    }
    private Connection journal(boolean write) throws IOException,SQLException {
        Path folder=metadata(write);
        if(folder==null || (!write && !Files.exists(folder.resolve("changes.sqlite3")))) return null;
        Path file=folder.resolve("changes.sqlite3");
        boolean existed=Files.exists(file);
        // Inspect the history format using a read-only connection before any writable open.
        if(existed) {
            try(var check=DriverManager.getConnection("jdbc:sqlite:"+file.toUri()+"?mode=ro")) {
                if(number(query(check,"PRAGMA user_version").get(0),"user_version")!=1)
                    throw failure("HISTORY_FORMAT_UNSUPPORTED: this history format does not match the draft store. Use a matching store and operation ID.");
            }
        }
        String url=write ? "jdbc:sqlite:"+file : "jdbc:sqlite:"+file.toUri()+"?mode=ro";
        Connection db=DriverManager.getConnection(url);
        try {
            sql(db,"PRAGMA busy_timeout=5000");
            if(write) { sql(db,"PRAGMA synchronous=FULL"); sql(db,SCHEMA); if(!existed) sql(db,"PRAGMA user_version=1"); }
            return db;
        } catch(SQLException error) { db.close(); throw error; }
    }
    private static void sql(Connection db,String query,Object... args) throws SQLException {
        try(var stmt=db.prepareStatement(query)) {
            for(int i=0;i<args.length;i++) stmt.setObject(i+1,args[i]);
            stmt.execute();
        }
    }
    private static List<Map<String,Object>> query(Connection db,String query,Object... args) throws SQLException {
        if(db==null) return List.of();
        try(var stmt=db.prepareStatement(query)) {
            for(int i=0;i<args.length;i++) stmt.setObject(i+1,args[i]);
            try(var rows=stmt.executeQuery()) {
                var result=new ArrayList<Map<String,Object>>();
                while(rows.next()) {
                    var row=new LinkedHashMap<String,Object>();
                    for(int i=1;i<=rows.getMetaData().getColumnCount();i++) row.put(rows.getMetaData().getColumnLabel(i),rows.getObject(i));
                    result.add(row);
                }
                return result;
            }
        }
    }
    private static Map<String,Object> row(Connection db,String id) throws SQLException {
        var rows=query(db,"SELECT * FROM changes WHERE operation_id=?",id);
        return rows.isEmpty()?null:rows.get(0);
    }
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static long head(Connection db,String id) throws SQLException {
        var rows=query(db,"SELECT COALESCE(MAX(sequence),0) AS latest FROM changes WHERE draft_id=? AND state='applied'",id);
        return rows.isEmpty()?0:number(rows.get(0),"latest");
    }
    private static void pending(Connection db,String id,String operationId) throws SQLException {
        if(!query(db,"SELECT 1 FROM changes WHERE draft_id=? AND state='prepared' AND operation_id<>?",id,operationId).isEmpty())
            throw failure("RECOVERY_REQUIRED: inspect and resume the pending operation first.");
    }
    private static Change change(Map<String,Object> row) {
        return new Change((String)row.get("operation_id"),(String)row.get("draft_id"),(String)row.get("action"),(String)row.get("target_operation_id"),
            new Version((byte[])row.get("before_bytes"),(String)row.get("before_mtime"),(String)row.get("before_inode")),(byte[])row.get("after_bytes"),number(row,"base_head"));
    }
    private static boolean sameChange(Change a,Change b) {
        return a.operationId().equals(b.operationId()) && a.draftId().equals(b.draftId()) && a.action().equals(b.action())
            && Objects.equals(a.targetId(),b.targetId()) && same(a.before(),b.before()) && Arrays.equals(a.after(),b.after()) && a.baseHead()==b.baseHead();
    }
    private Map<String,Object> remember(Change change,String state) throws IOException {
        String token=token(),old=latest.put(change.draftId(),token);
        if(old!=null) previews.remove(old);
        previews.put(token,change);
        String digest;
        try { digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(change.before().payload())); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return object("preview_id",token,"operation_id",change.operationId(),"draft_id",change.draftId(),"action",change.action(),
            "target_operation_id",change.targetId(),"path",store.destination(change.draftId()).toString(),"base_version",digest,
            "before",document(change.before().payload(),change.draftId()),"after",document(change.after(),change.draftId()),"operation_state",state);
    }
    public Map<String,Object> previewEdit(String id,String operationId,CatalogService.PurchaseReview quote) {
        synchronized(store.lock) {
            try {
                store.destination(operationId);
                var current=read(id); var before=document(current.payload(),id); long head;
                try(var db=journal(false)) {
                    var previous=row(db,operationId);
                    if(previous!=null) {
                        if(!Objects.equals(previous.get("action"),"edit") || !Objects.equals(previous.get("draft_id"),id)
                            || !document((byte[])previous.get("after_bytes"),id).get("quote").equals(quote(quote)))
                            throw failure("OPERATION_CONFLICT: this operation ID already identifies different content.");
                        return remember(change(previous),(String)previous.get("state"));
                    }
                    pending(db,id,operationId); head=head(db,id);
                }
                before.put("quote",quote(quote)); before.put("revision",operationId);
                return remember(new Change(operationId,id,"edit",null,current,encode(before),head),"new");
            } catch(IOException|SQLException error) { throw unavailable(error); }
        }
    }
    public Map<String,Object> previewUndo(String targetId,String operationId) {
        synchronized(store.lock) {
            try {
                store.destination(operationId); store.destination(targetId);
                try(var db=journal(false)) {
                    var target=row(db,targetId);
                    if(target==null || !target.get("state").equals("applied") || !target.get("action").equals("edit"))
                        throw failure("UNDO_UNAVAILABLE: only an applied edit can be undone.");
                    var previous=row(db,operationId);
                    if(previous!=null) {
                        if(!previous.get("action").equals("undo") || !Objects.equals(previous.get("target_operation_id"),targetId))
                            throw failure("OPERATION_CONFLICT: the cancellation ID is already in use.");
                        return remember(change(previous),(String)previous.get("state"));
                    }
                    String id=(String)target.get("draft_id");
                    pending(db,id,operationId);
                    var current=read(id);
                    if(!isCurrent(db,target,current)) throw failure("UNDO_CONFLICT: a later edit or external file change must be preserved.");
                    return remember(new Change(operationId,id,"undo",targetId,current,(byte[])target.get("before_bytes"),number(target,"sequence")),"new");
                }
            } catch(IOException|SQLException error) { throw unavailable(error); }
        }
    }
    private boolean isCurrent(Connection db,Map<String,Object> row,Version current) throws SQLException {
        try {
            if(current==null) current=read((String)row.get("draft_id"));
            return row.get("state").equals("applied") && head(db,(String)row.get("draft_id"))==number(row,"sequence")
                && Arrays.equals(current.payload(),(byte[])row.get("after_bytes")) && current.mtime().equals(row.get("after_mtime"))
                && current.inode().equals(row.get("after_inode"));
        } catch(IOException|IllegalArgumentException error) { return false; }
    }
    private Map<String,Object> result(Connection db,Map<String,Object> row,boolean repeated) throws IOException,SQLException {
        String id=(String)row.get("draft_id");
        return object("operation_id",row.get("operation_id"),"draft_id",id,"action",row.get("action"),"target_operation_id",row.get("target_operation_id"),
            "status",repeated && row.get("state").equals("applied")?"already_applied":row.get("state"),"path",store.destination(id).toString(),
            "total_krw",map(document((byte[])row.get("after_bytes"),id).get("quote")).get("total_krw"),
            "current_matches",isCurrent(db,row,null),"cleanup_pending",number(row,"cleanup_pending")!=0);
    }
    public Map<String,Object> getOperation(String id) {
        synchronized(store.lock) {
            try {
                store.destination(id);
                try(var db=journal(false)) {
                    var row=row(db,id);
                    if(row==null) throw failure("UNKNOWN_OPERATION: no save request is recorded for this ID.");
                    return result(db,row,false);
                }
            } catch(IOException|SQLException error) { throw unavailable(error); }
        }
    }
    public List<Map<String,Object>> history(String id) {
        synchronized(store.lock) {
            try {
                store.destination(id);
                try(var db=journal(false)) {
                    var results=new ArrayList<Map<String,Object>>();
                    for(var row:query(db,"SELECT * FROM changes WHERE draft_id=? ORDER BY sequence",id)) results.add(result(db,row,false));
                    return results;
                }
            } catch(IOException|SQLException error) { throw unavailable(error); }
        }
    }
    public Map<String,Object> apply(String previewId) {
        synchronized(store.lock) {
            var change=previews.get(previewId);
            if(change==null) throw failure("INVALID_PREVIEW: preview again after replacement or restart.");
            try {
                Map<String,Object> existing;
                try(var db=journal(false)) { existing=row(db,change.operationId()); }
                if(existing==null && !same(read(change.draftId()),change.before())) throw failure("VERSION_CONFLICT: draft changed after preview.");
                try(var db=journal(true)) {
                    sql(db,"BEGIN IMMEDIATE");
                    existing=row(db,change.operationId());
                    if(existing!=null) {
                        if(!sameChange(change(existing),change)) throw failure("OPERATION_CONFLICT: this ID already has a different request.");
                    } else {
                        pending(db,change.draftId(),change.operationId());
                        if(head(db,change.draftId())!=change.baseHead() || !same(read(change.draftId()),change.before()))
                            throw failure("VERSION_CONFLICT: draft changed after preview.");
                        sql(db,"""
                            INSERT INTO changes(operation_id,draft_id,action,target_operation_id,state,
                            before_bytes,before_mtime,before_inode,after_bytes,base_head,staging_name)
                            VALUES(?,?,?,?,'prepared',?,?,?,?,?,?)
                            """,change.operationId(),change.draftId(),change.action(),change.targetId(),change.before().payload(),
                            change.before().mtime(),change.before().inode(),change.after(),change.baseHead(),"pending-"+token()+".tmp");
                    }
                    sql(db,"COMMIT");
                }
                checkpoint("prepared");
                return resume(change.operationId());
            } catch(IOException error) { throw failure("CHANGE_PENDING: inspect this operation ID before retrying."); }
            catch(SQLException error) { throw unavailable(error); }
        }
    }
    protected void checkpoint(String stage) throws IOException { }
    private Path staging(Map<String,Object> row) throws IOException {
        Path folder=metadata(false);
        String name=(String)row.get("staging_name");
        if(folder==null || name==null || !name.matches("pending-[a-f0-9]{32}\\.tmp")) throw failure("INVALID_HISTORY: invalid staging location.");
        Path path=folder.resolve(name);
        var info=checked(path);
        if(info!=null && !info.isRegularFile()) throw failure("UNSAFE_STORAGE_PATH: staging must be a regular file.");
        return path;
    }
    protected boolean cleanup(Map<String,Object> row) {
        try { Files.deleteIfExists(staging(row)); return false; }
        catch(IOException|IllegalArgumentException error) { return true; }
    }
    protected void replace(Map<String,Object> row,Connection db) throws IOException,SQLException {
        Path target=store.destination((String)row.get("draft_id")),staging=staging(row);
        checkpoint("before-stage");
        if(!Files.exists(staging)) Files.createFile(staging);
        store.flush(staging,(byte[])row.get("after_bytes"));
        checkpoint("flushed");
        var staged=version(staging,Files.readAllBytes(staging),checked(staging));
        if(!Arrays.equals(staged.payload(),(byte[])row.get("after_bytes"))) throw failure("VERSION_CONFLICT: staging changed.");
        sql(db,"UPDATE changes SET staged_mtime=?,staged_inode=? WHERE operation_id=?",staged.mtime(),staged.inode(),row.get("operation_id"));
        sql(db,"COMMIT");
        checkpoint("staged");
        sql(db,"BEGIN IMMEDIATE");
        var latest=row(db,(String)row.get("operation_id"));
        if(!latest.get("state").equals("prepared") || head(db,(String)row.get("draft_id"))!=number(row,"base_head")
            || !same(read((String)row.get("draft_id")),change(row).before()))
            throw failure("VERSION_CONFLICT: draft changed before publication.");
        Files.move(staging,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        checkpoint("published");
    }
    public Map<String,Object> resume(String operationId) {
        synchronized(store.lock) {
            try {
                store.destination(operationId);
                Path folder=metadata(false);
                if(folder==null || !Files.exists(folder.resolve("changes.sqlite3"))) throw failure("UNKNOWN_OPERATION: no save request is recorded.");
                try(var db=journal(true)) {
                    sql(db,"BEGIN IMMEDIATE");
                    var row=row(db,operationId);
                    if(row==null) throw failure("UNKNOWN_OPERATION: no save request is recorded.");
                    if(row.get("state").equals("applied")) { sql(db,"COMMIT"); return result(db,row,true); }
                    if(row.get("state").equals("conflict")) throw failure("RECOVERY_CONFLICT: preserve the file and use a new operation ID.");
                    Version written;
                    try {
                        if(head(db,(String)row.get("draft_id"))!=number(row,"base_head")) throw failure("VERSION_CONFLICT: later operation exists.");
                        var current=read((String)row.get("draft_id"));
                        if(!Arrays.equals(current.payload(),(byte[])row.get("after_bytes"))) {
                            if(!same(current,change(row).before())) throw failure("VERSION_CONFLICT: before/after do not match.");
                            replace(row,db);
                        }
                        written=read((String)row.get("draft_id"));
                        var published=row(db,operationId);
                        if(!Arrays.equals(written.payload(),(byte[])row.get("after_bytes")) || !written.mtime().equals(published.get("staged_mtime"))
                            || !written.inode().equals(published.get("staged_inode")))
                            throw failure("VERSION_CONFLICT: published file was changed externally.");
                    } catch(IllegalArgumentException error) {
                        boolean cleanupPending=cleanup(row);
                        sql(db,"UPDATE changes SET state='conflict',cleanup_pending=? WHERE operation_id=? AND state='prepared'",cleanupPending?1:0,operationId);
                        sql(db,"COMMIT");
                        throw failure("RECOVERY_CONFLICT: current file was preserved; inspect before a new edit.");
                    }
                    boolean cleanupPending=cleanup(row);
                    sql(db,"UPDATE changes SET state='applied',after_mtime=?,after_inode=?,cleanup_pending=? WHERE operation_id=?",
                        written.mtime(),written.inode(),cleanupPending?1:0,operationId);
                    checkpoint("before-commit");
                    sql(db,"COMMIT");
                    return result(db,row(db,operationId),false);
                }
            } catch(IOException error) { throw failure("CHANGE_PENDING: inspect and resume this operation ID; do not assume failure or success."); }
            catch(SQLException error) { throw unavailable(error); }
        }
    }
    private static IllegalArgumentException unavailable(Exception cause) {
        return new IllegalArgumentException("HISTORY_UNAVAILABLE: inspect storage or resume a known operation requiring recovery.",cause);
    }
}
