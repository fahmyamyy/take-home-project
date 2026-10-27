package dev.cxtrends;

import java.util.Arrays;
import java.util.Set;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point. The first non-option argument selects what to run:
 *
 * <ul>
 *   <li>{@code serve} (default) — load the data and start the HTTP API</li>
 *   <li>{@code load} — (re)build cx.db from data/ and exit</li>
 *   <li>{@code queries} — run src/main/resources/sql/queries.sql (Part 1)</li>
 *   <li>{@code classify} — classify notes and write the disagreement report (Part 3a)</li>
 *   <li>{@code brief} — generate the weekly CX brief (Part 3b)</li>
 * </ul>
 *
 * The data is reloaded into cx.db on every start, so the database can never drift
 * from the CSVs.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CxTrendsApplication {

    static final Set<String> BATCH_COMMANDS = Set.of("load", "queries", "classify", "brief");

    public static void main(String[] args) {
        boolean batch = Arrays.stream(args).anyMatch(BATCH_COMMANDS::contains);
        new SpringApplicationBuilder(CxTrendsApplication.class)
                .web(batch ? WebApplicationType.NONE : WebApplicationType.SERVLET)
                .run(args);
    }
}
