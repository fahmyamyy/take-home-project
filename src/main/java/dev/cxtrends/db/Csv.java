package dev.cxtrends.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal RFC 4180 CSV reader: quoted fields, escaped quotes ({@code ""}) and commas
 * or newlines inside quotes. Enough for the assignment's files without pulling in a
 * dependency.
 */
public final class Csv {

    private Csv() {
    }

    /** Reads a CSV with a header row; each row maps column name to raw value. */
    public static List<Map<String, String>> read(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<List<String>> records = parse(reader);
            if (records.isEmpty()) {
                return List.of();
            }
            List<String> header = records.getFirst();
            List<Map<String, String>> rows = new ArrayList<>(records.size() - 1);
            for (List<String> rec : records.subList(1, records.size())) {
                if (rec.size() == 1 && rec.getFirst().isEmpty()) {
                    continue; // blank line
                }
                if (rec.size() != header.size()) {
                    throw new IllegalStateException("%s: expected %d fields but got %d in %s"
                            .formatted(file, header.size(), rec.size(), rec));
                }
                Map<String, String> row = HashMap.newHashMap(header.size());
                for (int i = 0; i < header.size(); i++) {
                    row.put(header.get(i), rec.get(i));
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("read " + file, e);
        }
    }

    static List<List<String>> parse(BufferedReader reader) throws IOException {
        List<List<String>> records = new ArrayList<>();
        List<String> rec = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean any = false;
        int c;
        while ((c = reader.read()) != -1) {
            any = true;
            char ch = (char) c;
            if (inQuotes) {
                if (ch == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        inQuotes = false;
                        if (next != -1) {
                            reader.reset();
                        }
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                rec.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r') {
                    reader.mark(1);
                    if (reader.read() != '\n') {
                        reader.reset();
                    }
                }
                rec.add(field.toString());
                field.setLength(0);
                records.add(rec);
                rec = new ArrayList<>();
                any = false;
            } else {
                field.append(ch);
            }
        }
        if (any) {
            rec.add(field.toString());
            records.add(rec);
        }
        return records;
    }

    /** Quotes a value for CSV output when needed. */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
