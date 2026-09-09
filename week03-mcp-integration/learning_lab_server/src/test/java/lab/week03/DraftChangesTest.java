package lab.week03;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static lab.week03.Json.*;
import static lab.week03.DraftStore.*;
import static lab.week03.DraftStoreTest.*;
import static lab.week03.PurchaseContractTest.quote;

class DraftChangesTest {
    @TempDir Path temporary;
    Path root,path; DraftStore store; DraftChanges changes; byte[] original;
    @BeforeEach void setup() throws Exception {
        root=temporary.resolve("drafts"); store=new DraftStore(root);
        store.save(token(store.preview("draft-001",quote(1))));
        path=root.resolve("draft-001.json");
        var document=Json.parse(Files.readAllBytes(path)); document.put("manual_note","Keep this note.");
        Files.writeString(path,"  "+Json.text(document)+"\n\n");
        original=Files.readAllBytes(path); changes=new DraftChanges(store);
    }
    Map<String,Object> preview(String id,long quantity) { return changes.previewEdit("draft-001",id,quote(quantity)); }
    Map<String,Object> apply(String id,long quantity) { return changes.apply(token(preview(id,quantity))); }
    long total() throws IOException { return ((Number)map(Json.parse(Files.readAllBytes(path)).get("quote")).get("total_krw")).longValue(); }
    void bumpTime() throws Exception { Files.setLastModifiedTime(path,FileTime.fromMillis(Files.getLastModifiedTime(path).toMillis()+2000)); }
    @Test void previewPreservesNotesAndSnapshotWithoutHistory() throws Exception {
        var preview=preview("edit-001",2);
        assertEquals("Keep this note.",map(preview.get("after")).get("manual_note"));
        assertFalse(Files.exists(root.resolve(".history"))); assertArrayEquals(original,Files.readAllBytes(path));
        map(map(preview.get("after")).get("quote")).put("total_krw",1);
        changes.apply(token(preview)); assertEquals(8500,total());
        assertEquals("Keep this note.",Json.parse(Files.readAllBytes(path)).get("manual_note"));
    }
    @Test void latestPreviewInvalidatesPrevious() {
        var old=preview("first",2);var fresh=preview("second",3);
        assertError("INVALID_PREVIEW",()->changes.apply(token(old)));
        assertFalse(Files.exists(root.resolve(".history")));
        assertEquals("applied",changes.apply(token(fresh)).get("status"));
    }
    @Test void externalEditAfterPreviewIsPreserved() throws Exception {
        var preview=preview("edit-001",2);Files.writeString(path,"\n",StandardOpenOption.APPEND);
        byte[] edited=Files.readAllBytes(path);
        assertError("VERSION_CONFLICT",()->changes.apply(token(preview)));
        assertArrayEquals(edited,Files.readAllBytes(path)); assertFalse(Files.exists(root.resolve(".history")));
        assertEquals("applied",apply("fresh",2).get("status"));
    }
    @Test void operationIdCannotIdentifyDifferentContentOrDraft() throws Exception {
        apply("edit-001",2);byte[] edited=Files.readAllBytes(path);
        assertError("OPERATION_CONFLICT",()->preview("edit-001",3));
        store.save(token(store.preview("draft-002",quote(1))));
        assertError("OPERATION_CONFLICT",()->changes.previewEdit("draft-002","edit-001",quote(2)));
        assertArrayEquals(edited,Files.readAllBytes(path));
    }
    @Test void restartRetryUsesHistoryWithoutRewriting() throws Exception {
        var preview=preview("edit-001",2);changes.apply(token(preview));
        byte[] edited=Files.readAllBytes(path);var time=Files.getLastModifiedTime(path);
        changes=new DraftChanges(new DraftStore(root));
        assertError("INVALID_PREVIEW",()->changes.apply(token(preview)));
        assertEquals("applied",changes.getOperation("edit-001").get("status"));
        assertEquals("already_applied",changes.resume("edit-001").get("status"));
        assertEquals("already_applied",changes.apply(token(preview("edit-001",2))).get("status"));
        assertArrayEquals(edited,Files.readAllBytes(path));assertEquals(time,Files.getLastModifiedTime(path));
        assertEquals(1,changes.history("draft-001").size());
    }
    @Test void retryAfterLaterEditIsHistoricalOnly() throws Exception {
        apply("edit-001",2);apply("edit-002",3);byte[] latest=Files.readAllBytes(path);
        var retry=changes.resume("edit-001");
        assertEquals("already_applied",retry.get("status"));assertEquals(false,retry.get("current_matches"));
        assertEquals(8500,retry.get("total_krw"));assertArrayEquals(latest,Files.readAllBytes(path));
        assertError("UNDO_CONFLICT",()->changes.previewUndo("edit-001","undo-old"));
    }
    @Test void undoRestoresExactOriginalBytesAndSurvivesRestart() throws Exception {
        apply("edit-001",2);var undo=changes.previewUndo("edit-001","undo-001");
        assertEquals("undo",changes.apply(token(undo)).get("action"));
        assertArrayEquals(original,Files.readAllBytes(path));var time=Files.getLastModifiedTime(path);
        changes=new DraftChanges(new DraftStore(root));
        assertEquals("already_applied",changes.resume("undo-001").get("status"));
        assertEquals("already_applied",changes.apply(token(changes.previewUndo("edit-001","undo-001"))).get("status"));
        assertArrayEquals(original,Files.readAllBytes(path));assertEquals(time,Files.getLastModifiedTime(path));
        assertEquals(List.of("edit","undo"),changes.history("draft-001").stream().map(r->r.get("action")).toList());
    }
    @Test void laterEditAndUndoStillBlockOlderUndo() throws Exception {
        apply("edit-a",2);byte[] first=Files.readAllBytes(path);apply("edit-b",3);
        changes.apply(token(changes.previewUndo("edit-b","undo-b")));assertArrayEquals(first,Files.readAllBytes(path));
        assertError("UNDO_CONFLICT",()->changes.previewUndo("edit-a","undo-a"));
    }
    @Test void sameContentExternalTimestampChangeBlocksUndo() throws Exception {
        apply("edit-001",2);bumpTime();assertError("UNDO_CONFLICT",()->changes.previewUndo("edit-001","undo-001"));
        assertEquals(8500,total());
    }
    @Test void editAfterUndoPreviewIsPreserved() throws Exception {
        apply("edit-001",2);var undo=changes.previewUndo("edit-001","undo-001");
        Files.writeString(path,"\n",StandardOpenOption.APPEND);byte[] edited=Files.readAllBytes(path);
        assertError("VERSION_CONFLICT",()->changes.apply(token(undo)));assertArrayEquals(edited,Files.readAllBytes(path));
        assertEquals(1,changes.history("draft-001").size());
    }
    @Test void unknownStatusResumeAndUnappliedPreviewCannotCreateHistory() {
        assertEquals(List.of(),changes.history("draft-001"));
        assertError("UNKNOWN_OPERATION",()->changes.getOperation("unknown"));
        assertError("UNKNOWN_OPERATION",()->changes.resume("unknown"));
        preview("unapproved",2);assertError("UNKNOWN_OPERATION",()->changes.resume("unapproved"));
        assertFalse(Files.exists(root.resolve(".history")));
    }
    DraftChanges failAt(String point) {
        return new DraftChanges(store) {
            @Override protected void checkpoint(String stage) throws IOException { if(stage.equals(point)) throw new IOException("fixture"); }
        };
    }
    @Test void preparedFailureBlocksOtherChangesAndResumes() throws Exception {
        changes=failAt("prepared");
        assertError("CHANGE_PENDING",()->apply("edit-001",2));
        assertEquals("prepared",changes.getOperation("edit-001").get("status"));
        assertError("RECOVERY_REQUIRED",()->preview("edit-other",3));assertArrayEquals(original,Files.readAllBytes(path));
        changes=new DraftChanges(new DraftStore(root));assertEquals("applied",changes.resume("edit-001").get("status"));assertEquals(8500,total());
    }
    @Test void failureAfterPublicationRecoversWithoutRewriting() throws Exception {
        changes=failAt("published");assertError("CHANGE_PENDING",()->apply("edit-001",2));
        assertEquals(8500,total());var time=Files.getLastModifiedTime(path);byte[] edited=Files.readAllBytes(path);
        changes=new DraftChanges(new DraftStore(root));assertEquals("applied",changes.resume("edit-001").get("status"));
        assertEquals(time,Files.getLastModifiedTime(path));assertArrayEquals(edited,Files.readAllBytes(path));
    }
    @Test void recoveryRejectsSameBytesRewrittenExternally() throws Exception {
        changes=failAt("published");assertError("CHANGE_PENDING",()->apply("edit-001",2));bumpTime();
        byte[] edited=Files.readAllBytes(path);changes=new DraftChanges(new DraftStore(root));
        assertError("RECOVERY_CONFLICT",()->changes.resume("edit-001"));
        assertEquals("conflict",changes.getOperation("edit-001").get("status"));
        assertArrayEquals(edited,Files.readAllBytes(path));assertError("UNDO_UNAVAILABLE",()->changes.previewUndo("edit-001","undo"));
    }
    @Test void recoveryPreservesExternalContentAndMarksTerminalConflict() throws Exception {
        changes=failAt("prepared");assertError("CHANGE_PENDING",()->apply("edit-001",2));
        Files.writeString(path,"\n",StandardOpenOption.APPEND);byte[] edited=Files.readAllBytes(path);
        changes=new DraftChanges(new DraftStore(root));assertError("RECOVERY_CONFLICT",()->changes.resume("edit-001"));
        assertEquals("conflict",changes.getOperation("edit-001").get("status"));
        assertError("RECOVERY_CONFLICT",()->changes.resume("edit-001"));assertArrayEquals(edited,Files.readAllBytes(path));
        assertEquals("new",preview("fresh",2).get("operation_state"));
    }
    @Test void externalChangeDuringStagingIsCheckedBeforeReplace() throws Exception {
        changes=new DraftChanges(store) { @Override protected void checkpoint(String stage) throws IOException {
            if(stage.equals("flushed")) Files.writeString(path,"\n",StandardOpenOption.APPEND);
        }};
        assertError("RECOVERY_CONFLICT",()->apply("edit-001",2));
        assertEquals(5000,total());assertEquals("conflict",changes.getOperation("edit-001").get("status"));
    }
    @Test void journalFailureNeverReportsSuccess() throws Exception {
        Files.createDirectories(root.resolve(".history"));Files.writeString(root.resolve(".history/changes.sqlite3"),"invalid database");
        assertError("HISTORY_UNAVAILABLE",()->preview("edit-001",2));assertArrayEquals(original,Files.readAllBytes(path));
    }
    @Test void cleanupStateIsSeparateFromAppliedState() {
        changes=new DraftChanges(store) { @Override protected boolean cleanup(Map<String,Object> row) { return true; }};
        var result=apply("edit-001",2);assertEquals("applied",result.get("status"));assertEquals(true,result.get("cleanup_pending"));
    }
    @Test void invalidDraftAndIdCannotCreateChange() throws Exception {
        assertError("INVALID_REQUEST_ID",()->preview("../escape",2));
        Files.writeString(path,"{}");assertError("INVALID_DRAFT",()->preview("edit-001",2));
        assertFalse(Files.exists(root.resolve(".history")));
    }
    @Test void legacyJournalIsRejectedWithoutMutation() throws Exception {
        Path history=root.resolve(".history");Files.createDirectory(history);
        Path database=history.resolve("changes.sqlite3");
        try(var db=java.sql.DriverManager.getConnection("jdbc:sqlite:"+database);var stmt=db.createStatement()) {
            stmt.execute("CREATE TABLE changes(operation_id TEXT,state TEXT)");
            stmt.execute("INSERT INTO changes VALUES('old-edit','applied')");
        }
        byte[] originalDatabase=Files.readAllBytes(database);var time=Files.getLastModifiedTime(database);
        assertError("HISTORY_FORMAT_UNSUPPORTED",()->changes.history("draft-001"));
        assertError("HISTORY_FORMAT_UNSUPPORTED",()->changes.resume("old-edit"));
        assertError("HISTORY_FORMAT_UNSUPPORTED",()->preview("new-edit",2));
        assertArrayEquals(originalDatabase,Files.readAllBytes(database));assertEquals(time,Files.getLastModifiedTime(database));
        assertArrayEquals(original,Files.readAllBytes(path));
    }
    @Test void nonDirectoryHistoryIsRejected() throws Exception {
        Files.writeString(root.resolve(".history"),"foreign");
        assertError("UNSAFE_STORAGE_PATH",()->preview("edit-001",2));assertArrayEquals(original,Files.readAllBytes(path));
    }
    @ParameterizedTest @ValueSource(strings={"prepared","before-stage","partial","flushed","staged","published","before-commit"})
    void actualProcessDeathRecoversExactEdit(String stage) throws Exception {
        var child=new ProcessBuilder(javaCommand(),"-cp",System.getProperty("test.classpath"),CrashChild.class.getName(),root.toString(),stage,"edit").inheritIO().start();
        assertTrue(child.waitFor(30,java.util.concurrent.TimeUnit.SECONDS));assertEquals(73,child.exitValue());
        byte[] beforeResume=Files.readAllBytes(path);var time=Files.getLastModifiedTime(path);
        changes=new DraftChanges(new DraftStore(root));
        assertEquals("applied",changes.resume("crash-edit").get("status"));assertEquals(8500,total());
        if(stage.equals("published") || stage.equals("before-commit")) {
            assertArrayEquals(beforeResume,Files.readAllBytes(path));assertEquals(time,Files.getLastModifiedTime(path));
        }
        changes.apply(token(changes.previewUndo("crash-edit","crash-undo")));
        assertArrayEquals(original,Files.readAllBytes(path));
    }
    @ParameterizedTest @ValueSource(strings={"staged","published"})
    void actualProcessDeathDuringUndoRestoresExactBytes(String stage) throws Exception {
        apply("edit-001",2);
        var child=new ProcessBuilder(javaCommand(),"-cp",System.getProperty("test.classpath"),CrashChild.class.getName(),root.toString(),stage,"undo").inheritIO().start();
        assertTrue(child.waitFor(30,java.util.concurrent.TimeUnit.SECONDS));assertEquals(73,child.exitValue());
        changes=new DraftChanges(new DraftStore(root));assertEquals("applied",changes.resume("crash-undo").get("status"));
        assertArrayEquals(original,Files.readAllBytes(path));
    }
    static String javaCommand() { return Path.of(System.getProperty("java.home"),"bin","java").toString(); }
    public static class CrashChild {
        public static void main(String[] args) {
            var store=new DraftStore(Path.of(args[0])) {
                @Override protected void flush(Path path,byte[] payload) throws IOException {
                    if(args[1].equals("partial")) {
                        Files.write(path,Arrays.copyOf(payload,20)); Runtime.getRuntime().halt(73);
                    }
                    super.flush(path,payload);
                }
            };
            var changes=new DraftChanges(store) { @Override protected void checkpoint(String stage) {
                if(stage.equals(args[1])) Runtime.getRuntime().halt(73);
            }};
            var preview=args[2].equals("undo")?changes.previewUndo("edit-001","crash-undo"):changes.previewEdit("draft-001","crash-edit",quote(2));
            changes.apply(token(preview));throw new AssertionError("Crash checkpoint was not reached.");
        }
    }
}
