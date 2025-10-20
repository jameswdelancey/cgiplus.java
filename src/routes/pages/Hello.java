package routes.pages;

import app.QueryUtil;

import java.time.Instant;
import java.util.Map;

public final class Hello {
    private Hello(){}

    public static void main(String[] args) {
        String rawQ = args.length > 0 ? args[0] : "";
        Map<String, String> qs = QueryUtil.parse(rawQ);
        String name = qs.getOrDefault("name", "friend");

        String body = """
                <!doctype html>
                <html>
                <head>
                  <meta charset=\"utf-8\"/>
                  <title>Hello Page</title>
                  <style>
                    body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;margin:2rem;line-height:1.5}
                    code{background:#f0f0f0;padding:.15rem .3rem;border-radius:6px}
                  </style>
                </head>
                <body>
                  <h1>Hello, %s!</h1>
                  <p>This page is rendered by <code>routes.pages.Hello</code>.</p>
                  <p>Try adding <code>?name=Your+Name</code> to the URL.</p>
                  <p class=\"meta\">Generated at %s.</p>
                </body>
                </html>
                """.stripIndent().formatted(escapeHtml(name), Instant.now());

        System.out.println(body.strip());
    }

    private static String escapeHtml(String s) {
        return s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
