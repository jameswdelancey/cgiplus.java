package adapters.out.process;

import ports.RouteExecutorPort;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Test double for {@link MakeExecutor}. The fake mirrors the
 * {@link RouteExecutorPort} contract by accepting a class name and query string
 * while returning a configurable {@link ExecResult}. Each invocation is
 * recorded for later inspection by tests.
 */
public class FakeMakeExecutor implements RouteExecutorPort {

    /**
     * Immutable snapshot of a single {@link #execOnce(String, String)} call.
     */
    public static final class Invocation {
        public final String className;
        public final String query;

        public Invocation(String className, String query) {
            this.className = className;
            this.query = query;
        }
    }

    private final List<Invocation> invocations = new ArrayList<>();
    private final Deque<ExecResult> queuedResults = new ArrayDeque<>();
    private ExecResult fallbackResult = new ExecResult(0, "", "");

    @Override
    public ExecResult execOnce(String className, String query) {
        invocations.add(new Invocation(className, query));
        ExecResult next = queuedResults.pollFirst();
        return next != null ? next : fallbackResult;
    }

    /**
     * Adds a canned result that will be returned by the next call to
     * {@link #execOnce(String, String)}.
     */
    public void enqueueResult(ExecResult result) {
        queuedResults.addLast(result);
    }

    /**
     * Replaces the fallback result used when no queued result is available.
     */
    public void setFallbackResult(ExecResult fallbackResult) {
        this.fallbackResult = fallbackResult;
    }

    /**
     * Returns an unmodifiable view of all recorded invocations.
     */
    public List<Invocation> getInvocations() {
        return Collections.unmodifiableList(invocations);
    }

    /**
     * Clears all recorded invocations and queued results.
     */
    public void reset() {
        invocations.clear();
        queuedResults.clear();
    }
}
