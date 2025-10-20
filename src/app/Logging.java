package app;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class Logging {
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());
    private static boolean initialized = false;

    private Logging() {}

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            Path logDir = Path.of("build", "logs");
            Files.createDirectories(logDir);
            Path logFile = logDir.resolve("app.log");

            Logger root = Logger.getLogger("");
            root.setLevel(Level.INFO);
            for (Handler h : root.getHandlers()) {
                root.removeHandler(h);
            }

            FileHandler fileHandler = new FileHandler(logFile.toString(), true);
            fileHandler.setLevel(Level.INFO);
            fileHandler.setFormatter(new MinimalFormatter());
            root.addHandler(fileHandler);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize logging", e);
        }
        initialized = true;
    }

    private static class MinimalFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();
            sb.append(TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(record.getMillis())));
            sb.append(' ');
            sb.append(record.getLevel().getName());
            sb.append(' ');
            String loggerName = record.getLoggerName();
            if (loggerName != null && !loggerName.isBlank()) {
                sb.append('[').append(loggerName).append(']').append(' ');
            }
            sb.append(formatMessage(record)).append(System.lineSeparator());
            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(sw));
                sb.append(sw);
            }
            return sb.toString();
        }
    }
}
