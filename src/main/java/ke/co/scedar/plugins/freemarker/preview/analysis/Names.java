package ke.co.scedar.plugins.freemarker.preview.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small English-name helpers used to name list items and to pick realistic sample values.
 */
public final class Names {

    private static final Map<String, String> IRREGULAR = Map.of(
            "people", "person", "children", "child", "men", "man", "women", "woman",
            "data", "datum", "statuses", "status", "addresses", "address", "indices", "index");

    private static final String[] COLLECTION_SUFFIXES = {"List", "Set", "Array", "Collection"};

    private Names() {
    }

    /**
     * {@code orders -> order}, {@code categories -> category}, {@code orderList -> order}, {@code data -> datum}.
     */
    public static String singular(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String irregular = IRREGULAR.get(lower);
        if (irregular != null) {
            return irregular;
        }
        for (String suffix : COLLECTION_SUFFIXES) {
            if (name.length() > suffix.length() && name.endsWith(suffix)) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        if (lower.endsWith("ies") && name.length() > 3) {
            return name.substring(0, name.length() - 3) + "y";
        }
        if (lower.endsWith("sses") || lower.endsWith("shes") || lower.endsWith("ches") || lower.endsWith("xes")
                || lower.endsWith("zes")) {
            return name.substring(0, name.length() - 2);
        }
        if (lower.endsWith("s") && !lower.endsWith("ss") && !lower.endsWith("us") && !lower.endsWith("is")
                && name.length() > 1) {
            return name.substring(0, name.length() - 1);
        }
        return name + "Item";
    }

    /**
     * Splits {@code customerName}, {@code customer_name} or {@code CUSTOMER-NAME} into lower-case words.
     */
    public static List<String> words(String name) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char[] chars = name.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (!Character.isLetterOrDigit(c)) {
                flush(words, current);
                continue;
            }
            boolean boundary = Character.isUpperCase(c) && i > 0
                    && (Character.isLowerCase(chars[i - 1])
                    || (i + 1 < chars.length && Character.isLowerCase(chars[i + 1]) && Character.isUpperCase(chars[i - 1])));
            if (boundary) {
                flush(words, current);
            }
            current.append(Character.toLowerCase(c));
        }
        flush(words, current);
        return words;
    }

    /**
     * {@code orderNote -> "Order note"}.
     */
    public static String humanize(String name) {
        List<String> words = words(name);
        if (words.isEmpty()) {
            return "Value";
        }
        String joined = String.join(" ", words);
        return Character.toUpperCase(joined.charAt(0)) + joined.substring(1);
    }

    private static void flush(List<String> words, StringBuilder current) {
        if (!current.isEmpty()) {
            words.add(current.toString());
            current.setLength(0);
        }
    }
}
