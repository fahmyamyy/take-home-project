package dev.cxtrends.classify;

import java.util.List;
import java.util.Locale;

/**
 * Separates the complaint itself from the "I'm contacting you again" wrapper agents
 * record around it.
 *
 * <p>Notes for repeat contacts are the original complaint with one or more fixed
 * prefixes in front, e.g. {@code "this is a follow up, still not resolved: sinyal
 * hilang total sejak kemarin"}. Stripping them reduces 42,751 notes to 91 distinct
 * complaint texts, and the number stripped is itself a repeat-contact signal.
 */
public final class NoteNormalizer {

    /** Lower-case prefixes observed in the data; they can be nested. */
    static final List<String> FOLLOW_UP_PREFIXES = List.of(
            "follow up dari laporan kemarin, ",
            "this is a follow up, ",
            "sudah lapor sebelumnya tapi belum ada solusi, ",
            "second time reporting the same issue, ",
            "komplain ulang karena masalah belum selesai, ",
            "still not resolved: ");

    private NoteNormalizer() {
    }

    /**
     * @param baseText      the complaint with all follow-up prefixes removed
     * @param followUpDepth how many prefixes were removed (0 = first contact)
     */
    public record Normalized(String baseText, int followUpDepth) {
    }

    public static Normalized normalize(String note) {
        String text = note.strip();
        int depth = 0;
        boolean stripped;
        do {
            stripped = false;
            String lower = text.toLowerCase(Locale.ROOT);
            for (String prefix : FOLLOW_UP_PREFIXES) {
                if (lower.startsWith(prefix)) {
                    text = text.substring(prefix.length()).strip();
                    depth++;
                    stripped = true;
                    break;
                }
            }
        } while (stripped);
        return new Normalized(text, depth);
    }
}
