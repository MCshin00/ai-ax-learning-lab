package lab.week05.example;

import java.util.*;
import lab.week05.example.Execution.*;

/** 현재 단계와 다음 행동의 이유를 구분할 요구에 맞춘 완결된 비교 예제. */
public final class StateEngine {
    public enum State { NEW, NEEDS_INPUT, READY, EXECUTING, VERIFYING, CHECK_FAILED, REPAIRING, SUCCEEDED, STOPPED }
    public enum Event { DETAILS_MISSING, PREPARED, START, EXECUTION_OK, EXECUTION_FAILED,
        VERIFICATION_OK, VERIFICATION_FAILED, REPAIR_ALLOWED, STOP_REQUIRED }
    public record Transition(State from, Event event, State to) {}
    public record Outcome(State state, int attempts, String reason, List<Transition> transitions) {}

    public static State next(State state, Event event) {
        return switch (state) {
            case NEW -> switch (event) {
                case DETAILS_MISSING -> State.NEEDS_INPUT;
                case PREPARED -> State.READY;
                default -> invalid(state, event);
            };
            case READY, REPAIRING -> event == Event.START ? State.EXECUTING : invalid(state, event);
            case EXECUTING -> switch (event) {
                case EXECUTION_OK -> State.VERIFYING;
                case EXECUTION_FAILED -> State.STOPPED;
                default -> invalid(state, event);
            };
            case VERIFYING -> switch (event) {
                case VERIFICATION_OK -> State.SUCCEEDED;
                case VERIFICATION_FAILED -> State.CHECK_FAILED;
                default -> invalid(state, event);
            };
            case CHECK_FAILED -> switch (event) {
                case REPAIR_ALLOWED -> State.REPAIRING;
                case STOP_REQUIRED -> State.STOPPED;
                default -> invalid(state, event);
            };
            default -> invalid(state, event);
        };
    }
    private static State invalid(State state, Event event) {
        throw new IllegalStateException("허용하지 않은 전이: " + state + " / " + event);
    }
    private static final class Session {
        State state = State.NEW;
        int attempts;
        String reason = "";
        final List<Transition> transitions = new ArrayList<>();
        void accept(Event event) {
            State target = next(state, event);
            transitions.add(new Transition(state, event, target)); state = target;
        }
        Outcome result() { return new Outcome(state, attempts, reason, List.copyOf(transitions)); }
    }
    public Outcome run(Job job, Executor executor) {
        var session = new Session();
        var missing = job.missingInformation();
        if (!missing.isEmpty()) {
            session.reason = String.join("\n", missing);
            session.accept(Event.DETAILS_MISSING); return session.result();
        }
        final String original;
        try { original = job.prompt(); }
        catch (Exception error) {
            session.reason = "맥락을 준비하지 못했습니다: " + error.getMessage();
            session.accept(Event.DETAILS_MISSING); return session.result();
        }
        session.accept(Event.PREPARED);
        String prompt = original;
        Result failure = null;
        while (true) {
            switch (session.state) {
                case READY, REPAIRING -> {
                    session.accept(Event.START); session.attempts++;
                    Result result = executor.run(job.executeCommand(), job.workspace(), prompt, job.timeoutSeconds());
                    session.reason = Execution.evidence(result);
                    session.accept(result.kind() == Kind.OK ? Event.EXECUTION_OK : Event.EXECUTION_FAILED);
                }
                case VERIFYING -> {
                    Result result = executor.run(job.verifyCommand(), job.workspace(), "", job.timeoutSeconds());
                    session.reason = Execution.evidence(result); failure = result;
                    session.accept(result.kind() == Kind.OK ? Event.VERIFICATION_OK : Event.VERIFICATION_FAILED);
                }
                case CHECK_FAILED -> {
                    boolean repair = failure.kind() == Kind.FAILED && session.attempts <= job.repairLimit();
                    if (repair) prompt = Execution.repairPrompt(original, failure);
                    session.accept(repair ? Event.REPAIR_ALLOWED : Event.STOP_REQUIRED);
                }
                case SUCCEEDED, STOPPED, NEEDS_INPUT -> { return session.result(); }
                default -> throw new IllegalStateException("실행 행동이 없는 상태: " + session.state);
            }
        }
    }
}
