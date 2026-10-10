package lab.inquiry.status;

import java.nio.file.Path;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static lab.inquiry.status.PlanSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class OperationsPlanTest {
    @TempDir Path directory;

    private PlanSupport.Execution run(String... args) throws Exception { return runMain(OperationsMain.class, args); }
    private void result(PlanSupport.Execution actual, String expected, int exit) {
        assertEquals(exit, actual.exit(), actual.err());
        assertEquals(expected + System.lineSeparator(), actual.out());
        assertFalse(actual.err().contains("사용법:"));
    }
    private void usage(String... args) throws Exception {
        var actual = run(args);
        assertEquals(2, actual.exit()); assertEquals("", actual.out());
        assertEquals("사용법: OperationsMain [--data-dir <자료 폴더>] [get <서비스ID>]. 인수가 없으면 도구 목록을 조회합니다."
                + System.lineSeparator(), actual.err());
    }

    @Test void O1_noArgumentsListsDeclaredTool() throws Exception {
        var actual = run();
        assertEquals(0, actual.exit(), actual.err());
        assertEquals(1, actual.out().lines().count());
        var root = StatusWire.JSON.readTree(actual.out());
        assertEquals(1, root.size());
        var tools = root.path("tools");
        var tool = StreamSupport.stream(tools.spliterator(), false)
                .filter(item -> item.path("name").asText().equals("get_service_status")).findFirst().orElseThrow();
        assertEquals(4, tool.size());
        assertEquals("get_service_status", tool.path("name").asText());
        assertEquals("서비스 ID로 현재 서비스 상태를 조회한다. 자료에 없는 서비스는 NOT_FOUND로 돌려준다.", tool.path("description").asText());
        assertEquals(StatusWire.JSON.valueToTree(StatusWire.tool().inputSchema()), tool.path("inputSchema"));
        assertEquals(StatusWire.JSON.valueToTree(StatusWire.outputSchema()), tool.path("outputSchema"));
    }
    @Test void O2_getVpn() throws Exception {
        var actual = run("get", "VPN"); result(actual, VPN, 0);
    }
    @Test void O3_getPayroll() throws Exception {
        var actual = run("get", "PAYROLL"); result(actual, ABSENT, 0);
    }
    @Test void O4_missingDirectory() throws Exception {
        var actual = run("--data-dir", directory.resolve("missing").toString(), "get", "VPN");
        result(actual, UNREADABLE, 1);
    }
    @Test void O5_emptyIdGoesToServer() throws Exception { result(run("get", ""), INVALID, 1); }
    @Test void O7_missingId() throws Exception { usage("get"); }
    @Test void O10_missingDataDirectoryValue() throws Exception { usage("--data-dir"); }
    @Test void serverUsageHasNoProtocolOutput() throws Exception {
        var actual = runMain(StatusServerMain.class, "data", "extra");
        assertEquals(2, actual.exit()); assertEquals("", actual.out());
        assertEquals("사용법: StatusServerMain [자료 폴더]" + System.lineSeparator(), actual.err());
    }
}
