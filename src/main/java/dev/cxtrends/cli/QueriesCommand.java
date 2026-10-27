package dev.cxtrends.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.springframework.stereotype.Component;

import dev.cxtrends.CxProperties;
import dev.cxtrends.db.Csv;
import dev.cxtrends.db.SqlScript;

/**
 * Part 1: runs every statement in src/main/resources/sql/queries.sql, prints the first rows of each
 * result and writes the full result to output/queries/&lt;Qn&gt;.csv.
 */
@Component
public class QueriesCommand {

    private static final int PREVIEW_ROWS = 15;

    private final JdbcTemplate jdbc;
    private final CxProperties props;

    public QueriesCommand(JdbcTemplate jdbc, CxProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public void run() {
        Path outDir = props.outputDir().resolve("queries");
        for (SqlScript.Statement s : SqlScript.read("/sql/queries.sql")) {
            SqlRowSet rs = jdbc.queryForRowSet(s.sql());
            SqlRowSetMetaData meta = rs.getMetaData();
            List<String> columns = List.of(meta.getColumnNames());
            List<List<String>> rows = new ArrayList<>();
            while (rs.next()) {
                List<String> row = new ArrayList<>(columns.size());
                for (int i = 1; i <= columns.size(); i++) {
                    Object v = rs.getObject(i);
                    row.add(v == null ? "" : format(v));
                }
                rows.add(row);
            }
            String name = s.title() == null ? "query" : s.title();
            System.out.printf("%n== %s. %s (%d rows, full result in %s)%n", name, s.description(), rows.size(),
                    outDir.resolve(name + ".csv"));
            printTable(columns, rows.subList(0, Math.min(PREVIEW_ROWS, rows.size())));
            writeCsv(outDir.resolve(name + ".csv"), columns, rows);
        }
    }

    private static String format(Object v) {
        if (v instanceof Double d) {
            return d == Math.rint(d) && Math.abs(d) < 1e15
                    ? String.valueOf(d.longValue())
                    : String.format(Locale.ROOT, "%.4f", d).replaceAll("0+$", "");
        }
        return v.toString();
    }

    private static void printTable(List<String> columns, List<List<String>> rows) {
        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            widths[i] = columns.get(i).length();
            for (List<String> row : rows) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }
        StringBuilder sb = new StringBuilder();
        appendRow(sb, columns, widths);
        for (int i = 0; i < widths.length; i++) {
            sb.append("-".repeat(widths[i])).append(i < widths.length - 1 ? "  " : "\n");
        }
        rows.forEach(r -> appendRow(sb, r, widths));
        System.out.print(sb);
    }

    private static void appendRow(StringBuilder sb, List<String> cells, int[] widths) {
        for (int i = 0; i < cells.size(); i++) {
            sb.append(String.format("%-" + widths[i] + "s", cells.get(i))).append(i < cells.size() - 1 ? "  " : "\n");
        }
    }

    private static void writeCsv(Path file, List<String> columns, List<List<String>> rows) {
        List<String> lines = new ArrayList<>(rows.size() + 1);
        lines.add(String.join(",", columns.stream().map(Csv::escape).toList()));
        rows.forEach(r -> lines.add(String.join(",", r.stream().map(Csv::escape).toList())));
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines);
        } catch (IOException e) {
            throw new UncheckedIOException("write " + file, e);
        }
    }
}
