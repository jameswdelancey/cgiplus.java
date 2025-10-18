package routes.api;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class Echo {
    // argv[0] is the raw query string, augmented with __post=<file> (and maybe __sid for async start)
    public static void main(String[] args) throws Exception {
        String rawQ = args.length > 0 ? args[0] : "";
        Map<String, String> qs = parseQuery(rawQ);

        String postPath = qs.getOrDefault("__post", "build/post_body.txt");
        String postBody = readFileSilently(postPath);

        JSONObject out = new JSONObject();
        out.put("route", "Echo");
        out.put("queryRaw", rawQ);
        out.put("query", qs);
        out.put("postPath", postPath);
        out.put("postBodyLength", postBody.length());
        out.put("postBodyPreview", postBody.length() > 256 ? postBody.substring(0, 256) : postBody);

        System.out.println(out.toString());
    }

    private static String readFileSilently(String path) {
        try { return Files.readString(Path.of(path), StandardCharsets.UTF_8); }
        catch (IOException e) { return ""; }
    }
    private static Map<String,String> parseQuery(String raw) {
        Map<String,String> m = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return m;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            String k = i >= 0 ? pair.substring(0, i) : pair;
            String v = i >= 0 ? pair.substring(i+1) : "";
            try {
                k = URLDecoder.decode(k, "UTF-8");
                v = URLDecoder.decode(v, "UTF-8");
            } catch (Exception ignored) {}
            m.put(k, v);
        }
        return m;
    }
}
