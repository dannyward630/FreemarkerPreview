package ke.co.scedar.plugins.freemarker.preview.ide;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.TimeoutUtil;
import ke.co.scedar.plugins.freemarker.preview.ide.editor.FreeMarkerSplitEditor;
import ke.co.scedar.plugins.freemarker.preview.ide.editor.FreeMarkerSplitEditorProvider;
import ke.co.scedar.plugins.freemarker.preview.ide.preview.FreeMarkerPreviewFileEditor;
import ke.co.scedar.plugins.freemarker.preview.ide.preview.PreviewController;
import ke.co.scedar.plugins.freemarker.preview.render.RenderDiagnostic;
import ke.co.scedar.plugins.freemarker.preview.render.RenderResult;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

/**
 * Exercises the real split editor in a light project. The test environment is headless, so JCEF is unavailable and the
 * preview uses its source fallback, which lets the test read the generated page.
 */
public class FreeMarkerPreviewEditorTest extends LightJavaCodeInsightFixtureTestCase {

    private static final long TIMEOUT_MS = 30_000;
    private final BlockingQueue<RenderResult> results = new LinkedBlockingQueue<>();
    private FreeMarkerSplitEditor editor;

    private static Predicate<RenderResult> successContaining(String text) {
        return r -> r instanceof RenderResult.Success s && s.output().contains(text);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (editor != null) {
                Disposer.dispose(editor);
            }
        } catch (Throwable t) {
            addSuppressedException(t);
        } finally {
            editor = null;
            super.tearDown();
        }
    }

    private VirtualFile template(String path, String text) {
        return myFixture.addFileToProject(path, text).getVirtualFile();
    }

    private PreviewController open(VirtualFile file) {
        FileEditor created = new FreeMarkerSplitEditorProvider().createEditor(getProject(), file);
        assertInstanceOf(created, FreeMarkerSplitEditor.class);
        editor = (FreeMarkerSplitEditor) created;
        PreviewController controller = editor.preview().controller();
        assertNotNull(controller);
        controller.addResultListener(results::add);
        return controller;
    }

    private FreeMarkerPreviewFileEditor preview() {
        return editor.preview();
    }

    private RenderResult awaitResult(Predicate<RenderResult> condition) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        RenderResult last = null;
        while (System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            RenderResult result;
            while ((result = results.poll()) != null) {
                last = result;
                if (condition.test(result)) {
                    return result;
                }
            }
            TimeoutUtil.sleep(20);
        }
        fail("No matching render result; last was " + last);
        return null;
    }

    private void setText(VirtualFile file, String text) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText(text));
    }

    public void testProviderAcceptsFreeMarkerTemplatesOnly() {
        FreeMarkerSplitEditorProvider provider = new FreeMarkerSplitEditorProvider();
        assertTrue(provider.accept(getProject(), template("templates/a.ftl", "x")));
        assertTrue(provider.accept(getProject(), template("templates/b.ftlh", "x")));
        assertTrue(provider.accept(getProject(), template("templates/c.ftlx", "<x/>")));
        assertFalse(provider.accept(getProject(), template("templates/d.html", "x")));
    }

    public void testRendersWithGeneratedDataAndRelativeIncludes() {
        template("templates/mail/parts/footer.ftl", "<footer>${company.name}</footer>");
        VirtualFile file = template("templates/mail/welcome.ftlh", """
                <h1>Hello ${customer.name}!</h1>
                <#list orders as order><p>${order.productName}: ${order.total}</p></#list>
                <#include "parts/footer.ftl">
                """);
        PreviewController controller = open(file);
        RenderResult result = awaitResult(successContaining("Hello Jane Doe!"));
        String output = ((RenderResult.Success) result).output();
        assertTrue(output, output.contains("<p>Laptop: ") && output.contains("<footer>Acme Corp</footer>"));
        assertTrue(preview().lastPageHtml().contains("Content-Security-Policy"));
        assertTrue(controller.sampleDataText().contains("\"productName\": \"Laptop\""));
        assertFalse(controller.isCustomData());
    }

    public void testRefreshesOnEditAndShowsDiagnosticsInsteadOfStaleOutput() {
        VirtualFile file = template("templates/page.ftl", "<p>${greeting}</p>");
        open(file);
        awaitResult(r -> r instanceof RenderResult.Success);

        setText(file, "<p>Updated ${user.name}</p>");
        awaitResult(successContaining("Updated Jane Doe"));

        setText(file, "<p>Broken <#if user.name></p>");
        RenderResult broken = awaitResult(r -> r instanceof RenderResult.Failure);
        assertEquals(RenderDiagnostic.Kind.TEMPLATE_SYNTAX, ((RenderResult.Failure) broken).diagnostic().kind());
        assertFalse("stale output must not be shown", preview().lastPageHtml().contains("Updated Jane Doe"));
        assertTrue(preview().statusText().contains("syntax error"));

        setText(file, "<p>Fixed ${user.name}</p>");
        awaitResult(successContaining("Fixed Jane Doe"));
    }

    public void testIncludedTemplateEditsRefreshThePreview() {
        VirtualFile footer = template("templates/footer.ftl", "v1");
        open(template("templates/page.ftl", "[<#include \"footer.ftl\">]"));
        awaitResult(successContaining("[v1]"));
        setText(footer, "v2");
        awaitResult(successContaining("[v2]"));
    }

    public void testCustomSampleDataIsKeptPersistedAndCanBeRegenerated() {
        VirtualFile file = template("templates/page.ftl", "${customer.name}");
        PreviewController controller = open(file);
        awaitResult(successContaining("Jane Doe"));

        controller.editSampleData("{\"customer\": {\"name\": \"Ada Lovelace\"}}");
        awaitResult(successContaining("Ada Lovelace"));
        assertTrue(controller.isCustomData());
        assertNotNull(SampleDataService.getInstance(getProject()).get(file));

        // Template changes do not overwrite the user's data; new variables are generated.
        setText(file, "${customer.name} from ${customer.city}");
        RenderResult merged = awaitResult(successContaining("from"));
        assertTrue(((RenderResult.Success) merged).output().startsWith("Ada Lovelace from "));
        assertTrue(controller.sampleDataText().contains("Ada Lovelace"));

        controller.editSampleData("{ not json");
        RenderResult invalid = awaitResult(r -> r instanceof RenderResult.Failure);
        assertEquals(RenderDiagnostic.Kind.SAMPLE_DATA, ((RenderResult.Failure) invalid).diagnostic().kind());

        controller.regenerateSampleData();
        awaitResult(successContaining("Jane Doe from"));
        assertFalse(controller.isCustomData());
        assertNull(SampleDataService.getInstance(getProject()).get(file));
    }

    public void testEmptyTemplateShowsEmptyState() {
        open(template("templates/empty.ftl", ""));
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (!preview().statusText().contains("Nothing to preview") && System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            TimeoutUtil.sleep(20);
        }
        assertTrue(preview().statusText(), preview().statusText().contains("Nothing to preview"));
    }

    public void testJavaTypesImproveSampleData() {
        myFixture.addClass("package com.acme; public enum Status { ACTIVE, SUSPENDED }");
        myFixture.addClass("""
                package com.acme;
                public class Customer {
                    public String getName() { return null; }
                    public Status getStatus() { return null; }
                    public int getLoyaltyPoints() { return 0; }
                    public boolean isVip() { return false; }
                }""");
        myFixture.addClass("package com.acme; public record Invoice(String number, java.util.List<Line> lines) {}");
        myFixture.addClass("package com.acme; public record Line(String description, int quantity) {}");

        VirtualFile file = template("templates/typed.ftl", """
                <#-- @ftlvariable name="invoice" type="com.acme.Invoice" -->
                ${customer.status} ${customer.loyaltyPoints} <#if customer.vip>VIP</#if>
                <#list invoice.lines as line>${line.quantity}x</#list>
                """);
        PreviewController controller = open(file);
        RenderResult result = awaitResult(r -> r instanceof RenderResult.Success);
        String output = ((RenderResult.Success) result).output();
        assertTrue(output, output.contains("ACTIVE") && output.contains("VIP"));
        String data = controller.sampleDataText();
        assertTrue(data, data.contains("\"status\": \"ACTIVE\""));
        assertTrue(data, data.matches("(?s).*\"quantity\": \\d+.*"));
    }

    public void testFragmentsFromSiblingFolderAndProjectImages() throws Exception {
        template("templates/fragments/header.ftl", "<#macro render><header><img src=\"/images/logo.png\"> ${model.clientConfig.contactEmail}</header></#macro>");
        VirtualFile logo = myFixture.addFileToProject("static/images/logo.png", "").getVirtualFile();
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            try {
                logo.setBinaryContent(new byte[]{(byte) 0x89, 'P', 'N', 'G'});
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        VirtualFile report = template("templates/reports/summary.ftl",
                "<#import \"fragments/header.ftl\" as header><@header.render/><p>${reportDate!\"N/A\"}</p>");
        open(report);
        RenderResult result = awaitResult(successContaining("jane.doe@example.com"));
        String output = ((RenderResult.Success) result).output();
        assertTrue(output, output.contains("<img src=\"data:image/png;base64,"));
        assertTrue(preview().statusText(), preview().statusText().contains("resolved leniently"));
    }

    public void testDisposingTheEditorStopsBackgroundWork() {
        VirtualFile file = template("templates/page.ftl", "${x}");
        PreviewController controller = open(file);
        awaitResult(r -> true);
        Disposer.dispose(editor);
        editor = null;
        results.clear();
        controller.refresh();
        long deadline = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            TimeoutUtil.sleep(20);
        }
        assertTrue("no results after disposal", results.isEmpty());
    }
}
