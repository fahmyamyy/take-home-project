package dev.cxtrends.classify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The topic taxonomy for note classification: the agents' own categories and
 * subcategories, so our label and theirs can be compared one-to-one. Each
 * subcategory gets a one-line definition — the agents work without one, which is
 * part of why their labels disagree with each other.
 */
public final class Taxonomy {

    public record Topic(String category, String subcategory, String definition) {

        public String label() {
            return category + " > " + subcategory;
        }
    }

    public static final List<Topic> TOPICS = List.of(
            new Topic("Network Quality", "No signal", "no network at all / zero bars / emergency calls only, anywhere"),
            new Topic("Network Quality", "Call drop", "voice calls connect but disconnect mid-call"),
            new Topic("Network Quality", "Slow data", "mobile internet is slow or buffering while signal is present"),
            new Topic("Network Quality", "Indoor coverage", "signal fine outdoors but weak or absent indoors"),

            new Topic("Billing & Charging", "Package charged but not active",
                    "money or balance was deducted for a package, but the package or its quota never arrived"),
            new Topic("Billing & Charging", "Double charge", "charged twice for one purchase"),
            new Topic("Billing & Charging", "Balance deducted unexpectedly",
                    "balance went down with no purchase or usage by the customer"),
            new Topic("Billing & Charging", "Wrong tariff", "charged a different rate than advertised or agreed"),

            new Topic("Package & Quota", "Package not active after purchase",
                    "purchase shows as successful but the package status is not active (no mention of a deduction)"),
            new Topic("Package & Quota", "Quota drained fast", "quota used up much faster than the customer's usage explains"),
            new Topic("Package & Quota", "Quota not as advertised", "package contains less quota than described"),
            new Topic("Package & Quota", "Auto-renew unwanted", "package renewed automatically without consent, or wants auto-renew off"),

            new Topic("Top-up & Voucher", "Top-up not received", "paid for a top-up / recharge but the balance never arrived"),
            new Topic("Top-up & Voucher", "Wrong amount credited", "top-up arrived but with a smaller amount than paid"),
            new Topic("Top-up & Voucher", "Voucher code rejected", "a physical or digital voucher code is refused"),
            new Topic("Top-up & Voucher", "Channel transaction failed",
                    "the payment channel (bank, e-wallet, outlet) failed the transaction"),

            new Topic("App & Digital", "Cannot login", "cannot log into the operator's app"),
            new Topic("App & Digital", "OTP not received", "verification / OTP code never arrives"),
            new Topic("App & Digital", "App crash", "app closes or crashes"),
            new Topic("App & Digital", "Payment failed in app", "checkout / payment inside the operator's app fails"),

            new Topic("Registration & SIM", "Registration failed", "SIM registration with identity data fails"),
            new Topic("Registration & SIM", "SIM blocked", "SIM or number blocked unexpectedly"),
            new Topic("Registration & SIM", "Number recycled", "the customer's old number was recycled / expired"),
            new Topic("Registration & SIM", "SIM replacement", "lost or damaged SIM, wants a replacement keeping the number"),

            new Topic("Others", "General enquiry", "a question about services, not a complaint"),
            new Topic("Others", "Feedback", "suggestion or feedback on the service, not a specific problem"),
            new Topic("Others", "Unclassified", "the note carries no information about the problem at all"));

    private static final Map<String, Topic> BY_LABEL = new LinkedHashMap<>();

    static {
        TOPICS.forEach(t -> BY_LABEL.put(t.label().toLowerCase(), t));
    }

    private Taxonomy() {
    }

    /** Finds a topic by its "Category > Subcategory" label, ignoring case; null if unknown. */
    public static Topic findByLabel(String label) {
        return label == null ? null : BY_LABEL.get(label.strip().toLowerCase());
    }
}
