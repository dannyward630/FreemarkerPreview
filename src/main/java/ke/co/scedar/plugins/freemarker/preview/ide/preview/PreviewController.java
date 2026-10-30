package ke.co.scedar.plugins.freemarker.preview.ide.preview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import ke.co.scedar.plugins.freemarker.preview.analysis.AnalysisResult;
import ke.co.scedar.plugins.freemarker.preview.analysis.TemplateAnalyzer;
import ke.co.scedar.plugins.freemarker.preview.ide.SampleDataService;
import ke.co.scedar.plugins.freemarker.preview.ide.ProjectResourceResolver;
import ke.co.scedar.plugins.freemarker.preview.ide.TemplateRoot;
import ke.co.scedar.plugins.freemarker.preview.ide.TypeResolverProvider;
import ke.co.scedar.plugins.freemarker.preview.ide.VfsTemplateSource;
import ke.co.scedar.plugins.freemarker.preview.model.Json;
import ke.co.scedar.plugins.freemarker.preview.model.ModelMerger;
import ke.co.scedar.plugins.freemarker.preview.model.SampleModelGenerator;
import ke.co.scedar.plugins.freemarker.preview.model.SampleModelGenerator.GeneratedModel;
import ke.co.scedar.plugins.freemarker.preview.model.TypeResolver;
import ke.co.scedar.plugins.freemarker.preview.render.RenderDiagnostic;
import ke.co.scedar.plugins.freemarker.preview.render.RenderRequest;
import ke.co.scedar.plugins.freemarker.preview.render.RenderResult;
import ke.co.scedar.plugins.freemarker.preview.render.ResourceInliner;
import ke.co.scedar.plugins.freemarker.preview.render.TemplateRenderer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Keeps one preview up to date.
 * <p>
 * Edits are debounced, then analysis, sample-data generation and rendering run on a bounded background pool, never on
 * the EDT and never while holding a read lock (only short read actions to snapshot text). Each run gets a generation
 * number: a newer run cancels (interrupts) the older one, and results are applied on the EDT only if they are still
 * the latest and the editor is not disposed.
 */
public final class PreviewController implements Disposable {

    private static final Logger LOG = Logger.getInstance(PreviewController.class);
    private static final ExecutorService EXECUTOR =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("FreeMarker Preview", 2);
    static final int TEMPLATE_DEBOUNCE_MS = 300;
    static final int SAMPLE_DATA_DEBOUNCE_MS = 400;
    /** Text of the analyzer note emitted when a template was loaded through the ancestor-folder fallback. */
    private static final String PATH_FALLBACK_MARKER = "does not exist; the preview loaded";

    private final Project project;
    private final VirtualFile file;
    private final Document document;
    private final PreviewPanel view;
    private final SampleDataPanel sampleDataPanel;

    private final TemplateAnalyzer analyzer = new TemplateAnalyzer();
    private final SampleModelGenerator generator = new SampleModelGenerator();
    private final TemplateRenderer renderer = new TemplateRenderer(AppExecutorUtil.getAppScheduledExecutorService());

    private final Object lock = new Object();
    private final AtomicLong generation = new AtomicLong();
    private @Nullable ScheduledFuture<?> pending;
    private @Nullable Future<?> running;
    private volatile boolean disposed;

    /** Sample data state; written on the EDT, read by background runs. */
    private volatile boolean customData;
    private volatile @Nullable String customText;
    private volatile @Nullable GeneratedModel lastGenerated;
    private volatile Set<String> dependencyUrls = Set.of();
    /** What the latest run is doing, shown if it takes unusually long. */
    private volatile String stage = "waiting to start";
    private volatile long shownRun;
    static final int SLOW_RUN_NOTICE_SECONDS = 8;

    private final List<Consumer<RenderResult>> resultListeners = new CopyOnWriteArrayList<>();

    PreviewController(Project project, VirtualFile file, Document document, PreviewPanel view, SampleDataPanel sampleData) {
        this.project = project;
        this.file = file;
        this.document = document;
        this.view = view;
        this.sampleDataPanel = sampleData;

        String persisted = SampleDataService.getInstance(project).get(file);
        if (persisted != null) {
            customData = true;
            customText = persisted;
            sampleData.setText(persisted);
            sampleData.setCustom(true);
        }

        document.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(DocumentEvent event) {
                schedule(TEMPLATE_DEBOUNCE_MS);
            }
        }, this);
        // Refresh when an included template is edited.
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(DocumentEvent event) {
                if (event.getDocument() == document || dependencyUrls.isEmpty()) {
                    return;
                }
                VirtualFile changed = FileDocumentManager.getInstance().getFile(event.getDocument());
                if (changed != null && dependencyUrls.contains(changed.getUrl())) {
                    schedule(TEMPLATE_DEBOUNCE_MS);
                }
            }
        }, this);
    }

    // ------------------------------------------------------------------------------------------- user operations

    /** Called on the EDT when the user edits the sample data. */
    void onSampleDataEdited(String text) {
        customData = true;
        customText = text;
        SampleDataService.getInstance(project).put(file, text);
        sampleDataPanel.setCustom(true);
        schedule(SAMPLE_DATA_DEBOUNCE_MS);
    }

    /** Discards custom sample data and regenerates it from the template. */
    public void regenerateSampleData() {
        customData = false;
        customText = null;
        SampleDataService.getInstance(project).remove(file);
        sampleDataPanel.setCustom(false);
        GeneratedModel generated = lastGenerated;
        if (generated != null) {
            sampleDataPanel.setText(Json.write(generated.model()));
        }
        schedule(0);
    }

    public void refresh() {
        schedule(0);
    }

    public boolean isCustomData() {
        return customData;
    }

    public boolean isSampleDataVisible() {
        return view.isSampleDataVisible();
    }

    public void setSampleDataVisible(boolean visible) {
        view.setSampleDataVisible(visible);
    }

    // ------------------------------------------------------------------------------------------------ scheduling

    void schedule(int delayMillis) {
        synchronized (lock) {
            if (disposed) {
                return;
            }
            if (pending != null) {
                pending.cancel(false);
            }
            pending = AppExecutorUtil.getAppScheduledExecutorService()
                    .schedule(this::startRun, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void startRun() {
        synchronized (lock) {
            if (disposed) {
                return;
            }
            long run = generation.incrementAndGet();
            if (running != null) {
                running.cancel(true);
            }
            running = EXECUTOR.submit(() -> runPipeline(run));
            AppExecutorUtil.getAppScheduledExecutorService()
                    .schedule(() -> noticeSlowRun(run), SLOW_RUN_NOTICE_SECONDS, TimeUnit.SECONDS);
        }
    }

    /** Replaces the "Preparing" page with what the run is waiting for, so a slow preview is never a silent one. */
    private void noticeSlowRun(long run) {
        if (isStale(run) || shownRun >= run) {
            return;
        }
        String current = stage;
        LOG.info("FreeMarker preview of " + file.getPath() + " is slow; current step: " + current);
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!isStale(run) && shownRun < run) {
                view.showMessage("Still preparing the preview…", "Current step: " + current + ".", List.of(
                        "Sample data from Java types waits for indexing to finish and for a moment without edits.",
                        "Use Refresh Preview to try again. If it stays here, the IDE log (Help | Show Log) has details."));
            }
        }, ModalityState.any(), o -> disposed);
    }

    private boolean isStale(long run) {
        return disposed || run != generation.get();
    }

    // -------------------------------------------------------------------------------------------------- pipeline

    private record Snapshot(String text, @Nullable TemplateRoot root) {
    }

    private void runPipeline(long run) {
        long started = System.nanoTime();
        try {
            if (isStale(run)) {
                LOG.debug("run ", run, ": stale before start");
                return;
            }
            LOG.debug("run ", run, ": started for ", file.getPath());
            stage = "reading the template";
            Snapshot snapshot = ReadAction.computeBlocking(() -> file.isValid()
                    ? new Snapshot(document.getText(), TemplateRoot.locate(project, file))
                    : new Snapshot("", null));
            if (snapshot.root() == null) {
                apply(run, null, null, () -> view.showMessage("Template not available",
                        "The file was deleted or is outside any folder.", List.of()));
                return;
            }
            String name = snapshot.root().templateName();
            VfsTemplateSource source = new VfsTemplateSource(snapshot.root().directory());

            LOG.debug("run ", run, ": template \"", name, "\" in root ", snapshot.root().directory().getPath());
            stage = "analyzing the template and its includes/imports";
            AnalysisResult analysis = analyzer.analyze(name, snapshot.text(), source);
            LOG.debug("run ", run, ": analyzed ", analysis.templates(), ", roots ", analysis.roots().keySet(),
                    ", parse error ", analysis.parseError());
            stage = "generating sample data (Java type lookup)";
            GeneratedModel generated = analysis.isSuccessful() ? generate(analysis) : lastGenerated;
            LOG.debug("run ", run, ": sample data generated");
            if (generated == null) {
                generated = new GeneratedModel(Map.of(), List.of());
            }
            if (analysis.isSuccessful()) {
                lastGenerated = generated;
            }
            if (isStale(run)) {
                return;
            }

            boolean custom = customData;
            String userText = customText;
            RenderResult result;
            if (snapshot.text().isBlank()) {
                result = null;
            } else {
                Map<String, Object> data = generated.model();
                RenderResult dataError = null;
                if (custom && userText != null) {
                    try {
                        data = ModelMerger.merge(generated.model(), Json.parseObject(userText));
                    } catch (Json.ParseException e) {
                        dataError = new RenderResult.Failure(new RenderDiagnostic(RenderDiagnostic.Kind.SAMPLE_DATA,
                                "Invalid sample data JSON", e.getMessage(), "Sample data", e.line(), e.column(), null,
                                List.of(), "Fix the JSON in the Sample Data panel, or click Regenerate to start over."));
                    }
                }
                stage = "rendering";
                result = dataError != null ? dataError
                        : renderer.render(new RenderRequest(name, snapshot.text(), source, data));
                LOG.debug("run ", run, ": rendered ", result instanceof RenderResult.Failure f
                        ? "with " + f.diagnostic().kind() + ": " + f.diagnostic().message() : "successfully");
                if (result instanceof RenderResult.Success success && success.kind() == RenderResult.OutputKind.HTML) {
                    stage = "embedding project images and stylesheets";
                    result = inlineProjectResources(success, snapshot.root());
                }
            }
            if (result instanceof RenderResult.Failure failure
                    && failure.diagnostic().kind() == RenderDiagnostic.Kind.CANCELLED) {
                return;
            }
            dependencyUrls = source.readUrls();

            String generatedJson = Json.write(generated.model());
            List<String> notes = new ArrayList<>(generated.notes());
            RenderResult finalResult = result;
            apply(run, finalResult, () -> {
                if (!customData) {
                    sampleDataPanel.setText(generatedJson);
                }
                sampleDataPanel.setNotes(notes);
            }, () -> {
                if (finalResult == null) {
                    view.showMessage("Nothing to preview yet",
                            "Start typing a FreeMarker template; the preview refreshes automatically.",
                            List.of("Variables you use, such as ${customer.name} or <#list orders as order>, get sample values.",
                                    "Edit the sample data below to try other values."));
                } else {
                    long fallbacks = notes.stream().filter(n -> n.contains(PATH_FALLBACK_MARKER)).count();
                    view.showResult(finalResult, custom, fallbacks == 0 ? null : fallbacks + " template path"
                            + (fallbacks == 1 ? "" : "s") + " resolved leniently; check them in your application (see sample data notes)");
                }
            });
            LOG.debug("run ", run, ": pipeline done in ", (System.nanoTime() - started) / 1_000_000, " ms");
        } catch (ProcessCanceledException e) {
            LOG.debug("run ", run, ": cancelled");
            // superseded or the project is closing
        } catch (Throwable t) {
            if (isStale(run) || Thread.currentThread().isInterrupted()) {
                return;
            }
            LOG.warn("FreeMarker preview failed", t);
            RenderResult failure = new RenderResult.Failure(RenderDiagnostic.simple(RenderDiagnostic.Kind.INTERNAL,
                    "Preview failed", t.getClass().getSimpleName() + ": " + t.getMessage(), null));
            apply(run, failure, null, () -> view.showResult(failure, customData));
        }
    }

    /** Embeds images and stylesheets the page references from the project (the preview itself cannot read files). */
    private RenderResult.Success inlineProjectResources(RenderResult.Success success, TemplateRoot root) {
        String html = ReadAction.computeBlocking(() -> project.isDisposed() || !file.isValid() ? success.output()
                : ResourceInliner.inline(success.output(), new ProjectResourceResolver(project, file, root.directory())));
        return html.equals(success.output()) ? success : new RenderResult.Success(html, success.kind(), success.millis());
    }

    /** Generates sample data, using project types when a resolver is available and indexes are ready. */
    private GeneratedModel generate(AnalysisResult analysis) {
        TypeResolver resolver = TypeResolverProvider.forFile(project, file);
        if (resolver == TypeResolver.NONE || DumbService.getInstance(project).isDumb()) {
            return generator.generate(analysis, TypeResolver.NONE);
        }
        try {
            return ReadAction.nonBlocking(() -> generator.generate(analysis, resolver))
                    .expireWith(this)
                    .executeSynchronously();
        } catch (IndexNotReadyException e) {
            return generator.generate(analysis, TypeResolver.NONE);
        }
    }

    private void apply(long run, @Nullable RenderResult result, @Nullable Runnable sampleDataUpdate, Runnable viewUpdate) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (isStale(run)) {
                LOG.debug("run ", run, ": result discarded (superseded by run ", generation.get(), ")");
                return;
            }
            LOG.debug("run ", run, ": showing result");
            shownRun = run;
            if (sampleDataUpdate != null) {
                sampleDataUpdate.run();
            }
            viewUpdate.run();
            if (result != null) {
                resultListeners.forEach(listener -> listener.accept(result));
            }
        }, ModalityState.stateForComponent(view), o -> disposed);
    }

    @TestOnly
    public void addResultListener(Consumer<RenderResult> listener) {
        resultListeners.add(listener);
    }

    @TestOnly
    public String sampleDataText() {
        return sampleDataPanel.getText();
    }

    @TestOnly
    public void editSampleData(String text) {
        sampleDataPanel.setTextAsUser(text);
    }

    @Override
    public void dispose() {
        synchronized (lock) {
            disposed = true;
            if (pending != null) {
                pending.cancel(false);
            }
            if (running != null) {
                running.cancel(true);
            }
        }
    }
}
