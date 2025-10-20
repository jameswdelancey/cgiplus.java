package adapters.in.http;

import app.JobService;
import app.QueryUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import domain.Job;
import ports.RouteExecutorPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

        String path = ex.getRequestURI().getPath();
        if (path == null || path.isBlank()) path = "/";

        Path staticFile = resolveStaticPath(path);
        if (staticFile != null && Files.isRegularFile(staticFile)) {
            Headers h = ex.getResponseHeaders();
            String contentType = Files.probeContentType(staticFile);
            if (contentType == null || contentType.isBlank()) contentType = guessContentType(staticFile);
            h.set("Content-Type", contentType);
            long len = Files.size(staticFile);
            ex.sendResponseHeaders(200, len);
            try (OutputStream os = ex.getResponseBody();
                 InputStream in = Files.newInputStream(staticFile)) {
                in.transferTo(os);
            }
            return;
        }

        handlePageRender(ex, path);
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
        Files.createDirectories(postPath.getParent());
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

    private void handlePageRender(HttpExchange ex, String path) throws IOException {
        String[] parts = path.split("/");
        StringBuilder cls = new StringBuilder("routes.pages.");
        boolean has = false;
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            String sanitized = part.replaceAll("[^a-zA-Z0-9_]", "");
            if (sanitized.isEmpty()) continue;
            cls.append(toPascal(sanitized));
            has = true;
        }
        if (!has) cls.append("Index");

        String rawQ = ex.getRequestURI().getRawQuery();
        if (rawQ == null) rawQ = "";

        RouteExecutorPort.ExecResult r = jobs.execSync(cls.toString(), rawQ);
        if (r.exit != 0) {
            String stderr = r.stderr == null ? "" : r.stderr;
            if (stderr.contains("Could not find or load main class")) {
                send(ex, 404, "text/plain; charset=utf-8", "Page not found");
            } else {
                send(ex, 500, "text/plain; charset=utf-8",
                        "Route process failed (exit " + r.exit + ")\n" + stderr);
            }
        } else {
            send(ex, 200, "text/html; charset=utf-8", r.stdout);
        }
    }

    private Path resolveStaticPath(String path) {
        String clean = path;
        if (clean.endsWith("/")) clean = clean + "index.html";
        if ("/".equals(clean)) clean = "/index.html";
        clean = clean.replace('\\', '/');
        if (clean.startsWith("/")) clean = clean.substring(1);

        Path root = Path.of("static").toAbsolutePath().normalize();
        Path resolved = root.resolve(clean).normalize();
        if (!resolved.startsWith(root)) return null;
        if (Files.isDirectory(resolved)) {
            Path idx = resolved.resolve("index.html");
            if (Files.exists(idx)) return idx;
        }
        if (Files.exists(resolved)) return resolved;
        return null;
    }

    private String guessContentType(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }

    private static void send(HttpExchange ex, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
