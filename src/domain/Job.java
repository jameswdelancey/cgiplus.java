package domain;

import java.nio.file.Path;
import java.util.UUID;

public class Job {
    public final String id = UUID.randomUUID().toString();
    public final String sid;
    public final String className;
    public final String query;       // augmented Q (includes __post, maybe __sid)
    public final Path stdoutPath;
    public final Path stderrPath;

    public volatile JobState state = JobState.QUEUED;
    public volatile int exit = -1;
    public volatile long startMs = 0;
    public volatile long endMs = 0;

    public Job(String className, String query, String sid) {
        this.className = className;
        this.query = query;
        this.sid = sid == null ? "" : sid;
        this.stdoutPath = Path.of("build/jobs", id + ".out");
        this.stderrPath = Path.of("build/jobs", id + ".err");
    }
}
