package tools;

import adapters.in.http.FakeHttpServerAdapter;
import adapters.out.process.FakeMakeExecutor;
import app.JobService;
import ports.RouteExecutorPort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimal command line entry point that exercises the fake adapters added for
 * tests. It enables quick manual verification from the shell without spinning
 * up the full HTTP server or launching real worker processes.
 */
public final class FakeAdaptersCli {

    private FakeAdaptersCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0])) {
            printUsage();
            return;
        }

        String subcommand = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (subcommand) {
            case "http" -> runHttp(rest);
            case "exec" -> runExec(rest);
            default -> {
                System.err.println("Unknown subcommand: " + subcommand);
                printUsage();
                System.exit(2);
            }
        }
    }

    private static void runHttp(String[] args) {
        int port = 8080;
        boolean fail = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--port requires a value");
                    }
                    port = Integer.parseInt(args[++i]);
                }
                case "--fail" -> fail = true;
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }

        FakeMakeExecutor fakeExecutor = new FakeMakeExecutor();
        JobService jobs = new JobService(fakeExecutor);
        FakeHttpServerAdapter fakeHttp = new FakeHttpServerAdapter(jobs);
        fakeHttp.setThrowOnStart(fail);

        try {
            fakeHttp.start(port);
            System.out.println("FakeHttpServerAdapter.start(" + port + ") completed without throwing.");
        } catch (IOException e) {
            System.out.println("FakeHttpServerAdapter.start(" + port + ") threw: " + e.getMessage());
        }

        System.out.println("Recorded start invocations:");
        for (FakeHttpServerAdapter.StartInvocation call : fakeHttp.getCalls()) {
            System.out.println("  - port=" + call.port);
        }
    }

    private static void runExec(String[] args) {
        List<RouteExecutorPort.ExecResult> queued = new ArrayList<>();
        RouteExecutorPort.ExecResult fallback = null;

        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if ("--enqueue".equals(arg)) {
                if (i + 3 >= args.length) {
                    throw new IllegalArgumentException("--enqueue requires <exit> <stdout> <stderr>");
                }
                int exit = Integer.parseInt(args[i + 1]);
                String stdout = args[i + 2];
                String stderr = args[i + 3];
                queued.add(new RouteExecutorPort.ExecResult(exit, stdout, stderr));
                i += 4;
            } else if ("--fallback".equals(arg)) {
                if (i + 3 >= args.length) {
                    throw new IllegalArgumentException("--fallback requires <exit> <stdout> <stderr>");
                }
                int exit = Integer.parseInt(args[i + 1]);
                String stdout = args[i + 2];
                String stderr = args[i + 3];
                fallback = new RouteExecutorPort.ExecResult(exit, stdout, stderr);
                i += 4;
            } else {
                break;
            }
        }

        if (args.length - i < 2) {
            throw new IllegalArgumentException("exec requires <class> <query>");
        }
        String className = args[i++];
        String query = args[i];

        FakeMakeExecutor fakeExecutor = new FakeMakeExecutor();
        if (fallback != null) {
            fakeExecutor.setFallbackResult(fallback);
        }
        for (RouteExecutorPort.ExecResult result : queued) {
            fakeExecutor.enqueueResult(result);
        }

        JobService jobs = new JobService(fakeExecutor);
        RouteExecutorPort.ExecResult result = jobs.execSync(className, query);

        System.out.println("Returned result:");
        System.out.println("  exit=" + result.exit);
        System.out.println("  stdout=" + result.stdout);
        System.out.println("  stderr=" + result.stderr);

        System.out.println("Recorded invocations:");
        for (FakeMakeExecutor.Invocation invocation : fakeExecutor.getInvocations()) {
            System.out.println("  - class=" + invocation.className + ", query=" + invocation.query);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: fake-adapters <subcommand> [options]\n" +
                "Subcommands:\n" +
                "  http [--port <n>] [--fail]   Exercise FakeHttpServerAdapter.start()\n" +
                "  exec [--enqueue <exit> <stdout> <stderr>]... [--fallback <exit> <stdout> <stderr>] <class> <query>\n" +
                "                              Run FakeMakeExecutor via JobService.execSync()\n");
    }
}
