package ke.co.scedar.plugins.freemarker.preview.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal strict JSON (RFC 8259) reader and pretty printer for the sample-data editor.
 * <p>
 * Objects become {@link LinkedHashMap} (key order preserved, {@code null} values kept), arrays {@link ArrayList},
 * integers {@link Long} (or {@link BigDecimal} when too large), other numbers {@link BigDecimal}.
 * Errors report line and column so the preview can point at the mistake.
 */
public final class Json {

    private static final int MAX_DEPTH = 64;

    private Json() {
    }

    public static Object parse(String text) throws ParseException {
        Reader reader = new Reader(text);
        reader.skipWhitespace();
        Object value = reader.value(0);
        reader.skipWhitespace();
        if (!reader.atEnd()) {
            throw reader.error("Unexpected content after the JSON value");
        }
        return value;
    }

    /**
     * Parses a JSON object (the sample data model must be one).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) throws ParseException {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new ParseException("The sample data must be a JSON object, e.g. { \"name\": \"Jane\" }", 1, 1);
        }
        return (Map<String, Object>) value;
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.append('\n').toString();
    }

    private static void write(Object value, StringBuilder out, int indent) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            quote(s, out);
        } else if (value instanceof BigDecimal d) {
            out.append(d.toPlainString());
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                indent(out, indent + 1);
                quote(String.valueOf(e.getKey()), out);
                out.append(": ");
                write(e.getValue(), out, indent + 1);
                out.append(++i < map.size() ? ",\n" : "\n");
            }
            indent(out, indent);
            out.append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                indent(out, indent + 1);
                write(list.get(i), out, indent + 1);
                out.append(i + 1 < list.size() ? ",\n" : "\n");
            }
            indent(out, indent);
            out.append(']');
        } else {
            quote(value.toString(), out);
        }
    }

    private static void indent(StringBuilder out, int level) {
        out.append("  ".repeat(level));
    }

    private static void quote(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    public static final class ParseException extends Exception {
        private final int line;
        private final int column;

        ParseException(String message, int line, int column) {
            super(message + " (line " + line + ", column " + column + ")");
            this.line = line;
            this.column = column;
        }

        public int line() {
            return line;
        }

        public int column() {
            return column;
        }
    }

    private static final class Reader {
        private final String text;
        private int pos;

        Reader(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        void skipWhitespace() {
            while (!atEnd()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object value(int depth) throws ParseException {
            if (depth > MAX_DEPTH) {
                throw error("JSON is nested too deeply");
            }
            if (atEnd()) {
                throw error("Unexpected end of JSON");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> object(depth);
                case '[' -> array(depth);
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield number();
                    }
                    throw error("Unexpected character '" + c + "'");
                }
            };
        }

        private Map<String, Object> object(int depth) throws ParseException {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw error("Expected a quoted property name");
                }
                int keyPos = pos;
                String key = string();
                if (map.containsKey(key)) {
                    pos = keyPos;
                    throw error("Duplicate property \"" + key + "\"");
                }
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, value(depth + 1));
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek('}')) {
                    pos++;
                    return map;
                } else {
                    throw error("Expected ',' or '}'");
                }
            }
        }

        private List<Object> array(int depth) throws ParseException {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(value(depth + 1));
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek(']')) {
                    pos++;
                    return list;
                } else {
                    throw error("Expected ',' or ']'");
                }
            }
        }

        private String string() throws ParseException {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("Unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw error("Unterminated string");
                    }
                    char e = text.charAt(pos++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > text.length()) {
                                throw error("Invalid \\u escape");
                            }
                            try {
                                sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw error("Invalid \\u escape");
                            }
                            pos += 4;
                        }
                        default -> {
                            pos--;
                            throw error("Invalid escape '\\" + e + "'");
                        }
                    }
                } else if (c < 0x20) {
                    pos--;
                    throw error("Control character in string (use \\n for line breaks)");
                } else {
                    sb.append(c);
                }
            }
        }

        private Object number() throws ParseException {
            int start = pos;
            if (peek('-')) {
                pos++;
            }
            if (peek('0')) {
                pos++;
            } else if (!digits()) {
                throw error("Invalid number");
            }
            boolean integral = true;
            if (peek('.')) {
                pos++;
                integral = false;
                if (!digits()) {
                    throw error("Expected digits after the decimal point");
                }
            }
            if (peek('e') || peek('E')) {
                pos++;
                integral = false;
                if (peek('+') || peek('-')) {
                    pos++;
                }
                if (!digits()) {
                    throw error("Expected digits in the exponent");
                }
            }
            String literal = text.substring(start, pos);
            if (integral) {
                try {
                    return Long.parseLong(literal);
                } catch (NumberFormatException tooLarge) {
                    return new BigDecimal(literal);
                }
            }
            return new BigDecimal(literal);
        }

        private boolean digits() {
            int start = pos;
            while (!atEnd() && Character.isDigit(text.charAt(pos)) && text.charAt(pos) < 128) {
                pos++;
            }
            return pos > start;
        }

        private Object literal(String word, Object value) throws ParseException {
            if (!text.startsWith(word, pos)) {
                throw error("Unexpected token (strings must be in double quotes)");
            }
            pos += word.length();
            return value;
        }

        private boolean peek(char c) {
            return !atEnd() && text.charAt(pos) == c;
        }

        private void expect(char c) throws ParseException {
            if (!peek(c)) {
                throw error("Expected '" + c + "'");
            }
            pos++;
        }

        ParseException error(String message) {
            int line = 1;
            int column = 1;
            for (int i = 0; i < Math.min(pos, text.length()); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new ParseException(message, line, column);
        }
    }
}
