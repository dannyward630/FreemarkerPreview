package ke.co.scedar.plugins.freemarker.preview.engine;

import freemarker.cache.TemplateNameFormat;
import freemarker.core.FtlAstAccess;
import freemarker.core.TemplateClassResolver;
import freemarker.template.*;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/**
 * A FreeMarker configuration hardened for previewing untrusted templates:
 * <ul>
 *     <li>templates load only through a {@link TemplateSource} (no file system or class path loader);</li>
 *     <li>{@code ?new} and {@code ?api} are disabled, and {@link SimpleObjectWrapper} refuses to expose Java objects,
 *     so templates cannot instantiate or call arbitrary Java classes;</li>
 *     <li>optionally, every loaded template (including includes) gets thread-interruption checks so a runaway
 *     template can be stopped.</li>
 * </ul>
 * A new instance is created for each preview run, so there is no stale template cache.
 */
public final class PreviewConfiguration extends Configuration {

    public static final Version VERSION = Configuration.VERSION_2_3_34;

    private final boolean interruptible;
    private final SafeTemplateLoader loader;
    private final Set<Template> instrumented = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    public PreviewConfiguration(TemplateSource source, boolean interruptible) {
        super(VERSION);
        this.interruptible = interruptible;
        loader = new SafeTemplateLoader(source);
        setTemplateLoader(loader);
        setLocalizedLookup(false);
        setTemplateNameFormat(TemplateNameFormat.DEFAULT_2_4_0);
        setRecognizeStandardFileExtensions(true);
        setDefaultEncoding("UTF-8");
        setLocale(Locale.getDefault());

        setObjectWrapper(new SimpleObjectWrapper(VERSION));
        setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        setAPIBuiltinEnabled(false);

        setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        setAttemptExceptionReporter((te, env) -> {
        });
        setLogTemplateExceptions(false);
        setWrapUncheckedExceptions(true);
        setFallbackOnNullLoopVariable(false);
    }

    /**
     * Templates that were not found at their exact path and were loaded from an ancestor directory instead.
     */
    public java.util.Map<String, String> templateFallbacks() {
        return loader.fallbacks();
    }

    @Override
    public Template getTemplate(String name, Locale locale, Object customLookupCondition, String encoding,
                                boolean parseAsFTL, boolean ignoreMissing) throws IOException {
        Template template = super.getTemplate(name, locale, customLookupCondition, encoding, parseAsFTL, ignoreMissing);
        if (interruptible && template != null && instrumented.add(template)) {
            FtlAstAccess.addInterruptionChecks(template);
        }
        return template;
    }
}
