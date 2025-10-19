package routes.api;

import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URLDecoder;

public class LongDemo {
    // Simulate a long task. Use ?seconds=N (default 5). Reads __sid if provided.
    public static void main(String[] args) throws Exception {
        String rawQ = args.length > 0 ? args[0] : "";
        Map<String,String> qs = parseQuery(rawQ);
        int seconds = parseInt(qs.getOrDefault("seconds", "5"), 5);
        String sid = qs.getOrDefault("__sid", "");

        long start = System.currentTimeMillis();
        Thread.sleep(Math.max(0, seconds) * 1000L);
        long end = System.currentTimeMillis();

        JSONObject out = new JSONObject();
        out.put("route", "LongDemo");
        out.put("sid", sid);
        out.put("sleptSeconds", seconds);
        out.put("startedMs", start);
        out.put("endedMs", end);
        out.put("durationMs", end - start);
        System.out.println(out.toString());
    }

    private static int parseInt(String s, int d){ try { return Integer.parseInt(s); } catch(Exception e){ return d; } }
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
