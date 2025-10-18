package ports;

public interface RouteExecutorPort {
    // Run a route synchronously, return [exit, stdout, stderr]
    ExecResult execOnce(String className, String query);

    final class ExecResult {
        public final int exit;
        public final String stdout;
        public final String stderr;
        public ExecResult(int exit, String stdout, String stderr) {
            this.exit = exit; this.stdout = stdout; this.stderr = stderr;
        }
    }
}
