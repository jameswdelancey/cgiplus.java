package adapters.in.http;

import app.JobService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test double for {@link HttpServerAdapter} that records invocations instead of
 * starting a real HTTP server. The fake mirrors the public API by exposing a
 * {@link #start(int)} method that accepts the listening port while returning
 * nothing.
 */
public class FakeHttpServerAdapter {

    /**
     * Immutable snapshot of a {@link #start(int)} invocation.
     */
    public static final class StartInvocation {
        public final int port;

        public StartInvocation(int port) {
            this.port = port;
        }
    }

    private final JobService jobs;
    private final List<StartInvocation> calls = new ArrayList<>();
    private boolean throwOnStart = false;

    public FakeHttpServerAdapter(JobService jobs) {
        this.jobs = jobs;
    }

    /**
     * Mimics {@link HttpServerAdapter#start(int)} by capturing the requested port
     * and optionally throwing an {@link IOException} to simulate startup
     * failures.
     */
    public void start(int port) throws IOException {
        calls.add(new StartInvocation(port));
        if (throwOnStart) {
            throw new IOException("Simulated start failure");
        }
    }

    /**
     * Returns an unmodifiable view of all recorded start invocations.
     */
    public List<StartInvocation> getCalls() {
        return Collections.unmodifiableList(calls);
    }

    /**
     * Enables or disables throwing on the next call to {@link #start(int)}.
     */
    public void setThrowOnStart(boolean throwOnStart) {
        this.throwOnStart = throwOnStart;
    }

    /**
     * Exposes the {@link JobService} dependency for tests that need to interact
     * with the underlying service directly.
     */
    public JobService getJobs() {
        return jobs;
    }
}
