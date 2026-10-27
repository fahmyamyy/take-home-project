package dev.cxtrends.cli;

import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import dev.cxtrends.brief.BriefCommand;
import dev.cxtrends.classify.ClassifyCommand;

/**
 * Runs the batch command named on the command line, if any. {@code load} needs no
 * work here: the data is loaded at start-up by {@link dev.cxtrends.db.DataLoader}.
 */
@Component
public class CommandRunner implements ApplicationRunner {

    private final Map<String, Runnable> commands;

    public CommandRunner(QueriesCommand queries, ClassifyCommand classify, BriefCommand brief) {
        this.commands = Map.of(
                "load", () -> { },
                "queries", queries::run,
                "classify", classify::run,
                "brief", brief::run);
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> names = args.getNonOptionArgs();
        for (String name : names) {
            Runnable command = commands.get(name);
            if (command == null) {
                throw new IllegalArgumentException("unknown command '%s'; expected one of %s or serve"
                        .formatted(name, commands.keySet()));
            }
            command.run();
        }
    }
}
