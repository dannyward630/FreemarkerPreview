package ke.co.scedar.plugins.freemarker.preview.render;

import freemarker.core.Environment;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateDateModel;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateNumberModel;
import freemarker.template.TemplateScalarModel;
import ke.co.scedar.plugins.freemarker.preview.model.SampleModelGenerator;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Converts the JSON sample data into a FreeMarker data model. Only plain data and inert stubs are produced; no
 * application code is ever invoked.
 */
public final class ModelConverter {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern DATETIME_PATTERN = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?(Z|[+-]\\d{2}:\\d{2})?");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\d{2}:\\d{2}(:\\d{2})?");

    private ModelConverter() {
    }

    public static Map<String, Object> toDataModel(Map<String, Object> json) {
        Map<String, Object> model = new LinkedHashMap<>();
        json.forEach((key, value) -> putConverted(model, key, value));
        return model;
    }

    private static void putConverted(Map<String, Object> target, String key, Object value) {
        if (key.endsWith(SampleModelGenerator.METHOD_SUFFIX) && key.length() > 2) {
            target.put(key.substring(0, key.length() - 2), new MethodStub(convert(value)));
        } else if (key.startsWith(SampleModelGenerator.DIRECTIVE_PREFIX) && key.length() > 1) {
            target.put(key.substring(1), new DirectiveStub(value instanceof String s ? s : ""));
        } else {
            target.put(key, convert(value));
        }
    }

    @SuppressWarnings("unchecked")
    static Object convert(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            ((Map<String, Object>) map).forEach((k, v) -> putConverted(converted, k, v));
            return converted;
        }
        if (value instanceof List<?> list) {
            List<Object> converted = new ArrayList<>(list.size());
            list.forEach(item -> converted.add(convert(item)));
            return converted;
        }
        if (value instanceof String s) {
            SampleDate date = SampleDate.parse(s);
            return date != null ? date : s;
        }
        if (value instanceof Number n) {
            return new SampleNumber(n);
        }
        return value;
    }

    /**
     * An ISO-8601 sample value that is both a date and a string, so {@code ${d?string("dd.MM.yyyy")}},
     * {@code d?date} and {@code d?upper_case} all work.
     */
    static final class SampleDate implements TemplateDateModel, TemplateScalarModel {
        private final Date date;
        private final int type;
        private final String text;

        private SampleDate(Date date, int type, String text) {
            this.date = date;
            this.type = type;
            this.text = text;
        }

        static SampleDate parse(String s) {
            ZoneId zone = ZoneId.systemDefault();
            try {
                if (DATE_PATTERN.matcher(s).matches()) {
                    return new SampleDate(Date.from(LocalDate.parse(s).atStartOfDay(zone).toInstant()), DATE, s);
                }
                if (DATETIME_PATTERN.matcher(s).matches()) {
                    String iso = s.replace(' ', 'T');
                    Date d = iso.endsWith("Z") || iso.matches(".*[+-]\\d{2}:\\d{2}$")
                            ? Date.from(OffsetDateTime.parse(iso).toInstant())
                            : Date.from(LocalDateTime.parse(iso).atZone(zone).toInstant());
                    return new SampleDate(d, DATETIME, s);
                }
                if (TIME_PATTERN.matcher(s).matches()) {
                    LocalDateTime t = LocalTime.parse(s).atDate(LocalDate.of(1970, 1, 1));
                    return new SampleDate(Date.from(t.atZone(zone).toInstant()), TIME, s);
                }
            } catch (DateTimeParseException ignored) {
                // Looks like a date but is not a valid one (e.g. 2026-13-40): keep it as a plain string.
            }
            return null;
        }

        @Override
        public Date getAsDate() {
            return date;
        }

        @Override
        public int getDateType() {
            return type;
        }

        @Override
        public String getAsString() {
            return text;
        }
    }

    /**
     * A number that also answers the common {@code BigDecimal} methods, as numbers do in applications whose object
     * wrapper exposes Java methods ({@code amount.divide(total, 4, "roundHalfUp")}, {@code x.compareTo(0)}). The
     * methods are a fixed list implemented here; nothing is invoked reflectively.
     */
    static final class SampleNumber implements TemplateNumberModel, TemplateHashModel {
        private final Number value;

        SampleNumber(Number value) {
            this.value = value;
        }

        @Override
        public Number getAsNumber() {
            return value;
        }

        @Override
        public TemplateModel get(String key) {
            return NumberMethod.NAMES.contains(key) ? new NumberMethod(key, decimal(value)) : null;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    private static final class NumberMethod implements TemplateMethodModelEx {
        static final java.util.Set<String> NAMES = java.util.Set.of("divide", "multiply", "add", "subtract",
                "remainder", "compareTo", "max", "min", "pow", "abs", "negate", "signum", "setScale",
                "stripTrailingZeros", "doubleValue", "floatValue", "intValue", "longValue", "shortValue", "byteValue",
                "intValueExact", "longValueExact", "toPlainString", "scale", "precision", "movePointLeft",
                "movePointRight");

        private final String name;
        private final java.math.BigDecimal self;

        NumberMethod(String name, java.math.BigDecimal self) {
            this.name = name;
            this.self = self;
        }

        @Override
        public Object exec(@SuppressWarnings("rawtypes") List arguments) throws TemplateModelException {
            try {
                return result(switch (name) {
                    case "divide" -> divide(arguments);
                    case "multiply" -> self.multiply(number(arguments, 0));
                    case "add" -> self.add(number(arguments, 0));
                    case "subtract" -> self.subtract(number(arguments, 0));
                    case "remainder" -> self.remainder(number(arguments, 0));
                    case "max" -> self.max(number(arguments, 0));
                    case "min" -> self.min(number(arguments, 0));
                    case "compareTo" -> self.compareTo(number(arguments, 0));
                    case "pow" -> self.pow(number(arguments, 0).intValueExact());
                    case "abs" -> self.abs();
                    case "negate" -> self.negate();
                    case "signum" -> self.signum();
                    case "setScale" -> arguments.size() > 1
                            ? self.setScale(number(arguments, 0).intValueExact(), rounding(arguments.get(1)))
                            : self.setScale(number(arguments, 0).intValueExact(), java.math.RoundingMode.UNNECESSARY);
                    case "stripTrailingZeros" -> self.stripTrailingZeros();
                    case "movePointLeft" -> self.movePointLeft(number(arguments, 0).intValueExact());
                    case "movePointRight" -> self.movePointRight(number(arguments, 0).intValueExact());
                    case "doubleValue", "floatValue" -> self.doubleValue();
                    case "intValue", "intValueExact", "shortValue", "byteValue" -> self.intValue();
                    case "longValue", "longValueExact" -> self.longValue();
                    case "scale" -> self.scale();
                    case "precision" -> self.precision();
                    case "toPlainString" -> self.toPlainString();
                    default -> throw new TemplateModelException("Unsupported method " + name);
                });
            } catch (ArithmeticException e) {
                throw new TemplateModelException(name + "(...) failed: " + e.getMessage(), e);
            }
        }

        private java.math.BigDecimal divide(List<?> arguments) throws TemplateModelException {
            java.math.BigDecimal divisor = number(arguments, 0);
            if (arguments.size() >= 3) {
                return self.divide(divisor, number(arguments, 1).intValueExact(), rounding(arguments.get(2)));
            }
            if (arguments.size() == 2) {
                return self.divide(divisor, rounding(arguments.get(1)));
            }
            return self.divide(divisor, java.math.MathContext.DECIMAL64);
        }

        private static Object result(Object value) {
            return value instanceof Number n ? new SampleNumber(n) : new SimpleScalar(String.valueOf(value));
        }

        private java.math.BigDecimal number(List<?> arguments, int index) throws TemplateModelException {
            if (arguments.size() <= index) {
                throw new TemplateModelException(name + "(...) needs " + (index + 1) + " argument(s)");
            }
            Object argument = arguments.get(index);
            if (argument instanceof TemplateNumberModel number) {
                return decimal(number.getAsNumber());
            }
            if (argument instanceof TemplateScalarModel scalar) {
                try {
                    return new java.math.BigDecimal(scalar.getAsString().trim());
                } catch (NumberFormatException ignored) {
                    // reported below
                }
            }
            throw new TemplateModelException(name + "(...) expects a number as argument " + (index + 1));
        }

        /** {@code "roundHalfUp"}, {@code "HALF_UP"}, or a legacy {@code BigDecimal.ROUND_*} int. */
        private java.math.RoundingMode rounding(Object argument) throws TemplateModelException {
            if (argument instanceof TemplateNumberModel number) {
                return java.math.RoundingMode.valueOf(number.getAsNumber().intValue());
            }
            if (argument instanceof TemplateScalarModel scalar) {
                String text = scalar.getAsString().trim();
                if (text.regionMatches(true, 0, "round", 0, 5)) {
                    text = text.substring(5);
                }
                String constant = text.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(java.util.Locale.ROOT);
                try {
                    return java.math.RoundingMode.valueOf(constant);
                } catch (IllegalArgumentException ignored) {
                    // reported below
                }
            }
            throw new TemplateModelException(name + "(...): unknown rounding mode " + argument);
        }
    }

    private static java.math.BigDecimal decimal(Number n) {
        if (n instanceof java.math.BigDecimal d) {
            return d;
        }
        if (n instanceof Double || n instanceof Float) {
            return java.math.BigDecimal.valueOf(n.doubleValue());
        }
        return new java.math.BigDecimal(n.toString());
    }

    /** Stands in for a method: returns the sample value regardless of the arguments. */
    static final class MethodStub implements TemplateMethodModelEx {
        private final Object result;

        MethodStub(Object result) {
            this.result = result;
        }

        @Override
        public Object exec(@SuppressWarnings("rawtypes") List arguments) {
            return result;
        }
    }

    /** Stands in for a user-defined directive: prints its sample text, then renders the nested content once. */
    static final class DirectiveStub implements TemplateDirectiveModel {
        private final String output;

        DirectiveStub(String output) {
            this.output = output;
        }

        @Override
        public void execute(Environment env, @SuppressWarnings("rawtypes") Map params, TemplateModel[] loopVars,
                            TemplateDirectiveBody body) throws freemarker.template.TemplateException, IOException {
            env.getOut().write(output);
            for (int i = 0; i < loopVars.length; i++) {
                loopVars[i] = new SimpleScalar("item" + (i + 1));
            }
            if (body != null) {
                body.render(env.getOut());
            }
        }
    }
}
