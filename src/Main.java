package app;

import adapters.in.http.HttpServerAdapter;
import adapters.out.process.MakeExecutor;
import ports.RouteExecutorPort;

public class Main {
    public static void main(String[] args) throws Exception {
        RouteExecutorPort exec = new MakeExecutor();
        JobService jobs = new JobService(exec);
        new HttpServerAdapter(jobs).start(8080);
    }
}
