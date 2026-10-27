package dev.cxtrends.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void parsesQuotedFieldsWithCommasQuotesAndNewlines() throws Exception {
        String csv = "id,note\r\n1,plain\n2,\"a, b\"\n3,\"he said \"\"hi\"\"\"\n4,\"two\nlines\"\n5,\n";

        List<List<String>> records = Csv.parse(new BufferedReader(new StringReader(csv)));

        assertThat(records).containsExactly(
                List.of("id", "note"), List.of("1", "plain"), List.of("2", "a, b"),
                List.of("3", "he said \"hi\""), List.of("4", "two\nlines"), List.of("5", ""));
    }

    @Test
    void escapeRoundTrips() throws Exception {
        String value = "x, \"y\"";
        List<List<String>> records = Csv.parse(new BufferedReader(new StringReader(Csv.escape(value))));

        assertThat(records.getFirst()).containsExactly(value);
    }
}
