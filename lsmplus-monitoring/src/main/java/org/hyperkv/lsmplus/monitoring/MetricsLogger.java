package org.hyperkv.lsmplus.monitoring;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class MetricsLogger implements MetricsListener, Closeable {

    private final Path logFilePath;
    private final Writer writer;
    private final DateTimeFormatter formatter;
    private volatile boolean closed;

    public MetricsLogger(String logFilePath) throws IOException {
        this.logFilePath = Paths.get(logFilePath);
        this.formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        this.closed = false;

        Path parentDir = this.logFilePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        this.writer = new BufferedWriter(
            new OutputStreamWriter(
                new FileOutputStream(this.logFilePath.toFile(), true),
                java.nio.charset.StandardCharsets.UTF_8
            )
        );

        writeHeader();
    }

    private void writeHeader() throws IOException {
        writer.write("# Performance Counter Log\n");
        writer.write("# Started at: " + LocalDateTime.now().format(formatter) + "\n");
        writer.write("# Format: timestamp | counter_name | count | errors | mean(μs) | min(μs) | max(μs) | p50(μs) | p75(μs) | p90(μs) | p99(μs) | [field=(avg,min,max,total) ...]\n");
        writer.write("#" + "=".repeat(150) + "\n");
        writer.flush();
    }

    @Override
    public void onSnapshot(MetricsSnapshot snapshot) {
        if (closed) {
            return;
        }

        try {
            String timestamp = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(snapshot.getTimestamp()),
                ZoneId.systemDefault()
            ).format(formatter);

            for (CounterSnapshot counter : snapshot.getCounters().values()) {
                if (counter.getCount() > 0) {
                    writer.write(String.format(
                        "%s | %-30s | %8d | %7d | %9.2f | %7d | %7d | %7d | %7d | %7d | %7d\n",
                        timestamp,
                        counter.getName(),
                        counter.getCount(),
                        counter.getErrorCount(),
                        counter.getMean(),
                        counter.getMin(),
                        counter.getMax(),
                        counter.getP50(),
                        counter.getP75(),
                        counter.getP90(),
                        counter.getP99()
                    ));
                }
            }

            // Log extended counter additional fields in single line
            for (ExtendedCounterSnapshot extCounter : snapshot.getExtendedCounters().values()) {
                if (extCounter.getCount() > 0) {
                    StringBuilder fieldsBuilder = new StringBuilder();
                    for (Map.Entry<String, ExtendedPerformanceCounter.FieldSnapshot> fieldEntry : extCounter.getFieldSnapshots().entrySet()) {
                        ExtendedPerformanceCounter.FieldSnapshot field = fieldEntry.getValue();
                        if (field.getCount() > 0) {
                            if (fieldsBuilder.length() > 0) {
                                fieldsBuilder.append(" | ");
                            }
                            fieldsBuilder.append(String.format("%s=(%.2f, %d, %d, %d)",
                                fieldEntry.getKey(),
                                field.getAverage(),
                                field.getMin(),
                                field.getMax(),
                                field.getTotal()
                            ));
                        }
                    }
                    
                    if (fieldsBuilder.length() > 0) {
                        writer.write(String.format(
                            "%s | %-30s | %8d | %7d | %9.2f | %7d | %7d | %7d | %7d | %7d | %7d | %s\n",
                            timestamp,
                            extCounter.getName(),
                            extCounter.getCount(),
                            extCounter.getErrorCount(),
                            extCounter.getMean(),
                            extCounter.getMin(),
                            extCounter.getMax(),
                            extCounter.getP50(),
                            extCounter.getP75(),
                            extCounter.getP90(),
                            extCounter.getP99(),
                            fieldsBuilder.toString()
                        ));
                    }
                }
            }

            writer.flush();

        } catch (IOException e) {
            System.err.println("Error writing metrics to log: " + e.getMessage());
        }
    }

    @Override
    public void onHealthCheckChanged(String name, HealthCheckResult result) {
        if (closed) {
            return;
        }

        try {
            String timestamp = LocalDateTime.now().format(formatter);
            writer.write(String.format(
                "%s | HEALTH_CHECK | %-20s | %s | %s\n",
                timestamp,
                name,
                result.getStatus(),
                result.getMessage()
            ));
            writer.flush();

        } catch (IOException e) {
            System.err.println("Error writing health check to log: " + e.getMessage());
        }
    }

    public void logCustomMessage(String message) {
        if (closed) {
            return;
        }

        try {
            String timestamp = LocalDateTime.now().format(formatter);
            writer.write(String.format("%s | %s\n", timestamp, message));
            writer.flush();

        } catch (IOException e) {
            System.err.println("Error writing custom message to log: " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;
        try {
            writer.write("#" + "=".repeat(120) + "\n");
            writer.write("# Log closed at: " + LocalDateTime.now().format(formatter) + "\n");
            writer.flush();
            writer.close();

        } catch (IOException e) {
            System.err.println("Error closing metrics log: " + e.getMessage());
        }
    }

    public Path getLogFilePath() {
        return logFilePath;
    }
}
