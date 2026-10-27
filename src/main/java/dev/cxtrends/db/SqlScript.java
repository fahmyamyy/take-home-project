package dev.cxtrends.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a .sql script on the classpath (src/main/resources/sql/) into statements. A statement ends at a line whose last
 * non-blank character is {@code ;}. Comment lines directly above a statement are
 * kept as its description; a comment starting with {@code -- Q<n>.} names it.
 */
public final class SqlScript {

    private static final Pattern TITLE = Pattern.compile("^--\\s*(Q\\d+[a-z]?)\\.\\s*(.*)$");

    public record Statement(String title, String description, String sql) {
    }

    private SqlScript() {
    }

    public static List<Statement> read(String resource) {
        try (InputStream in = SqlScript.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource " + resource);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException("read " + resource, e);
        }
    }

    static List<Statement> parse(List<String> lines) {
        List<Statement> statements = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        String title = null;
        String description = null;
        for (String line : lines) {
            String trimmed = line.strip();
            if (sql.isEmpty() && trimmed.startsWith("--")) {
                Matcher m = TITLE.matcher(trimmed);
                if (m.matches()) {
                    title = m.group(1);
                    description = m.group(2);
                }
                continue;
            }
            if (sql.isEmpty() && trimmed.isEmpty()) {
                continue;
            }
            sql.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String text = sql.toString().strip();
                statements.add(new Statement(title, description, text.substring(0, text.length() - 1)));
                sql.setLength(0);
                title = null;
                description = null;
            }
        }
        return statements;
    }
}
