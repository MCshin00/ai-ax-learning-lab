package lab.inquiry.status;

import java.nio.file.Path;

/** 운영 진입점은 그대로 실행하고, 자식 서버 클래스가 없는 클래스패스로 시작 실패를 만든다. */
public final class UnavailableOperationsMain {
    public static void main(String[] args) {
        System.setProperty("java.class.path", Path.of("build", "nonexistent-server-for-test").toAbsolutePath().toString());
        OperationsMain.main(args);
    }
}
