package lab.harness.run;

import java.nio.file.Path;

/** 작업 폴더의 프로젝트를 검사하고 결과를 checkDir에 남긴다. 중단 요청과 프로세스 기록은 작업자 실행과 같은 것을 쓴다. */
@FunctionalInterface
public interface Checker {
    Check run(Path project, Path checkDir, ProcessRunner.StopSignal stop, ProcessRunner.Watcher watcher);

    /** terminationConfirmed가 false이면 검사 프로세스가 남아 있을 수 있어 그 시도의 잠금을 풀 수 없다. */
    record Check(Verdict verdict, String summary, boolean terminationConfirmed) {
        public enum Verdict { PASS, FAILED, UNAVAILABLE }

        public Check(Verdict verdict, String summary) { this(verdict, summary, true); }
    }
}
