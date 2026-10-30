package ke.co.scedar.plugins.freemarker.preview.model;

import ke.co.scedar.plugins.freemarker.preview.analysis.Names;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Deterministic, name-aware sample values: {@code customer.name -> "Jane Doe"}, {@code total -> 85000},
 * {@code createdAt -> "2026-01-15T09:30:00"}. The {@code index} varies values between items of a generated list.
 */
public final class SampleValues {

    private static final List<String> PEOPLE = List.of("Jane Doe", "John Smith", "Amina Otieno", "Luis García");
    private static final List<String> FIRST_NAMES = List.of("Jane", "John", "Amina", "Luis");
    private static final List<String> LAST_NAMES = List.of("Doe", "Smith", "Otieno", "García");
    private static final List<String> PRODUCTS = List.of("Laptop", "Monitor", "Keyboard", "Headphones");
    private static final List<String> COMPANIES = List.of("Acme Corp", "Globex Ltd", "Initech", "Umbrella Inc");
    private static final List<String> CITIES = List.of("Nairobi", "London", "Toronto", "Lisbon");
    private static final List<String> COUNTRIES = List.of("Kenya", "United Kingdom", "Canada", "Portugal");
    private static final List<String> STREETS = List.of("221B Baker Street", "742 Evergreen Terrace",
            "12 Kenyatta Avenue", "31 Spooner Street");
    private static final List<String> TITLES = List.of("Getting started", "Release notes", "Quarterly report",
            "Welcome aboard");
    private static final List<String> TEXTS = List.of(
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
            "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
            "Ut enim ad minim veniam, quis nostrud exercitation ullamco.");
    private static final List<String> STATUSES = List.of("ACTIVE", "PENDING", "SHIPPED", "CLOSED");
    private static final List<String> ROLES = List.of("Administrator", "Editor", "Viewer");
    private static final List<String> EMAILS = List.of("jane.doe@example.com", "john.smith@example.com",
            "amina.otieno@example.com", "luis.garcia@example.com");
    private static final List<String> USERNAMES = List.of("jdoe", "jsmith", "aotieno", "lgarcia");
    private static final List<String> COLORS = List.of("#3366FF", "#FF6633", "#33AA77", "#AA33CC");
    private static final List<Integer> AMOUNTS = List.of(85000, 25000, 12000, 4500);
    private static final List<Integer> QUANTITIES = List.of(2, 1, 5, 3);
    private static final List<String> FRACTIONS = List.of("0.92", "0.84", "0.67", "0.45");
    private static final List<String> SCHOOLS = List.of("Riverside Academy", "Hillcrest High School",
            "Lakeside College", "Northgate University");
    private static final List<String> TERMS = List.of("Term 1 2026", "Term 3 2025", "Term 2 2026", "Term 1 2025");
    private static final Set<String> SCHOOL_WORDS = Set.of("institution", "school", "university", "college",
            "academy", "campus");
    private static final Set<String> ORGANIZATION_WORDS = Set.of("client", "config", "organization", "organisation",
            "org", "company", "tenant", "business", "shop", "store", "employer", "vendor", "supplier", "brand", "site",
            "merchant", "partner");
    private static final Set<String> MONEY = Set.of("total", "amount", "price", "cost", "subtotal", "balance",
            "salary", "revenue", "fee", "tax", "discount", "sum", "payment", "income", "budget", "value", "due",
            "fees", "costs", "amounts", "payments", "charges", "outstanding", "collected", "paid", "overpayment",
            "refund", "deposit", "prices", "totals", "earnings", "arrears", "expenses");
    private static final Set<String> COUNTS = Set.of("count", "quantity", "qty", "stock", "items", "units",
            "students", "users", "members", "employees", "customers", "visitors", "views", "clicks", "downloads",
            "attendees", "participants", "pupils", "teachers", "staff", "sales", "enrollments", "subscribers");
    private static final Set<String> NUMERIC_WORDS = Set.of("age", "year", "month", "rating", "score", "stars",
            "percent", "percentage", "rate", "id", "num", "no", "size", "weight", "height", "width", "length",
            "level", "priority", "rank", "page", "position", "index", "duration", "days", "hours", "minutes",
            "number", "version", "port", "capacity", "limit");
    private static final Set<String> BOOLEAN_PREFIXES = Set.of("is", "has", "can", "should", "was", "will",
            "allow", "show", "enable", "use", "needs", "must");
    private static final Set<String> BOOLEAN_WORDS = Set.of("active", "enabled", "visible", "paid", "verified",
            "selected", "checked", "valid", "available", "published", "approved", "completed", "done", "admin",
            "premium", "required", "featured", "online", "subscribed", "confirmed");
    private static final Set<String> IMAGE_WORDS = Set.of("image", "img", "avatar", "photo", "picture", "logo",
            "thumbnail", "icon", "banner");
    private SampleValues() {
    }

    /**
     * Guesses the kind of a value from its name alone (used when the template gives no type hint).
     */
    public static Kind guessKind(String name) {
        List<String> words = Names.words(name);
        if (words.isEmpty()) {
            return Kind.STRING;
        }
        String first = words.get(0);
        String last = words.get(words.size() - 1);
        if (words.size() > 1 && BOOLEAN_PREFIXES.contains(first) || BOOLEAN_WORDS.contains(String.join("", words))) {
            return Kind.BOOLEAN;
        }
        if (last.equals("date") || last.equals("birthday") || last.equals("dob") || last.equals("on") && words.size() > 1) {
            return Kind.DATE;
        }
        if (last.equals("at") && words.size() > 1 || last.equals("timestamp") || last.equals("datetime")) {
            return Kind.DATETIME;
        }
        if (last.equals("time") && words.size() == 1) {
            return Kind.TIME;
        }
        if (MONEY.contains(last) || COUNTS.contains(last) || NUMERIC_WORDS.contains(last)) {
            return Kind.NUMBER;
        }
        return Kind.STRING;
    }

    public static Object of(Kind kind, String name, int index) {
        return switch (kind) {
            case STRING -> string(name, index);
            case NUMBER -> number(name, index);
            case BOOLEAN -> Boolean.TRUE;
            case DATE -> date(index);
            case DATETIME -> dateTime(index);
            case TIME -> time(index);
        };
    }

    public static String string(String name, int index) {
        List<String> words = Names.words(name);
        String last = words.isEmpty() ? "" : words.get(words.size() - 1);
        String prefix = words.size() > 1 ? words.get(words.size() - 2) : "";
        String joined = String.join("", words);

        if (joined.equals("firstname") || joined.equals("givenname")) {
            return pick(FIRST_NAMES, index);
        }
        if (joined.equals("lastname") || joined.equals("surname") || joined.equals("familyname")) {
            return pick(LAST_NAMES, index);
        }
        // "username" is an account handle; "userName" (user + name, also produced for user.name) is a person.
        if (last.equals("username") || last.equals("login") || last.equals("handle") || last.equals("nickname")) {
            return pick(USERNAMES, index);
        }
        if (last.equals("name") || last.equals("title") && Set.of("product", "item").contains(prefix)) {
            return switch (prefix) {
                case "product", "item", "article" -> pick(PRODUCTS, index);
                case "company", "organization", "organisation", "org", "business", "shop", "store", "employer" ->
                        pick(COMPANIES, index);
                case "city", "town" -> pick(CITIES, index);
                case "country" -> pick(COUNTRIES, index);
                case "file" -> "document-" + (index + 1) + ".pdf";
                case "", "full", "display", "customer", "user", "client", "person", "author", "owner", "employee",
                     "contact", "recipient", "sender", "member", "account" -> pick(PEOPLE, index);
                default -> {
                    List<String> context = words.subList(0, words.size() - 1);
                    if (context.stream().anyMatch(SCHOOL_WORDS::contains)) {
                        yield pick(SCHOOLS, index);
                    }
                    if (context.stream().anyMatch(ORGANIZATION_WORDS::contains)) {
                        yield pick(COMPANIES, index);
                    }
                    yield Names.humanize(String.join(" ", context)) + " " + (index + 1);
                }
            };
        }
        if (last.equals("email") || last.equals("mail")) {
            return pick(EMAILS, index);
        }
        if (Set.of("phone", "mobile", "telephone", "tel", "cell", "fax").contains(last)) {
            return "+1 555 01" + String.format("%02d", index % 100);
        }
        if (last.equals("address") || last.equals("street") || last.equals("line1")) {
            return pick(STREETS, index);
        }
        if (last.equals("city") || last.equals("town")) {
            return pick(CITIES, index);
        }
        if (last.equals("country")) {
            return pick(COUNTRIES, index);
        }
        if (Set.of("zip", "postcode", "zipcode").contains(last) || joined.endsWith("postalcode")) {
            return String.format("%05d", 100 + index * 10);
        }
        if (Set.of("company", "organization", "organisation", "employer", "business", "vendor", "supplier")
                .contains(last)) {
            return pick(COMPANIES, index);
        }
        if (last.equals("product")) {
            return pick(PRODUCTS, index);
        }
        if (Set.of("title", "subject", "heading", "headline", "caption").contains(last)) {
            return pick(TITLES, index);
        }
        if (Set.of("description", "summary", "body", "content", "text", "message", "comment", "comments", "note",
                "notes", "bio", "details", "excerpt", "paragraph").contains(last)) {
            return pick(TEXTS, index);
        }
        if (IMAGE_WORDS.contains(last) || words.stream().anyMatch(IMAGE_WORDS::contains)
                && Set.of("path", "url", "src", "uri", "link", "file", "href", "location").contains(last)) {
            return placeholderImage(Names.humanize(name));
        }
        if (Set.of("term", "semester", "trimester", "quarter", "session", "period").contains(last)) {
            return last.equals("term") || last.equals("semester") || last.equals("trimester")
                    ? pick(TERMS, index) : "Q" + (index % 4 + 1) + " 2026";
        }
        if (Set.of("url", "link", "href", "website", "homepage", "uri", "permalink").contains(last)) {
            return "https://example.com/" + String.join("-", words.size() > 1 ? words.subList(0, words.size() - 1)
                    : List.of("page")) + "/" + (index + 1);
        }
        if (last.equals("status") || last.equals("state")) {
            return pick(STATUSES, index);
        }
        if (last.equals("role")) {
            return pick(ROLES, index);
        }
        if (last.equals("color") || last.equals("colour")) {
            return pick(COLORS, index);
        }
        if (last.equals("currency")) {
            return "USD";
        }
        if (Set.of("locale", "lang", "language").contains(last)) {
            return "en";
        }
        if (Set.of("password", "secret", "token", "apikey", "key", "pin").contains(last)) {
            return "********";
        }
        if (Set.of("uuid", "guid").contains(last)) {
            return "3f2a9c1e-7b4d-4e2a-9c1e-00000000000" + (index % 10);
        }
        if (last.equals("sku") || last.equals("code") && Set.of("product", "item", "article").contains(prefix)) {
            return "SKU-" + (1001 + index);
        }
        if (Set.of("code", "reference", "ref").contains(last)) {
            // institutionCode -> "INS-1001": recognizable, and identical for every map keyed by the same name.
            String tag = prefix.isEmpty() ? last : prefix;
            return tag.substring(0, Math.min(3, tag.length())).toUpperCase(java.util.Locale.ROOT) + "-" + (1001 + index);
        }
        if (last.equals("number") || last.equals("id") || last.equals("no")) {
            return (prefix.isEmpty() ? "NO" : prefix.substring(0, Math.min(3, prefix.length())).toUpperCase())
                    + "-2026-" + String.format("%04d", index + 1);
        }
        if (Set.of("type", "kind", "category", "tag", "label", "group", "team", "department").contains(last)) {
            return Names.humanize(last) + " " + (index + 1);
        }
        String humanized = Names.humanize(name);
        return index == 0 ? humanized : humanized + " " + (index + 1);
    }

    public static Number number(String name, int index) {
        List<String> words = Names.words(name);
        String last = words.isEmpty() ? "" : words.get(words.size() - 1);
        if (MONEY.contains(last)) {
            return pick(AMOUNTS, index);
        }
        if (COUNTS.contains(last)) {
            return pick(QUANTITIES, index);
        }
        return switch (last) {
            case "age" -> 34 + index * 7;
            case "year" -> 2026 - index;
            case "month" -> index % 12 + 1;
            case "rating", "stars" -> 5 - index % 3;
            case "score" -> 87 - index * 5;
            case "percent", "percentage", "rate" -> 15 + index * 5;
            case "id", "number", "num", "no" -> 1001 + index;
            case "level", "priority", "rank", "position", "index", "page", "version" -> index + 1;
            default -> List.of(42, 17, 23, 8).get(index % 4);
        };
    }

    /**
     * Ratios such as a collection rate compared with {@code 0.9}.
     */
    public static java.math.BigDecimal fraction(int index) {
        return new java.math.BigDecimal(pick(FRACTIONS, index));
    }

    public static String date(int index) {
        return LocalDate.of(2026, 1, 15).plusDays(index * 7L).toString();
    }

    public static String dateTime(int index) {
        return LocalDateTime.of(2026, 1, 15, 9, 30).plusDays(index).plusHours(index * 2L).toString() + ":00";
    }

    public static String time(int index) {
        return LocalTime.of(9, 30).plusMinutes(index * 45L).toString() + ":00";
    }

    /**
     * A self-contained SVG placeholder, so previews show an image without loading anything from the network.
     */
    static String placeholderImage(String label) {
        String safe = label.replaceAll("[^A-Za-z0-9 ]", "");
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='160' height='100'>"
                + "<rect width='100%' height='100%' fill='#d9dde3'/>"
                + "<text x='50%' y='50%' font-family='sans-serif' font-size='13' fill='#5b6270' "
                + "text-anchor='middle' dominant-baseline='middle'>" + safe + "</text></svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private static <T> T pick(List<T> values, int index) {
        return values.get(Math.floorMod(index, values.size()));
    }

    public enum Kind {STRING, NUMBER, BOOLEAN, DATE, DATETIME, TIME}
}
