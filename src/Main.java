package app;

import adapters.in.http.HttpServerAdapter;
import adapters.out.process.MakeExecutor;
import ports.RouteExecutorPort;

import java.util.logging.Logger;

public class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws Exception {
        Logging.init();
        LOG.info("Starting application");
        RouteExecutorPort exec = new MakeExecutor();
        JobService jobs = new JobService(exec);
        new HttpServerAdapter(jobs).start(8080);
    }
}
