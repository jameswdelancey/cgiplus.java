package adapters.out.process;

import ports.RouteExecutorPort;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MakeExecutor implements RouteExecutorPort {
    @Override
    public ExecResult execOnce(String className, String query) {
        try {
            List<String> cmd = List.of("make", "-s", "run", "CLASS=" + className, "Q=" + query);
            Process p = new ProcessBuilder(cmd).start();
            String out, err;
            try (BufferedReader ro = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader re = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                out = ro.lines().reduce(new StringBuilder(), (b, s) -> b.append(s).append('\n'), StringBuilder::append).toString();
                err = re.lines().reduce(new StringBuilder(), (b, s) -> b.append(s).append('\n'), StringBuilder::append).toString();
            }
            int exit = p.waitFor();
            return new ExecResult(exit, out, err);
        } catch (Exception e) {
            return new ExecResult(-1, "", "Exception: " + e);
        }
    }
}
