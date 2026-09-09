package lab.week03;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static lab.week03.Json.*;
import static lab.week03.DraftStore.*;
import static lab.week03.PurchaseContractTest.quote;

class DraftStoreTest {
    @TempDir Path temporary;
    Path root;
    DraftStore store;
    @BeforeEach void setup() { root=temporary.resolve("drafts"); store=new DraftStore(root); }
    Map<String,Object> preview() { return store.preview("purchase-001",quote(2)); }
    static String token(Map<String,Object> preview) { return (String)preview.get("preview_id"); }
    @Test void previewHasCompleteQuoteWithoutFiles() {
        var preview=preview();
        assertEquals(8500,map(preview.get("quote")).get("total_krw"));
        assertEquals(root.resolve("purchase-001.json").toString(),preview.get("path"));
        assertFalse(Files.exists(root));
    }
    @Test void snapshotIgnoresMutatedReturnedData() throws Exception {
        var preview=preview(); map(preview.get("quote")).put("total_krw",1);
        assertEquals("saved",store.save(token(preview)).get("status"));
        assertEquals(8500,map(document(Files.readAllBytes(root.resolve("purchase-001.json")),"purchase-001").get("quote")).get("total_krw"));
    }
    @Test void newPreviewReplacesOldWithoutWriting() {
        var old=preview(); var fresh=store.preview("purchase-001",quote(1));
        assertError("INVALID_PREVIEW",()->store.save(token(old)));
        assertFalse(Files.exists(root)); assertEquals(5000L,store.save(token(fresh)).get("total_krw"));
    }
    @Test void retriesKeepBytesTimestampAndFileCount() throws Exception {
        var preview=preview(); store.save(token(preview));
        Path path=root.resolve("purchase-001.json");
        byte[] bytes=Files.readAllBytes(path); var time=Files.getLastModifiedTime(path);
        assertEquals("already_saved",store.save(token(preview)).get("status"));
        assertEquals("already_saved",store.save(token(preview())).get("status"));
        assertArrayEquals(bytes,Files.readAllBytes(path)); assertEquals(time,Files.getLastModifiedTime(path));
        try(var files=Files.list(root)) { assertEquals(1,files.count()); }
    }
    @Test void changedContentAndManualEditsConflict() throws Exception {
        store.save(token(preview())); Path path=root.resolve("purchase-001.json");
        byte[] original=Files.readAllBytes(path);
        assertError("DRAFT_CONFLICT",()->store.save(token(store.preview("purchase-001",quote(1)))));
        Files.writeString(path,"\n",StandardOpenOption.APPEND);
        byte[] edited=Files.readAllBytes(path);
        assertError("DRAFT_CONFLICT",()->store.save(token(preview())));
        assertArrayEquals(edited,Files.readAllBytes(path));
    }
    @Test void foreignDestinationIsPreserved() throws Exception {
        store.save(token(preview())); Path foreign=root.resolve("purchase-002.json");
        Files.copy(root.resolve("purchase-001.json"),foreign); byte[] bytes=Files.readAllBytes(foreign);
        assertError("DRAFT_CONFLICT",()->store.save(token(store.preview("purchase-002",quote(2)))));
        assertArrayEquals(bytes,Files.readAllBytes(foreign));
    }
    @ParameterizedTest @ValueSource(strings={"","../outside","a/b","a\\b","/outside","C:outside","UPPER","con","lpt1",".","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void invalidIdsCannotEscapeRoot(String id) {
        assertError("INVALID_REQUEST_ID",()->store.preview(id,quote(2)));
        assertFalse(Files.exists(root));
    }
    @Test void publishFailurePreservesFilesAndAllowsRetry() throws Exception {
        Files.createDirectories(root); Files.writeString(root.resolve("existing.json"),"existing");
        DraftStore failing=new DraftStore(root) {
            boolean fail=true;
            @Override protected void publish(Path source,Path target) throws IOException {
                if(fail) { fail=false; throw new IOException("fixture"); } super.publish(source,target);
            }
        };
        var preview=failing.preview("purchase-001",quote(2));
        assertError("SAVE_FAILED",()->failing.save(token(preview)));
        assertEquals("existing",Files.readString(root.resolve("existing.json")));
        assertFalse(Files.exists(root.resolve("purchase-001.json")));
        assertEquals("saved",failing.save(token(preview)).get("status"));
    }
    @Test void flushFailureCannotPublishPartialFile() throws Exception {
        DraftStore failing=new DraftStore(root) {
            @Override protected void flush(Path path,byte[] bytes) throws IOException { throw new IOException("fixture"); }
        };
        assertError("SAVE_FAILED",()->failing.save(token(failing.preview("purchase-001",quote(2)))));
        try(var files=Files.list(root)) { assertEquals(0,files.count()); }
    }
    @Test void publicationCollisionNeverOverwrites() throws Exception {
        DraftStore collision=new DraftStore(root) {
            @Override protected void publish(Path source,Path target) throws IOException {
                Files.writeString(target,"other");throw new FileAlreadyExistsException("fixture");
            }
        };
        assertError("DRAFT_CONFLICT",()->collision.save(token(collision.preview("purchase-001",quote(2)))));
        assertEquals("other",Files.readString(root.resolve("purchase-001.json")));
    }
    @Test void cleanupFailureIsReportedSeparatelyFromSuccess() throws Exception {
        DraftStore failing=new DraftStore(root) { @Override protected void cleanup(Path path) throws IOException { throw new IOException("fixture"); } };
        var result=failing.save(token(failing.preview("purchase-001",quote(2))));
        assertEquals("saved",result.get("status")); assertEquals(true,result.get("cleanup_pending"));
        assertTrue(Files.exists(root.resolve("purchase-001.json")));
    }
    @Test void restartRequiresNewPreviewAndRecognizesSameContent() {
        var old=preview();store.save(token(old));var restarted=new DraftStore(root);
        assertError("INVALID_PREVIEW",()->restarted.save(token(old)));
        assertEquals("already_saved",restarted.save(token(restarted.preview("purchase-001",quote(2)))).get("status"));
    }
    @Test void symlinkRootIsRejected() throws Exception {
        Path outside=temporary.resolve("outside"); Files.createDirectory(outside);
        try { Files.createSymbolicLink(root,outside); }
        catch(IOException|UnsupportedOperationException error) { Assumptions.abort("Platform cannot create test symlink."); }
        assertError("UNSAFE_STORAGE_PATH",()->preview());
        try(var files=Files.list(outside)) { assertEquals(0,files.count()); }
    }
    static void assertError(String code,org.junit.jupiter.api.function.Executable action) {
        assertTrue(assertThrows(IllegalArgumentException.class,action).getMessage().contains(code));
    }
}
