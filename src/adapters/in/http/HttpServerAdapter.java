package adapters.in.http;

import app.JobService;
import app.QueryUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import domain.Job;
import ports.RouteExecutorPort;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public class HttpServerAdapter {

    private final JobService jobs;

    public HttpServerAdapter(JobService jobs) {
        this.jobs = jobs;
    }

    public void start(int port) throws IOException {
        HttpServer srv = HttpServer.create(new InetSocketAddress(port), 0);
        srv.setExecutor(Executors.newCachedThreadPool());

        srv.createContext("/", this::handleRoot);
        srv.createContext("/api", this::handleApi);
        srv.start();
        System.out.println("Listening on http://localhost:" + port);
    }

    // ---- handlers ----
    private void handleRoot(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
            send(ex, 405, "text/plain; charset=utf-8", "Method Not Allowed");
            return;
        }
        Path p = Path.of("static/index.html");
        if (!Files.exists(p)) { send(ex, 500, "text/plain; charset=utf-8", "Missing static/index.html"); return; }
        byte[] b = Files.readAllBytes(p);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private void handleApi(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        if (path.equals("/api/job/start")) { handleJobStart(ex); return; }
        if (path.equals("/api/job/status")) { handleJobStatus(ex); return; }
        if (path.equals("/api/job/output")) { handleJobOutput(ex); return; }

        // default sync route: /api/<Name> -> routes.api.<Name>
        String[] parts = path.split("/");
        String last = parts.length >= 3 ? parts[2] : "echo";
        String className = "routes.api." + toPascal(last);

        // per-request POST file
        Path postPath = persistBodyIfAny(ex);
        String rawQ = ex.getRequestURI().getRawQuery();
        if (rawQ == null) rawQ = "";
        String augmentedQ = rawQ.isEmpty() ? "__post=" + QueryUtil.enc(postPath.toString())
                : rawQ + "&__post=" + QueryUtil.enc(postPath.toString());

        RouteExecutorPort.ExecResult r = jobs.execSync(className, augmentedQ);
        if (r.exit != 0) {
            send(ex, 500, "text/plain; charset=utf-8",
                    "Route process failed (exit " + r.exit + ")\n" + r.stderr);
        } else {
            send(ex, 200, "application/json; charset=utf-8", r.stdout);
        }
    }

    private void handleJobStart(HttpExchange ex) throws IOException {
        Map<String,String> qs = QueryUtil.parse(ex.getRequestURI().getRawQuery());
        String name = qs.get("name");
        String fqcn = qs.get("class");
        String sid  = qs.getOrDefault("sid", "");

        if ((name == null || name.isBlank()) && (fqcn == null || fqcn.isBlank())) {
            send(ex, 400, "text/plain; charset=utf-8", "Missing name= or class=");
            return;
        }
        String className = (fqcn != null && !fqcn.isBlank()) ? fqcn : "routes.api." + toPascal(name);

        Path postPath = persistBodyIfAny(ex);
        String rawQ = ex.getRequestURI().getRawQuery();
        if (rawQ == null) rawQ = "";
        String augmentedQ = rawQ
                + (rawQ.isEmpty() ? "" : "&")
                + "__post=" + QueryUtil.enc(postPath.toString())
                + "&__sid=" + QueryUtil.enc(sid);

        Job j = jobs.enqueue(className, augmentedQ, sid);
        send(ex, 200, "application/json; charset=utf-8",
                "{\"jobId\":\"" + j.id + "\",\"state\":\"" + j.state + "\",\"sid\":\"" + esc(sid) + "\"}");
    }

    private void handleJobStatus(HttpExchange ex) throws IOException {
        Map<String,String> qs = QueryUtil.parse(ex.getRequestURI().getRawQuery());
        String id = qs.get("id");
        if (id == null || id.isBlank()) { send(ex, 400, "text/plain; charset=utf-8", "Missing id"); return; }
        Job j = jobs.get(id);
        if (j == null) { send(ex, 404, "text/plain; charset=utf-8", "No such job"); return; }

        long now = System.currentTimeMillis();
        long runtime = (j.startMs == 0 ? 0 : (j.endMs == 0 ? now - j.startMs : j.endMs - j.startMs));
        String json = "{"
                + "\"jobId\":\""+j.id+"\","
                + "\"sid\":\""+esc(j.sid)+"\","
                + "\"class\":\""+esc(j.className)+"\","
                + "\"state\":\""+j.state+"\","
                + "\"exit\":"+j.exit+","
                + "\"startMs\":"+j.startMs+","
                + "\"endMs\":"+j.endMs+","
                + "\"runtimeMs\":"+runtime
                + "}";
        send(ex, 200, "application/json; charset=utf-8", json);
    }

    private void handleJobOutput(HttpExchange ex) throws IOException {
        Map<String,String> qs = QueryUtil.parse(ex.getRequestURI().getRawQuery());
        String id = qs.get("id");
        if (id == null || id.isBlank()) { send(ex, 400, "text/plain; charset=utf-8", "Missing id"); return; }
        Job j = jobs.get(id);
        if (j == null) { send(ex, 404, "text/plain; charset=utf-8", "No such job"); return; }

        try {
            String out = Files.readString(j.stdoutPath, StandardCharsets.UTF_8);
            send(ex, 200, "text/plain; charset=utf-8", out);
        } catch (IOException e) {
            send(ex, 500, "text/plain; charset=utf-8", "Reading output failed: " + e);
        }
    }

    // ---- helpers ----
    private Path persistBodyIfAny(HttpExchange ex) throws IOException {
        String m = ex.getRequestMethod();
        boolean has = "POST".equalsIgnoreCase(m) || "PUT".equalsIgnoreCase(m) || "PATCH".equalsIgnoreCase(m);
        Path postPath = Path.of("build/post", UUID.randomUUID() + ".txt");
        if (has) try (OutputStream out = Files.newOutputStream(postPath)) { ex.getRequestBody().transferTo(out); }
        else Files.writeString(postPath, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return postPath;
    }

    private static String toPascal(String seg) {
        if (seg == null || seg.isEmpty()) return "Echo";
        String s = seg.replaceAll("[^a-zA-Z0-9_]", "");
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + (s.length() > 1 ? s.substring(1) : "");
    }

    private static String esc(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }

    private static void send(HttpExchange ex, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
