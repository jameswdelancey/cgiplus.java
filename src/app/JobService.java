package app;

import domain.Job;
import domain.JobState;
import ports.RouteExecutorPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class JobService {
    private final RouteExecutorPort executor;
    private final ExecutorService pool;
    private final ConcurrentMap<String, Job> jobs = new ConcurrentHashMap<>();

    public JobService(RouteExecutorPort executor) {
        this.executor = executor;
        int n = Math.max(2, Runtime.getRuntime().availableProcessors()/2);
        this.pool = Executors.newFixedThreadPool(n);
        try {
            Files.createDirectories(Path.of("build/post"));
            Files.createDirectories(Path.of("build/jobs"));
        } catch (IOException ignored) {}
    }

    public RouteExecutorPort.ExecResult execSync(String className, String augmentedQ) {
        return executor.execOnce(className, augmentedQ);
    }

    public Job enqueue(String className, String augmentedQ, String sid) {
        Job j = new Job(className, augmentedQ, sid);
        jobs.put(j.id, j);
        pool.submit(() -> runJob(j));
        return j;
    }

    public Job get(String id) { return jobs.get(id); }

    public Path writePostBody(byte[] bodyBytes) throws IOException {
        String id = UUID.randomUUID().toString();
        Path postPath = Path.of("build/post", id + ".txt");
        Files.write(postPath, bodyBytes == null ? new byte[0] : bodyBytes,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return postPath;
    }

    private void runJob(Job j) {
        j.state = JobState.RUNNING;
        j.startMs = System.currentTimeMillis();
        int exit = -1;
        try {
            ProcessBuilder pb = new ProcessBuilder("make", "run", "CLASS=" + j.className, "Q=" + j.query);
            pb.redirectOutput(j.stdoutPath.toFile());
            pb.redirectError(j.stderrPath.toFile());
            Process p = pb.start();
            exit = p.waitFor();
        } catch (Exception e) {
            try {
                Files.writeString(j.stderrPath, "Exception: " + e + "\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {}
        } finally {
            j.exit = exit;
            j.endMs = System.currentTimeMillis();
            j.state = JobState.DONE;
        }
    }
}
