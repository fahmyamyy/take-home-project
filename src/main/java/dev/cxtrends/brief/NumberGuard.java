package dev.cxtrends.brief;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects generated text containing any number that does not appear in the facts.
 * An executive brief with one invented figure is worse than a stiff one, so the
 * LLM's prose is only used if every number in it can be traced back.
 */
public final class NumberGuard {

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    /** "1. ", "2) " at the start of a line are list markers, not figures. */
    private static final Pattern LIST_MARKER = Pattern.compile("(?m)^\\s*\\d+[.)]\\s");

    private final Set<String> allowed;

    public NumberGuard(String factsText) {
        this.allowed = new HashSet<>(numbers(factsText));
    }

    /** Numbers in {@code text} that are not in the facts; empty means the text passes. */
    public List<String> unknownNumbers(String text) {
        List<String> unknown = new ArrayList<>();
        for (String n : numbers(LIST_MARKER.matcher(text).replaceAll(""))) {
            if (!allowed.contains(n) && !unknown.contains(n)) {
                unknown.add(n);
            }
        }
        return unknown;
    }

    /** Normalised numbers: thousands separators removed, trailing ".0" dropped. */
    static List<String> numbers(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String n = m.group().replace(",", "");
            if (n.endsWith(".")) {
                n = n.substring(0, n.length() - 1);
            }
            if (n.contains(".")) {
                n = n.replaceAll("\\.?0+$", "");
            }
            out.add(n);
        }
        return out;
    }
}
