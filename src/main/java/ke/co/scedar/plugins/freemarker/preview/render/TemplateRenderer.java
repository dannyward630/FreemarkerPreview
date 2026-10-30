package ke.co.scedar.plugins.freemarker.preview.render;

import freemarker.core.*;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;
import ke.co.scedar.plugins.freemarker.preview.engine.PreviewConfiguration;
import ke.co.scedar.plugins.freemarker.preview.engine.TemplateSource;
import ke.co.scedar.plugins.freemarker.preview.render.RenderDiagnostic.Kind;
import ke.co.scedar.plugins.freemarker.preview.render.RenderDiagnostic.SourceLine;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Renders a template with sample data on the calling (background) thread.
 * <p>
 * Runaway templates are contained: rendering is interrupted after {@link RenderRequest#timeout()}, output is capped at
 * {@link RenderRequest#maxOutputChars()}, and deep recursion is reported instead of crashing. Interrupting the calling
 * thread (e.g. because a newer edit superseded this render) stops rendering with a {@link Kind#CANCELLED} result.
 */
public final class TemplateRenderer {

    private static final Pattern MARKUP = Pattern.compile("<\\s*[A-Za-z!/][^>]*>");
    private static final int EXCERPT_RADIUS = 3;
    private final ScheduledExecutorService scheduler;

    public TemplateRenderer() {
        this(null);
    }

    /**
     * @param scheduler schedules the timeout watchdog; inside the IDE, the application's shared scheduler
     */
    public TemplateRenderer(@Nullable ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    private static RenderDiagnostic renderingError(TemplateException e, TemplateSource source, String mainName) {
        Kind kind = Kind.RENDERING;
        String title = "Rendering error";
        String hint = null;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof TemplateNotFoundException || t instanceof java.io.FileNotFoundException) {
                kind = Kind.MISSING_TEMPLATE;
                title = "Missing included template";
                hint = "Includes are resolved inside the template root only. Check the path, or use "
                        + "<#include \"...\" ignore_missing=true> for optional parts.";
            }
        }
        String message = e.getMessageWithoutStackTop();
        if (kind == Kind.RENDERING && message.contains("evaluated to null or missing")) {
            title = "Missing value";
            hint = "Add the value in the Sample Data panel, or give the expression a default such as ${value!\"\"}.";
        }
        String name = nameOr(e.getTemplateSourceName(), mainName);
        int line = e.getLineNumber() == null ? 0 : e.getLineNumber();
        int column = e.getColumnNumber() == null ? 0 : e.getColumnNumber();
        return new RenderDiagnostic(kind, title, message.strip(), name, line, column, e.getFTLInstructionStack(),
                excerpt(source, name, line), hint);
    }

    private static boolean isCancellation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (FtlAstAccess.isInterruption(t) || t instanceof InterruptedException) {
                return true;
            }
        }
        return Thread.currentThread().isInterrupted();
    }

    private static RenderResult timeout(RenderRequest request) {
        return failure(RenderDiagnostic.simple(Kind.TIMEOUT, "Rendering timed out",
                "The template did not finish within " + request.timeout().toMillis() + " ms and was stopped.",
                "Look for loops that never end, e.g. <#list 1.. as i> without <#break>."));
    }

    private static RenderResult cancelled() {
        return failure(RenderDiagnostic.simple(Kind.CANCELLED, "Cancelled", "Superseded by a newer render.", null));
    }

    private static RenderResult failure(RenderDiagnostic diagnostic) {
        return new RenderResult.Failure(diagnostic);
    }

    static RenderResult.OutputKind outputKind(OutputFormat format, String output) {
        if (format instanceof HTMLOutputFormat || format instanceof XHTMLOutputFormat) {
            return RenderResult.OutputKind.HTML;
        }
        if (format instanceof XMLOutputFormat) {
            return RenderResult.OutputKind.XML;
        }
        return MARKUP.matcher(output).find() ? RenderResult.OutputKind.HTML : RenderResult.OutputKind.TEXT;
    }

    private static String nameOr(@Nullable String name, String fallback) {
        return name == null || name.isEmpty() ? fallback : name;
    }

    static List<SourceLine> excerpt(TemplateSource source, String templateName, int line) {
        if (line <= 0) {
            return List.of();
        }
        String text;
        try {
            text = source.read(templateName);
        } catch (IOException e) {
            return List.of();
        }
        if (text == null) {
            return List.of();
        }
        String[] lines = text.split("\r\n|\r|\n", -1);
        List<SourceLine> excerpt = new ArrayList<>();
        for (int n = Math.max(1, line - EXCERPT_RADIUS); n <= Math.min(lines.length, line + EXCERPT_RADIUS); n++) {
            excerpt.add(new SourceLine(n, lines[n - 1], n == line));
        }
        return excerpt;
    }

    public RenderResult render(RenderRequest request) {
        long start = System.nanoTime();
        TemplateSource source = request.source().overlay(request.templateName(), request.templateText());
        PreviewConfiguration configuration = new PreviewConfiguration(source, true);
        Watchdog watchdog = new Watchdog(scheduler == null ? DefaultScheduler.INSTANCE : scheduler,
                Thread.currentThread(), request.timeout().toMillis());
        try {
            Template template = configuration.getTemplate(request.templateName());
            LimitedWriter out = new LimitedWriter(request.maxOutputChars());
            template.process(ModelConverter.toDataModel(request.sampleData()), out);
            if (watchdog.stop()) {
                return timeout(request);
            }
            String output = out.toString();
            return new RenderResult.Success(output, outputKind(template.getOutputFormat(), output),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        } catch (ParseException e) {
            watchdog.stop();
            return failure(new RenderDiagnostic(Kind.TEMPLATE_SYNTAX, "Template syntax error", e.getEditorMessage(),
                    nameOr(e.getTemplateName(), request.templateName()), e.getLineNumber(), e.getColumnNumber(),
                    null, excerpt(source, nameOr(e.getTemplateName(), request.templateName()), e.getLineNumber()),
                    "The editor keeps working; the preview updates as soon as the template parses again."));
        } catch (TemplateException e) {
            boolean timedOut = watchdog.stop();
            if (timedOut) {
                return timeout(request);
            }
            if (isCancellation(e)) {
                return cancelled();
            }
            return failure(renderingError(e, source, request.templateName()));
        } catch (LimitedWriter.LimitExceededException e) {
            watchdog.stop();
            return failure(RenderDiagnostic.simple(Kind.OUTPUT_TOO_LARGE, "Output too large",
                    "Rendering stopped after " + request.maxOutputChars() + " characters of output.",
                    "Check for unbounded loops such as <#list 1.. as i> or very large sample collections."));
        } catch (TemplateNotFoundException e) {
            watchdog.stop();
            return failure(RenderDiagnostic.simple(Kind.MISSING_TEMPLATE, "Template not found",
                    String.valueOf(e.getMessage()), null));
        } catch (IOException e) {
            watchdog.stop();
            return failure(RenderDiagnostic.simple(Kind.INTERNAL, "Could not read a template",
                    String.valueOf(e.getMessage()), null));
        } catch (StackOverflowError e) {
            watchdog.stop();
            return failure(RenderDiagnostic.simple(Kind.RENDERING, "Recursion too deep",
                    "The template recursed too deeply (for example a macro or include that calls itself without end).",
                    "Add a stop condition to the recursive macro or include."));
        } catch (RuntimeException e) {
            boolean timedOut = watchdog.stop();
            if (timedOut) {
                return timeout(request);
            }
            if (isCancellation(e)) {
                return cancelled();
            }
            return failure(RenderDiagnostic.simple(Kind.INTERNAL, "Unexpected rendering failure",
                    e.getClass().getSimpleName() + ": " + e.getMessage(), null));
        } finally {
            watchdog.stop();
            if (watchdog.fired()) {
                Thread.interrupted(); // never leak our own interrupt to a pooled thread
            }
        }
    }

    /**
     * Used when no scheduler is supplied (outside the IDE, e.g. in plain unit tests).
     */
    private static final class DefaultScheduler {
        static final ScheduledExecutorService INSTANCE = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FreeMarker Preview watchdog");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Interrupts the rendering thread once the timeout elapses, unless stopped first.
     */
    private static final class Watchdog {
        private final Thread thread;
        private final ScheduledFuture<?> future;
        private boolean stopped;
        private boolean fired;

        Watchdog(ScheduledExecutorService scheduler, Thread thread, long timeoutMillis) {
            this.thread = thread;
            this.future = scheduler.schedule(this::fire, timeoutMillis, TimeUnit.MILLISECONDS);
        }

        private synchronized void fire() {
            if (!stopped) {
                fired = true;
                thread.interrupt();
            }
        }

        /**
         * @return whether the timeout fired before rendering finished
         */
        synchronized boolean stop() {
            stopped = true;
            future.cancel(false);
            return fired;
        }

        synchronized boolean fired() {
            return fired;
        }
    }

    /**
     * Collects output and fails once it grows beyond the limit.
     */
    static final class LimitedWriter extends Writer {
        private final StringBuilder buffer = new StringBuilder();
        private final int limit;
        LimitedWriter(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(char[] chars, int offset, int length) throws IOException {
            if (buffer.length() + length > limit) {
                throw new LimitExceededException();
            }
            buffer.append(chars, offset, length);
        }

        @Override
        public void write(String s, int offset, int length) throws IOException {
            if (buffer.length() + length > limit) {
                throw new LimitExceededException();
            }
            buffer.append(s, offset, offset + length);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return buffer.toString();
        }

        static final class LimitExceededException extends IOException {
            LimitExceededException() {
                super("Output limit exceeded");
            }
        }
    }
}
