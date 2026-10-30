# FreeMarker Live Preview

An IntelliJ IDEA plugin that opens Apache FreeMarker templates (`.ftl`, `.ftlh`, `.ftlx`) in a split editor:
template source on the left, rendered output on the right, refreshed as you type. Sample data is generated
automatically from the template and can be edited as JSON.

- **Target:** IntelliJ IDEA 2026.2 (build 262+), Java 25 bytecode (required by the 2026.2 platform)
- **Built with:** IntelliJ Platform Gradle Plugin 2.19.0, Gradle 9.1.0, FreeMarker 2.3.35, JCEF

## Setup

Requirements: JDK 21+ to run Gradle, and a JDK 25 toolchain for compiling.

`gradle.properties` points both at the locally installed IDE, so nothing large is downloaded:

```properties
platformLocalPath=/snap/intellij-idea/current             # compile/run against this IDE
org.gradle.java.installations.paths=/snap/intellij-idea/current/jbr   # JDK 25 (the IDE's bundled JBR)
```

On another machine, change or delete these lines. Without `platformLocalPath` the build downloads
`platformVersion` (~1.6 GB), and without a JDK 25 the foojay resolver provisions one.

| Task | Command |
|---|---|
| Run all tests | `./gradlew test` |
| Build the installable zip | `./gradlew buildPlugin` → `build/distributions/FreemarkerPreview-0.1.0.zip` |
| Launch a sandbox IDE with the plugin | `./gradlew runIde` |
| Run the IntelliJ Plugin Verifier | `./gradlew verifyPlugin` |

**Install into your IDE:** *Settings → Plugins → ⚙ → Install Plugin from Disk…* → choose the zip.

## Using it

Open any `.ftl`/`.ftlh`/`.ftlx` file. It opens as source + preview; the layout buttons in the editor's top-right
corner switch between editor-only, split and preview-only. You can also right-click a template (editor or Project
view) → **Open FreeMarker Preview**.

Toolbar actions on the split editor:

| Action | What it does |
|---|---|
| ⟳ Refresh Preview | Renders again immediately |
| ⚡ Regenerate Sample Data | Discards your sample-data edits and generates data from the template again |
| ▦ Show Sample Data | Shows/hides the JSON editor below the preview |
| 🌐 Load Remote Images and Styles | Allows/blocks images, stylesheets and fonts from `http(s)` URLs (on by default) |

### Preview behavior

- Re-renders 300 ms after you stop typing (400 ms after sample-data edits). A newer edit cancels a render in progress.
- Also re-renders when a template it includes is edited.
- Analysis, generation and rendering run on a background pool and never block the UI. Rendering runs outside any
  read lock; results are applied only if they are still the latest and the editor is still open.
- **Errors never show stale output.** A syntax error, missing value, missing include or invalid sample-data JSON
  replaces the preview with a diagnostic: the message, template and line, a source excerpt with the line highlighted,
  a hint, and FreeMarker's instruction stack. The editor stays fully usable, and the preview recovers as soon as the
  template is valid again.
- Output kind: `.ftlh` → HTML with auto-escaping; `.ftlx` → XML, shown as text; `.ftl` → shown as HTML if it
  contains markup, otherwise as plain text (e.g. text emails).
- An empty template shows an empty-state page.
- If JCEF is unavailable (some remote-dev setups), the generated HTML is shown as text instead.

### Includes and imports

Template names are resolved against a **template root**: the nearest enclosing `templates/` directory inside the
source/resource root (e.g. `src/main/resources/templates`, the Spring convention), otherwise the source root, the
content root, or the template's own folder. Relative (`parts/footer.ftl`) and root-absolute (`/layout.ftl`) paths
work. Anything outside the root is refused: `../` escaping the root, `classpath:`, absolute file paths, symlinks
pointing outside, and the `*` acquisition syntax. Unsaved editor content of included files is used.

**Lenient fragment lookup.** If an included or imported template doesn't exist at its exact path, the preview also
looks in the parent folders for the same trailing path. For example, `<#import "fragments/layout.ftl">` from
`reports/rollover.ftl` normally means `reports/fragments/layout.ftl`; if that's missing, `fragments/layout.ftl` is
used. This mimics loaders that are configured with a different base path, which the preview can't know.

> ⚠ When this fallback is used, the status line says *"template path resolved leniently"* and a note under the
> sample data names the path. **Check that path in your real application**: plain FreeMarker doesn't do this, so
> a preview that works can still hide a "template not found" in production.

## Sample data

### How the model is generated

The template is parsed with **FreeMarker's own parser** and the parse tree is walked symbolically. Nothing is
executed. So comments, `r"raw strings"`, `<#noparse>` and ordinary text are never mistaken for variables, and
scoping is respected:

```ftl
<h1>Hello ${customer.name}!</h1>
<#list orders as order>
    <p>${order.productName}: ${order.total}</p>
</#list>
```

generates

```json
{
  "customer": { "name": "Jane Doe" },
  "orders": [
    { "productName": "Laptop",  "total": 85000 },
    { "productName": "Monitor", "total": 25000 }
  ]
}
```

What the analyzer understands:

| Construct | Effect |
|---|---|
| `${a.b.c}`, `a["key"]` | nested objects |
| `#list xs as x`, `#list xs` + `#items as x`, `#list m as k, v`, `xs[0]`, `?size`, `?first`, `?join`, `?sort_by("p")` | non-empty lists (2 items) or key/value maps; `x` stays local |
| `?filter(p -> p.age > 18)`, `?map(...)` | lambda parameter bound to the list item |
| `#if`, `&&`, `!`, `?then`, `?string("y","n")` | booleans |
| comparisons/arithmetic with literals, `?round`, `?c`, `#{...}` | numbers |
| `?date`, `?time`, `?datetime`, `?iso…`, `?string("dd.MM.yyyy")` | dates (ISO strings) |
| `?upper_case`, `?trim`, `== "x"`… | strings |
| `x!"default"`, `(a.b)!`, `x??`, `?has_content`, `?default(...)` | marked optional; the default's type is a *weak* hint: the name wins (`reportDate!"N/A"` is a date, `institutionCount!"N/A"` a number) |
| `#assign`/`#global`/`#local`, macro/function parameters, `#import … as ns`, `#escape x as …` | local, not part of the model (`#assign c = customer` aliases `customer`) |
| `obj.method(...)`, `fn(...)` | method stub `"method()": value` |
| `<@x/>` where `x` is not a macro | directive stub `"@x": ""` |
| `#include "static/path.ftl"` | included templates are analyzed too (scope is shared) |
| `#import "fragments/x.ftl" as x` | imported libraries are analyzed in their own namespace; variables their macros read (e.g. `${model.clientConfig.name}` in a header fragment) become part of the model |
| `amount.divide(total, 4, "roundHalfUp")`, `x.compareTo(0)`, `.subtract`, `.doubleValue()` | `amount`/`total` are numbers (BigDecimal methods) |
| `x?string["#,##0.00"]` | legacy call syntax, same as `x?string("#,##0.00")`: a number |
| `byCode[code]` with a computed key | a map; keys share the key's name, so `#list m as code, v` + `other[code]` get matching keys |
| comparisons with fractions (`rate gte 0.9`) | ratios: 0.92, 0.84, 0.67, … |

Values are picked by name and context and are deterministic: `email` → `jane.doe@example.com`, `company.name` →
`Acme Corp`, `product.name` → `Laptop`, `createdAt` → `2026-01-15T09:30:00`, `total` → `85000`, `isVip` → `true`,
image-like names → an inline SVG placeholder. Emails use the reserved `example.com` domain; secrets become `********`.

*Why not a Faker library?* I evaluated Datafaker 2.7.0. Seeded per field it is deterministic, but its values are
incoherent for previews (`Garnet VonRueden` / `clemencia.mcdermott@hotmail.com`, products like
`Sleek Copper Bench`), it adds 3 MB, and its phone provider needs the optional `libphonenumber`. The curated
generator lives in a single class (`SampleValues`) if you want to swap it.

### Editing the sample data

The JSON editor under the preview shows the effective data and its mode:

- **Generated sample data** follows the template as you type.
- **Custom sample data**: as soon as you edit the JSON, your edits are kept and never overwritten while typing.
  Your JSON is applied *on top of* the generated model: objects merge recursively, other values replace the
  generated ones, and variables you haven't set are still generated. So adding `${customer.city}` to the template
  just works.
- Set a value to `null` to make it **missing**, e.g. to preview `${x!"fallback"}` or `<#if x??>` branches.
- **Regenerate** (link in the panel header or toolbar) discards your edits.
- Custom data is saved per template in `.idea/workspace.xml` (personal, not shared) and restored on reopen.

JSON → FreeMarker conventions:

| JSON | In the template |
|---|---|
| `"2026-01-15"`, `"2026-01-15T09:30:00"`, `"09:30:00"` | both a date and a string (`?string("yyyy")`, `?date`, `?is_string` all work) |
| `"fmt": { "money()": "$1.00" }` | `${fmt.money(order.total)}` returns `$1.00` for any arguments |
| `"@layout": "<header/>"` | `<@layout>…</@layout>` prints the value, then the nested content |
| `null` | missing value |
| numbers | numbers that also answer common `BigDecimal` methods (`divide`, `multiply`, `add`, `subtract`, `compareTo`, `setScale`, `doubleValue`, …), like a BeansWrapper-configured app. These are a fixed list implemented by the plugin; no Java method is invoked |

### Project-aware inference (Java)

When the Java plugin is installed (an optional dependency) and indexing has finished:

1. `<#-- @ftlvariable name="invoice" type="com.acme.Invoice" -->` declarations are resolved (generics such as
   `java.util.List<com.acme.Order>` work).
2. Otherwise, a variable whose properties the template reads is matched to a project class by name
   (`customer` → `Customer`, list `orders` → `Order`), but **only if exactly one class has that name**.

Property types come from getters (`getX`/`isX`), record components and called methods. Enums give their real
constants, dates/numbers/booleans/collections/maps map accordingly. Only properties the template references are
generated. If a type can't be resolved, generated mock data is used. The notes under the sample-data header show
what was inferred, e.g. `invoice: com.acme.Invoice (@ftlvariable)`. No project code is compiled or executed, and
Spring is not required.

## Security model

The template is treated as untrusted:

- `?new` and `?api` are disabled and `SimpleObjectWrapper` exposes no Java objects, so templates cannot instantiate
  classes or call Java methods. Stubs return sample values only.
- Templates are loaded only through a sandboxed loader limited to the template root: no class-path or file-system
  loader, 1 MB per template, at most 200 loads per render.
- The preview page has a Content-Security-Policy: **template scripts don't run**, and frames, forms and plugins are
  blocked. A second JCEF request filter cancels everything else.
- **Images, stylesheets and fonts from `http(s)`** (logos, CDN icon fonts) load by default. Switch them off with the
  🌐 toolbar toggle if a template must not contact remote hosts; the preview then shows only inline/`data:`
  resources. Fetching them reveals your IP address to those hosts, like any browser would.
- **Project images and stylesheets** referenced by local paths (`/images/logo.png`, `css/app.css`) are read by the
  plugin and inlined as `data:` URIs or `<style>`. They're looked up next to the template, in the template root, and
  in the module's static folders (`static/`, `public/`, `META-INF/resources/`, `webapp/`, …). Only image/CSS files
  inside the project's content are read (2 MB / 500 KB limits); the page itself can't reach any file.
- Clicking an `http(s)` link opens it in your system browser; other navigation is blocked.
- Runaway templates are stopped: 5 s timeout (thread interruption checks are injected into every loop), 5 M
  character output cap, stack-overflow protection.
- No network listener or external service; the JCEF browser is disposed with the editor.

## Supported syntax and limitations

Supported: everything FreeMarker 2.3.35 parses with default settings, including angle- and square-bracket *tag*
syntax (auto-detected) and `${…}` interpolation.

Known limitations:

- **Not interactively click-tested by me.** Automated tests run headless, so they exercise the real split editor,
  controller, refresh, sample-data editing and persistence through the non-JCEF fallback view. `runIde` was verified
  to load the plugin without errors, but I did not manually click through the JCEF preview. The scroll-position
  keeper (via `window.name`) is untested inside JCEF.
- Square-bracket *interpolation* `[=x]` is not enabled.
- Your application's FreeMarker `Configuration` is not read: shared variables, auto-imports/includes, custom number or
  date formats, locale, custom object wrappers and Spring settings are ignored. The preview uses defaults and the
  IDE's locale.
- Includes and imports with computed names (`<#include path>`) are not analyzed for variables. A note is shown, and
  you can add values manually.
- Only common `BigDecimal` methods are emulated on numbers; other Java methods on values aren't (use the `"name()"`
  stub convention for your own methods).
- CSS `url(...)` references inside inlined stylesheets are not rewritten.
- Arguments passed to macros don't propagate types to the macro's parameters.
- Template JavaScript doesn't run in the preview (by design, see Security).
- Java inference only covers the Java plugin's PSI (Kotlin and Lombok classes visible through PSI may work but are
  untested), and is skipped while indexing.
- Debounce, list size (2), timeout and output cap are constants; there is no settings page yet.
- Custom sample data is keyed by file URL, so renaming or moving a template drops it.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `.ftl` opens without a preview | Check the plugin is enabled. If another plugin opens the file with a different editor, choose the **FreeMarker** tab or use *Open FreeMarker Preview*. |
| "JCEF is not available… showing the generated HTML instead" | Your IDE runtime lacks JCEF (some remote/thin-client setups). Use a JetBrains Runtime with JCEF. |
| "Missing included template" | The include path resolves against the template root (see above); check the folder layout or use `ignore_missing=true`. |
| Preview says "Still preparing the preview… Current step: …" | The step named is still running after 8 s. *Generating sample data (Java type lookup)* waits for indexing to finish. Use Refresh Preview; if it persists, check the IDE log (Help → Show Log), or enable debug logging for `#ke.co.scedar.plugins.freemarker.preview` (Help → Diagnostic Tools → Debug Log Settings) to see each step. |
| Status says "template path resolved leniently" | A fragment was found in a parent folder, not at its exact path. The preview works, but verify the path in your application. |
| Images don't show | Remote: check the 🌐 toggle. Local: the file must be inside the project, e.g. under `static/`, `public/` or next to the template. |
| "Missing value" | Add the value in the Sample Data panel, or give the expression a default (`${x!""}`). |
| Java types not picked up | Wait for indexing to finish; use `@ftlvariable` with a fully-qualified type; name-matching needs a unique class name. |
| Sample data edits seem ignored | Check the header says *Custom sample data* and the JSON is valid (invalid JSON shows a diagnostic). |
| Build fails: can't find JDK 25 | Set `org.gradle.java.installations.paths` to a JDK 25, or remove it to let Gradle provision one. |

## Architecture

```
freemarker.core.FtlAstAccess      read-only bridge to FreeMarker's package-private AST accessors
engine/    TemplateSource, TemplatePaths, SafeTemplateLoader, PreviewConfiguration (hardened Configuration)
analysis/  TemplateAnalyzer → AnalysisResult of VariableShapes (no IntelliJ dependencies)
model/     SampleModelGenerator, SampleValues, TypeInfo/TypeResolver, Json, ModelMerger
render/    TemplateRenderer (timeout/output cap), ModelConverter (JSON → TemplateModels), PreviewHtml (CSP, pages)
ide/       editor providers, PreviewController (debounce/background/cancellation), JCEF view, sample-data panel,
           actions, SampleDataService (persistence), TypeResolverProvider extension point
ide/java/  JavaTypeResolver (PSI; loaded via optional dependency on com.intellij.java)
```

The core (`engine`, `analysis`, `model`, `render`) has no IntelliJ dependencies and is unit-tested directly.

**Testing notes:** 67 tests (58 unit + 9 platform integration), including a regression test built from a real
report with layout/header/footer fragments (`src/test/resources/fixtures/rollover`). The test sandbox disables
`com.intellij.modules.ultimate`: the unified IDEA distribution's licensing module fails to start under the headless
test framework, independent of this plugin (see `prepareTestSandbox` in `build.gradle.kts`).
