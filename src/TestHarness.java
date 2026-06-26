import com.emudhra.esign.eSign.AppearanceType;
import com.emudhra.esign.pdf.PdfEngine;
import com.emudhra.esign.pdf.model.AppearanceSpec;
import com.emudhra.esign.pdf.model.PdfRect;
import com.emudhra.esign.pdf.model.PreSignResult;
import com.emudhra.esign.pdf.model.SignatureFieldSpec;
import com.emudhra.esign.pdf.model.SignatureMetadata;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Standalone empirical gate for the multi-page signature fix. NOT part of the SDK jar
 * (excluded in build.xml). Uses ONLY the vendored PDFBox + PdfEngine + model classes —
 * no JUnit, no new dependencies.
 *
 * Verifies that prepareSignature produces ONE PDSignature/digest (one /ByteRange) but N
 * widget annotations — one per selected page — for every page-selection scenario, and that
 * an out-of-range page is rejected.
 *
 * Run (after `ant compile`):
 *   java -cp "build/classes;lib/*" TestHarness        (Windows)
 *   java -cp "build/classes:lib/*" TestHarness        (Unix)
 */
public final class TestHarness {

    public static void main(String[] args) {
        try {
            byte[] pdf = make3PagePdf();

            runScenario("All{1,2,3}",        pdf, new int[]{1, 2, 3});
            runScenario("Even{2}",           pdf, new int[]{2});
            runScenario("Odd{1,3}",          pdf, new int[]{1, 3});
            runScenario("Specify{2,3}",      pdf, new int[]{2, 3});
            runScenario("PageLevelMulti{1,2,3}", pdf, new int[]{1, 2, 3});

            // Large-document 'All': 60-page doc signed on every page must NOT fail (no page cap)
            // and must still produce ONE signature with N=60 widgets.
            byte[] pdf60 = makeNPagePdf(60);
            int[] all60 = new int[60];
            for (int i = 0; i < 60; i++) all60[i] = i + 1;
            runScenario("All60{1..60}", pdf60, all60);

            runNegativePageOutOfRange(pdf);

            System.out.println("ALL PASS");
        } catch (Throwable t) {
            System.out.println("FAIL: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }

    // -----------------------------------------------------------------------
    // Scenarios
    // -----------------------------------------------------------------------

    private static void runScenario(String name, byte[] pdf, int[] pages) throws Exception {
        List<PdfRect> rects = new ArrayList<>();
        List<Integer> pageList = new ArrayList<>();
        for (int p : pages) {
            pageList.add(p);
            // A simple visible rect on each selected page
            rects.add(new PdfRect(50f, 50f, 250f, 110f));
        }

        SignatureFieldSpec spec = new SignatureFieldSpec(
                rects, pageList, /*isCoSign*/ false,
                standardAppearance(), metadata(), /*border*/ null);

        PreSignResult result = new PdfEngine().prepareSignature(pdf, spec, 21000);
        byte[] signed = result.preSignedPdfBytes;
        assertTrue(signed != null && signed.length > 0, name + ": preSignedPdfBytes empty");

        // Exactly ONE /ByteRange (one digest/PKCS7) regardless of widget count
        int byteRangeCount = countOccurrences(signed, "/ByteRange");
        assertTrue(byteRangeCount == 1,
                name + ": expected exactly 1 /ByteRange but found " + byteRangeCount);

        try (PDDocument doc = Loader.loadPDF(signed)) {
            // Exactly one signature dictionary
            List<PDSignature> sigs = doc.getSignatureDictionaries();
            assertTrue(sigs.size() == 1,
                    name + ": expected 1 signature dictionary but found " + sigs.size());

            // Exactly one PDSignatureField in the AcroForm
            PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm(null);
            assertTrue(acroForm != null, name + ": AcroForm missing");
            int sigFieldCount = 0;
            PDSignatureField sigField = null;
            for (PDField f : acroForm.getFields()) {
                if (f instanceof PDSignatureField) {
                    sigFieldCount++;
                    sigField = (PDSignatureField) f;
                }
            }
            assertTrue(sigFieldCount == 1,
                    name + ": expected 1 PDSignatureField but found " + sigFieldCount);

            // N widgets, one per selected page, each with a positive rect on the right page
            List<PDAnnotationWidget> widgets = sigField.getWidgets();
            assertTrue(widgets.size() == pages.length,
                    name + ": expected " + pages.length + " widgets but found " + widgets.size());

            for (int i = 0; i < widgets.size(); i++) {
                PDAnnotationWidget w = widgets.get(i);
                PDRectangle r = w.getRectangle();
                assertTrue(r != null && r.getWidth() > 0 && r.getHeight() > 0,
                        name + ": widget " + i + " has non-positive rect");
                PDPage wpage = w.getPage();
                assertTrue(wpage != null, name + ": widget " + i + " has no /P page");
                int idx = doc.getPages().indexOf(wpage);
                assertTrue(idx == pages[i] - 1,
                        name + ": widget " + i + " on page index " + idx
                                + " but expected " + (pages[i] - 1));
            }
        }

        System.out.println("PASS " + name);
    }

    private static void runNegativePageOutOfRange(byte[] pdf) throws Exception {
        List<PdfRect> rects = new ArrayList<>();
        List<Integer> pageList = new ArrayList<>();
        pageList.add(99); // out of range (doc has 3 pages)
        rects.add(new PdfRect(50f, 50f, 250f, 110f));

        SignatureFieldSpec spec = new SignatureFieldSpec(
                rects, pageList, false, standardAppearance(), metadata(), null);

        boolean threw = false;
        try {
            new PdfEngine().prepareSignature(pdf, spec, 21000);
        } catch (java.io.IOException expected) {
            threw = true;
        }
        assertTrue(threw, "Negative{page>count}: expected IOException for out-of-range page");
        System.out.println("PASS Negative{page>count}");
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private static byte[] make3PagePdf() throws Exception {
        return makeNPagePdf(3);
    }

    private static byte[] makeNPagePdf(int pageCount) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                doc.addPage(new PDPage(PDRectangle.A4));
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static AppearanceSpec standardAppearance() {
        return new AppearanceSpec(
                AppearanceType.StandardSignature,
                "Digitally Signed\nTest Harness", /*fontSize*/ 0, /*fontColor*/ null,
                /*acro6Layers*/ false, /*certified*/ false,
                /*oneLinerText*/ null,
                /*imageBytes*/ null,
                /*advanceImageType*/ null, /*advanceSvgBytes*/ null,
                /*leftSideText*/ null, /*rightSideText*/ null,
                /*rightBorderRgb*/ null, /*leftBorderRgb*/ null);
    }

    private static SignatureMetadata metadata() {
        return new SignatureMetadata(
                /*reason*/ "Test", /*location*/ "Test", /*signerName*/ "Tester",
                /*creator*/ "TestHarness", /*signDate*/ Calendar.getInstance());
    }

    // -----------------------------------------------------------------------
    // Assert helpers
    // -----------------------------------------------------------------------

    private static int countOccurrences(byte[] data, String needle) {
        String hay = new String(data, StandardCharsets.ISO_8859_1);
        int count = 0;
        int from = 0;
        while (true) {
            int idx = hay.indexOf(needle, from);
            if (idx < 0) break;
            count++;
            from = idx + needle.length();
        }
        return count;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new RuntimeException(message);
        }
    }
}
