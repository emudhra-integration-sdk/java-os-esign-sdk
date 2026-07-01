package com.emudhra.esign.pdf;

import com.emudhra.esign.Enums.ImageType;
import com.emudhra.esign.pdf.model.AppearanceSpec;
import com.emudhra.esign.pdf.model.PageTextMatch;
import com.emudhra.esign.pdf.model.PdfColor;
import com.emudhra.esign.pdf.model.PdfRect;
import com.emudhra.esign.pdf.model.PreSignResult;
import com.emudhra.esign.pdf.model.SignatureFieldSpec;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.XMLResourceDescriptor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.visible.PDFTemplateCreator;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.visible.PDVisibleSigBuilder;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.visible.PDVisibleSignDesigner;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * Single PDFBox-based implementation for all PDF operations in the eSign flow.
 * Stateless and thread-safe (no instance fields).
 */
public final class PdfEngine {

    private static final int DPI = 72;

    // -----------------------------------------------------------------------
    // Document utilities
    // -----------------------------------------------------------------------

    private PDDocument loadDoc(byte[] bytes, String password) throws IOException {
        return (password != null && !password.isEmpty())
            ? Loader.loadPDF(bytes, password)
            : Loader.loadPDF(bytes);
    }

    public int getPageCount(byte[] pdfBytes) throws IOException {
        return getPageCount(pdfBytes, null);
    }

    public int getPageCount(byte[] pdfBytes, String password) throws IOException {
        try (PDDocument doc = loadDoc(pdfBytes, password)) {
            return doc.getNumberOfPages();
        }
    }

    public PdfRect getPageSize(byte[] pdfBytes, int page) throws IOException {
        return getPageSize(pdfBytes, page, null);
    }

    public PdfRect getPageSize(byte[] pdfBytes, int page, String password) throws IOException {
        try (PDDocument doc = loadDoc(pdfBytes, password)) {
            PDPage p = doc.getPage(page - 1);
            PDRectangle r = p.getMediaBox();
            return new PdfRect(r.getLowerLeftX(), r.getLowerLeftY(),
                               r.getUpperRightX(), r.getUpperRightY());
        }
    }

    public boolean isValidPdf(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return doc.getNumberOfPages() >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Repair a rebuilt PDF by doing a load + full save round-trip.
     */
    public byte[] normalizeDocument(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    // -----------------------------------------------------------------------
    // Phase 1: prepareSignature — deferred-signing pattern
    // -----------------------------------------------------------------------

    /**
     * Creates a PDF incremental update with a zeroed /Contents placeholder and
     * returns the bytes to hash plus the position data for later injection.
     * Uses PDFBox's saveIncrementalForExternalSigning — the documented two-phase API.
     */
    public PreSignResult prepareSignature(byte[] pdfBytes,
                                          SignatureFieldSpec spec,
                                          int contentEstimated) throws IOException {
        return prepareSignature(pdfBytes, spec, contentEstimated, null);
    }

    public PreSignResult prepareSignature(byte[] pdfBytes,
                                          SignatureFieldSpec spec,
                                          int contentEstimated,
                                          String password) throws IOException {
        try (PDDocument doc = loadDoc(pdfBytes, password)) {
            // All-or-nothing: validate the full multi-page spec BEFORE any mutation.
            validateMultiPageSpec(spec, doc.getNumberOfPages());

            PDSignature sig = new PDSignature();
            sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            sig.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            if (spec.metadata.signDate != null) sig.setSignDate(spec.metadata.signDate);
            if (spec.metadata.reason   != null && !spec.metadata.reason.isEmpty())   sig.setReason(spec.metadata.reason);
            if (spec.metadata.location != null && !spec.metadata.location.isEmpty()) sig.setLocation(spec.metadata.location);
            if (spec.metadata.signerName != null && !spec.metadata.signerName.isEmpty()) sig.setName(spec.metadata.signerName);

            SignatureOptions options = new SignatureOptions();
            options.setPreferredSignatureSize(contentEstimated);

            boolean isStandard = spec.appearance == null
                    || spec.appearance.mode == null
                    || spec.appearance.mode == com.emudhra.esign.eSign.AppearanceType.StandardSignature;

            boolean isBackgroundImage = spec.appearance != null
                    && spec.appearance.mode == com.emudhra.esign.eSign.AppearanceType.BackgroundImage;

            boolean isSignatureImage = spec.appearance != null
                    && spec.appearance.mode == com.emudhra.esign.eSign.AppearanceType.SignatureImage;

            boolean isAdvanceSignature = spec.appearance != null
                    && spec.appearance.mode == com.emudhra.esign.eSign.AppearanceType.advanceSignature;

            // These modes bypass the PNG pipeline and build native PDF content streams
            boolean useNativeAppearance = isStandard || isBackgroundImage || isSignatureImage || isAdvanceSignature;

            if (!spec.rects.isEmpty()) {
                // First widget's page; the remaining widgets are attached during de-merge.
                options.setPage(spec.pages.get(0) - 1);
            }

            doc.addSignature(sig, options);

            // coSign=false → certify the document so no further modifications are allowed.
            // coSign=true  → normal incremental signature; additional co-signers can sign later.
            if (!spec.isCoSign) {
                setMDPPermission(doc, sig, 1);
            }

            // De-merge the single widget addSignature created into N per-page widgets that
            // share ONE /V (one digest/PKCS7/ByteRange), then build the active mode's
            // appearance on every widget. /V stays only on the field; kids stay pure widgets.
            if (!spec.rects.isEmpty()) {
                PDSignatureField field = findSignatureField(doc, sig);
                if (field != null) {
                    List<PDAnnotationWidget> widgets = splitIntoWidgets(doc, field, spec);
                    PdfRect r0 = spec.rects.get(0);

                    // Build the active mode's shared resource ONCE (decode/rasterize a single time).
                    PDImageXObject sharedImg = null;
                    PDImageXObject sharedAdv = null;
                    PDImageXObject sharedTpl = null;
                    try {
                        if (isBackgroundImage || isSignatureImage) {
                            if (spec.appearance != null && spec.appearance.imageBytes != null
                                    && spec.appearance.imageBytes.length > 0) {
                                sharedImg = PDImageXObject.createFromByteArray(
                                        doc, spec.appearance.imageBytes, "sig");
                            }
                        } else if (isAdvanceSignature) {
                            sharedAdv = buildAdvanceXObjectOnce(doc, spec.appearance, r0);
                        } else if (!useNativeAppearance) {
                            // OneLiner / ColoredGraphic: Java2D raster template, built once.
                            sharedTpl = buildRasterTemplateOnce(doc, spec.appearance, r0);
                        }
                    } catch (Exception e) {
                        // shared-resource build failure is non-fatal; widgets fall back gracefully
                    }

                    for (int i = 0; i < widgets.size(); i++) {
                        PDAnnotationWidget widget = widgets.get(i);
                        PdfRect rect = spec.rects.get(i);
                        // Per-widget appearance failure must not abort the others or the signature.
                        try {
                            if (isStandard) {
                                buildStandardTextAppearance(doc, widget, spec.appearance, rect, spec.border);
                            } else if (isBackgroundImage) {
                                buildNativeImageAppearance(doc, widget, spec.appearance, rect, true, spec.border, sharedImg);
                            } else if (isSignatureImage) {
                                buildNativeImageAppearance(doc, widget, spec.appearance, rect, false, spec.border, sharedImg);
                            } else if (isAdvanceSignature) {
                                buildNativeAdvanceAppearance(doc, widget, spec.appearance, rect, spec.border, sharedAdv);
                            } else {
                                buildNativeRasterAppearance(doc, widget, rect, spec.border, sharedTpl);
                            }
                        } catch (Exception e) {
                            // single-widget appearance failure is non-fatal
                        }
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ExternalSigningSupport externalSigning = doc.saveIncrementalForExternalSigning(baos);

            // Inject zero-filled placeholder so the output PDF is complete and parseable
            externalSigning.setSignature(new byte[contentEstimated]);

            byte[] output = baos.toByteArray();

            // Parse /ByteRange from the saved output
            long[] byteRange = parseByteRange(output);
            if (byteRange == null) {
                throw new IOException("Could not locate /ByteRange in saved PDF");
            }

            // placeholderPosition = start of hex content (byte after '<')
            int placeholderPos = (int) byteRange[1] + 1;

            // Bytes to hash = ranges excluded by ByteRange (everything except /Contents hex)
            byte[] bytesToHash = extractRangeBytes(output, byteRange);

            return new PreSignResult(placeholderPos, output.length, output, bytesToHash, byteRange);
        }
    }

    // -----------------------------------------------------------------------
    // Phase 2: injectSignature — overwrite placeholder in-place
    // -----------------------------------------------------------------------

    public byte[] injectSignature(byte[] pkcs7Bytes, PreSignResult preSign,
                                  int contentEstimated) throws IOException {
        byte[] output = Arrays.copyOf(preSign.preSignedPdfBytes, preSign.outputBufferSize);

        // Hex-encode PKCS7, uppercase, pad to placeholder length with trailing zeros
        long[] br = preSign.byteRange != null ? preSign.byteRange : parseByteRange(output);
        int hexLen = (br != null && br[2] > br[1] + 2) ?
            (int) (br[2] - br[1] - 2) : // exclude '<' and '>'
            contentEstimated * 2;

        StringBuilder hex = new StringBuilder();
        for (byte b : pkcs7Bytes) {
            hex.append(String.format("%02X", b & 0xFF));
        }
        while (hex.length() < hexLen) hex.append('0');

        byte[] hexBytes = hex.toString().getBytes(StandardCharsets.US_ASCII);
        if (hexBytes.length > hexLen) {
            throw new IOException("PKCS7 signature (" + pkcs7Bytes.length +
                " bytes) exceeds reserved placeholder (" + hexLen / 2 + " bytes)");
        }
        System.arraycopy(hexBytes, 0, output, preSign.placeholderPosition, hexBytes.length);
        return output;
    }

    // -----------------------------------------------------------------------
    // Phase 3: patchSignatureAppearance — incremental append
    // -----------------------------------------------------------------------

    public byte[] patchSignatureAppearance(byte[] signedPdf,
                                           String signerName,
                                           String aadhaarSuffix) throws IOException {
        try (PDDocument doc = Loader.loadPDF(signedPdf)) {
            PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
            if (acroForm == null) return signedPdf;

            List<PDField> fields = acroForm.getFields();
            if (fields == null || fields.isEmpty()) return signedPdf;

            for (PDField field : fields) {
                if (!(field instanceof PDSignatureField)) continue;
                PDSignatureField sigField = (PDSignatureField) field;
                PDSignature sig = sigField.getSignature();

                List<PDAnnotationWidget> widgets = sigField.getWidgets();
                if (widgets == null || widgets.isEmpty()) continue;

                // Build appearance text (shared across all widgets of this field)
                String reason = sig != null ? sig.getReason() : null;
                Calendar signDate = sig != null ? sig.getSignDate() : null;
                String dateStr = "";
                if (signDate != null) {
                    dateStr = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss").format(signDate.getTime());
                }

                // Multi-page: rewrite the /AP /N of EVERY widget, not just the first.
                // Pre-existing DocMDP (S3) post-sign-patch hazard preserved, not worsened
                // (AP-only rewrite, no new structure added).
                for (PDAnnotationWidget widget : widgets) {
                    PDRectangle rect = widget.getRectangle();
                    if (rect == null) continue;

                    float w = rect.getWidth();
                    float h = rect.getHeight();
                    if (w <= 0 || h <= 0) continue;

                    // Create the new Form XObject appearance
                    PDAppearanceStream ap = buildTextAppearance(doc, w, h,
                            signerName, aadhaarSuffix, reason, dateStr);

                    PDAppearanceDictionary apDict = new PDAppearanceDictionary();
                    apDict.setNormalAppearance(ap);
                    widget.setAppearance(apDict);
                    widget.getCOSObject().setNeedToBeUpdated(true);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.saveIncremental(baos);
            return baos.toByteArray();
        }
    }

    private PDAppearanceStream buildTextAppearance(PDDocument doc, float w, float h,
            String signerName, String aadhaarSuffix, String reason, String dateStr) throws IOException {
        PDAppearanceStream ap = new PDAppearanceStream(doc);
        ap.setResources(new org.apache.pdfbox.pdmodel.PDResources());
        ap.setBBox(new PDRectangle(w, h));

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);
        float fontSize = 7f;
        float leading = 9f;
        float margin = 3f;

        try (PDPageContentStream cs = new PDPageContentStream(doc, ap)) {
            // White background
            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.addRect(0, 0, w, h);
            cs.fill();

            cs.setNonStrokingColor(0f, 0f, 0f);
            cs.beginText();
            cs.setFont(font, fontSize);
            cs.setLeading(leading);
            cs.newLineAtOffset(margin, h - leading);

            cs.showText("Digitally Signed by");
            cs.newLine();
            cs.showText("Name : " + nvl(signerName));
            cs.newLine();
            cs.showText("Aadhaar No : **** **** " + nvl(aadhaarSuffix));
            if (reason != null && !reason.isEmpty()) {
                cs.newLine();
                cs.showText("Reason: " + reason);
            }
            if (!dateStr.isEmpty()) {
                cs.newLine();
                cs.showText("Date : " + dateStr);
            }
            cs.endText();
        }
        return ap;
    }

    // -----------------------------------------------------------------------
    // Text extraction
    // -----------------------------------------------------------------------

    public List<PageTextMatch> findText(byte[] pdfBytes, String regexPattern) throws IOException {
        return findText(pdfBytes, regexPattern, null);
    }

    public List<PageTextMatch> findText(byte[] pdfBytes, String regexPattern, String password) throws IOException {
        List<PageTextMatch> results = new ArrayList<>();
        try (PDDocument doc = loadDoc(pdfBytes, password)) {
            int numPages = doc.getNumberOfPages();
            for (int p = 1; p <= numPages; p++) {
                final int pageNum = p;
                List<TextPosition> pagePositions = new ArrayList<>();
                PDFTextStripper stripper = new PDFTextStripper() {
                    @Override
                    protected void writeString(String text, List<TextPosition> textPositions) {
                        pagePositions.addAll(textPositions);
                    }
                };
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                stripper.getText(doc);

                // Build full page text and character position map
                StringBuilder sb = new StringBuilder();
                for (TextPosition tp : pagePositions) sb.append(tp.getUnicode());
                String fullText = sb.toString();

                java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile(regexPattern).matcher(fullText);
                while (m.find()) {
                    int start = m.start();
                    int end   = m.end() - 1;
                    if (start >= pagePositions.size() || end >= pagePositions.size()) continue;
                    TextPosition tpStart = pagePositions.get(start);
                    TextPosition tpEnd   = pagePositions.get(end);
                    results.add(new PageTextMatch(pageNum,
                            tpStart.getXDirAdj(), tpStart.getYDirAdj(),
                            tpEnd.getXDirAdj() + tpEnd.getWidthDirAdj(),
                            tpEnd.getYDirAdj() + tpEnd.getHeightDir()));
                }
            }
        }
        return results;
    }

    // -----------------------------------------------------------------------
    // Multi-page de-merge: ONE field + N widgets sharing ONE /V
    // -----------------------------------------------------------------------

    /**
     * Validates the entire multi-page spec up-front so prepareSignature is all-or-nothing
     * (no partial mutation of the document on a bad request).
     */
    private void validateMultiPageSpec(SignatureFieldSpec spec, int pageCount) throws IOException {
        if (spec == null || spec.pages == null || spec.rects == null) {
            throw new IOException("Invalid signature spec: spec/pages/rects must not be null");
        }
        if (spec.pages.size() != spec.rects.size()) {
            throw new IOException("Invalid signature spec: pages.size (" + spec.pages.size()
                    + ") != rects.size (" + spec.rects.size() + ")");
        }
        int n = spec.pages.size();
        // No upper cap on page count: the pre-change path signed large 'All' docs successfully
        // (appearance on page 1), and resource-sharing (one shared XObject/SVG/template reused
        // across all widgets) mitigates the memory concern — a large 'All' must produce N
        // widgets, not a hard failure.
        for (int i = 0; i < n; i++) {
            Integer page = spec.pages.get(i);
            if (page == null || page < 1 || page > pageCount) {
                throw new IOException("Signature page out of range: " + page
                        + " (document has " + pageCount + " page(s))");
            }
            PdfRect r = spec.rects.get(i);
            if (r == null || r.getWidth() <= 0 || r.getHeight() <= 0) {
                throw new IOException("Invalid signature rectangle at index " + i + ": " + r);
            }
        }
    }

    /**
     * Locates the PDSignatureField that owns the given PDSignature by COS identity.
     * Mirrors the lookup idiom previously inlined in the appearance helpers.
     */
    private PDSignatureField findSignatureField(PDDocument doc, PDSignature sig) throws IOException {
        PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm(null);
        if (acroForm == null) return null;
        for (PDField field : acroForm.getFields()) {
            if (!(field instanceof PDSignatureField)) continue;
            PDSignatureField sf = (PDSignatureField) field;
            PDSignature sfSig = sf.getSignature();
            if (sfSig != null && sfSig.getCOSObject().equals(sig.getCOSObject())) {
                return sf;
            }
        }
        return null;
    }

    /**
     * De-merges the single field+widget dictionary that addSignature created into a field
     * with N pure /Kids widget annotations — one per selected page — all sharing the field's
     * single /V. /V and /FT remain ONLY on the field dict; the kids carry no /V, /FT or shared
     * digest. This is what makes one signature (one digest/PKCS7/ByteRange) appear on N pages.
     */
    private List<PDAnnotationWidget> splitIntoWidgets(PDDocument doc, PDSignatureField field,
            SignatureFieldSpec spec) throws IOException {
        COSDictionary fd = field.getCOSObject();
        int n = spec.pages.size();

        // Keys that belong to the widget (annotation), not the field — peeled onto kid 0.
        COSName[] WKEYS = {
            COSName.SUBTYPE, COSName.RECT, COSName.AP, COSName.P, COSName.F,
            COSName.getPDFName("MK"), COSName.getPDFName("BS")
        };

        // --- Widget 0: move the widget keys off the merged field dict into a pure kid ---
        COSDictionary kid0 = new COSDictionary();
        for (COSName k : WKEYS) {
            COSBase v = fd.getItem(k);
            if (v != null) {
                kid0.setItem(k, v);
                fd.removeItem(k);
            }
        }
        kid0.setItem(COSName.TYPE, COSName.ANNOT);
        kid0.setItem(COSName.SUBTYPE, COSName.WIDGET);
        kid0.setItem(COSName.PARENT, fd);

        // The parent is now a pure field (/FT,/T,/V,/Ff,/Kids) — drop the leftover /Type=/Annot
        // that the merged field+widget dict carried so it is no longer mislabelled as an annotation.
        fd.removeItem(COSName.TYPE);

        PDAnnotationWidget w0 = new PDAnnotationWidget(kid0);
        PdfRect r0 = spec.rects.get(0);
        w0.setRectangle(new PDRectangle(r0.llx, r0.lly, r0.getWidth(), r0.getHeight()));
        PDPage page0 = doc.getPage(spec.pages.get(0) - 1);
        kid0.setItem(COSName.P, page0.getCOSObject());

        COSArray kids = new COSArray();
        kids.add(kid0);
        List<PDAnnotationWidget> out = new ArrayList<>();
        out.add(w0);
        // Replace the merged field ref on page 0 with the de-merged widget.
        attachWidgetToPage(doc, page0, w0, fd);

        // --- Widgets 1..n-1: brand-new pure widget dicts (no /V, no /FT, no /AP yet) ---
        for (int i = 1; i < n; i++) {
            COSDictionary kid = new COSDictionary();
            kid.setItem(COSName.TYPE, COSName.ANNOT);
            kid.setItem(COSName.SUBTYPE, COSName.WIDGET);
            kid.setItem(COSName.PARENT, fd);
            kid.setItem(COSName.F, COSInteger.get(4)); // Print flag

            PDAnnotationWidget wi = new PDAnnotationWidget(kid);
            PdfRect ri = spec.rects.get(i);
            wi.setRectangle(new PDRectangle(ri.llx, ri.lly, ri.getWidth(), ri.getHeight()));
            PDPage pagei = doc.getPage(spec.pages.get(i) - 1);
            kid.setItem(COSName.P, pagei.getCOSObject());

            kids.add(kid);
            out.add(wi);
            attachWidgetToPage(doc, pagei, wi, null);
        }

        fd.setItem(COSName.KIDS, kids);
        fd.setNeedToBeUpdated(true);
        return out;
    }

    /**
     * Adds a widget to a page's /Annots (keeping /Annots a DIRECT object), optionally first
     * removing a prior reference (the merged field dict) by COS identity. Marks both the page
     * and the widget for incremental update.
     */
    private void attachWidgetToPage(PDDocument doc, PDPage page, PDAnnotationWidget widget,
            COSDictionary removeRef) throws IOException {
        List<PDAnnotation> annots = page.getAnnotations();
        if (removeRef != null) {
            annots.removeIf(a -> a.getCOSObject() == removeRef);
        }
        boolean present = false;
        for (PDAnnotation a : annots) {
            if (a.getCOSObject() == widget.getCOSObject()) { present = true; break; }
        }
        if (!present) annots.add(widget);
        page.setAnnotations(annots);
        page.getCOSObject().setNeedToBeUpdated(true);
        widget.getCOSObject().setNeedToBeUpdated(true);
    }

    /**
     * Builds the advanceSignature background image XObject ONCE (SVG rasterised at 2× or raster
     * decoded directly), so all N widgets share a single decode. Returns null if no image.
     */
    private PDImageXObject buildAdvanceXObjectOnce(PDDocument doc, AppearanceSpec ap, PdfRect r0)
            throws IOException {
        if (ap == null) return null;
        boolean hasSvg = ap.advanceImageType == ImageType.SVG
                && ap.advanceSvgBytes != null && ap.advanceSvgBytes.length > 0;
        boolean hasImage = ap.imageBytes != null && ap.imageBytes.length > 0;
        if (hasSvg) {
            byte[] png = rasterizeSvgToPng(ap.advanceSvgBytes,
                    (int) (r0.getWidth() * 2), (int) (r0.getHeight() * 2));
            return png != null ? PDImageXObject.createFromByteArray(doc, png, "svg") : null;
        } else if (hasImage) {
            return PDImageXObject.createFromByteArray(doc, ap.imageBytes, "adv");
        }
        return null;
    }

    /**
     * Renders the OneLiner/ColoredGraphic Java2D appearance ONCE to a PNG-backed XObject that
     * every widget reuses (drawn scaled into each widget's BBox). Non-fatal: returns null on failure.
     */
    private PDImageXObject buildRasterTemplateOnce(PDDocument doc, AppearanceSpec ap, PdfRect r0) {
        try {
            int w = Math.max(10, (int) r0.getWidth());
            int h = Math.max(10, (int) r0.getHeight());
            BufferedImage img = renderAppearance(ap, w, h);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", png);
            return PDImageXObject.createFromByteArray(doc, png.toByteArray(), "tpl");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Per-widget appearance for the raster (OneLiner/ColoredGraphic) modes: white base, the
     * shared template image scaled into the widget BBox, then the optional border.
     */
    private void buildNativeRasterAppearance(PDDocument doc, PDAnnotationWidget widget,
            PdfRect rect, com.emudhra.esign.pdf.model.BorderSpec border,
            PDImageXObject tpl) throws IOException {
        float w = rect.getWidth();
        float h = rect.getHeight();
        widget.setRectangle(new PDRectangle(rect.llx, rect.lly, w, h));

        PDAppearanceStream apStream = new PDAppearanceStream(doc);
        apStream.setResources(new org.apache.pdfbox.pdmodel.PDResources());
        apStream.setBBox(new PDRectangle(w, h));

        try (PDPageContentStream cs = new PDPageContentStream(doc, apStream)) {
            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.addRect(0, 0, w, h);
            cs.fill();
            if (tpl != null) {
                cs.drawImage(tpl, 0, 0, w, h);
            }
            drawBorder(cs, w, h, border);
        }

        PDAppearanceDictionary apDict = new PDAppearanceDictionary();
        apDict.getCOSObject().setDirect(true);
        apDict.setNormalAppearance(apStream);
        widget.setAppearance(apDict);
        widget.getCOSObject().setNeedToBeUpdated(true);
    }

    // -----------------------------------------------------------------------
    // StandardSignature: direct vector text appearance on the widget
    // -----------------------------------------------------------------------

    /**
     * After addSignature creates the invisible widget, this method:
     *  1. Patches the widget rect to the desired visible position
     *  2. Builds a PDAppearanceStream with real PDF text operators (no PNG/rasterization)
     *  3. Sets it as the widget's /AP /N stream
     */
    private void buildStandardTextAppearance(PDDocument doc, PDAnnotationWidget widget,
            AppearanceSpec ap, PdfRect desiredRect,
            com.emudhra.esign.pdf.model.BorderSpec border) throws IOException {
        float w = desiredRect.getWidth();
        float h = desiredRect.getHeight();

        // Restore the real rect (prepareNonVisibleSignature set it to [0 0 0 0])
        widget.setRectangle(new PDRectangle(desiredRect.llx, desiredRect.lly, w, h));

        // --- Prepare text lines ---
        String text = (ap != null && ap.layer2Text != null && !ap.layer2Text.trim().isEmpty())
                ? ap.layer2Text : "Digitally Signed.";
        String[] lines = text.split("\n");

        // Count non-empty lines to drive font-size calculation
        int lineCount = 0;
        for (String l : lines) if (!l.trim().isEmpty()) lineCount++;
        lineCount = Math.max(1, lineCount);

        float margin  = 3f;
        float availH  = h - 2 * margin;

        // Start from the configured/default size and shrink until all lines fit
        float fontSize = (ap != null && ap.fontSize > 0) ? (float) ap.fontSize : 9f;
        float leading  = fontSize * 1.5f;
        while (lineCount * leading > availH && fontSize > 4.5f) {
            fontSize -= 0.5f;
            leading   = fontSize * 1.5f;
        }

        // --- Build the /AP /N appearance stream ---
        PDAppearanceStream apStream = new PDAppearanceStream(doc);
        apStream.setResources(new org.apache.pdfbox.pdmodel.PDResources());
        apStream.setBBox(new PDRectangle(w, h));

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);

        try (PDPageContentStream cs = new PDPageContentStream(doc, apStream)) {
            // White background
            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.addRect(0, 0, w, h);
            cs.fill();

            // Text colour
            if (ap != null && ap.fontColor != null) {
                cs.setNonStrokingColor(
                    ap.fontColor.r / 255f,
                    ap.fontColor.g / 255f,
                    ap.fontColor.b / 255f);
            } else {
                cs.setNonStrokingColor(0f, 0f, 0f);
            }

            cs.beginText();
            cs.setFont(font, fontSize);
            cs.setLeading(leading);
            // First baseline just below the top edge
            cs.newLineAtOffset(margin, h - margin - fontSize);
            for (String line : lines) {
                String l = line.trim();
                cs.showText(l.isEmpty() ? " " : l);
                cs.newLine();
            }
            cs.endText();
            drawBorder(cs, w, h, border);
        }

        PDAppearanceDictionary apDict = new PDAppearanceDictionary();
        apDict.getCOSObject().setDirect(true);
        apDict.setNormalAppearance(apStream);
        widget.setAppearance(apDict);
        widget.getCOSObject().setNeedToBeUpdated(true);
    }

    // -----------------------------------------------------------------------
    // BackgroundImage / SignatureImage: native PDImageXObject + vector text
    // -----------------------------------------------------------------------

    /**
     * Builds a native PDF appearance stream using PDImageXObject (no Java2D re-encoding).
     * fullBackground=true  → image covers the full box width; text is overlaid on the right half.
     * fullBackground=false → image on the left 45%, text on the right 55% (split layout).
     */
    private void buildNativeImageAppearance(PDDocument doc, PDAnnotationWidget widget,
            AppearanceSpec ap, PdfRect desiredRect, boolean fullBackground,
            com.emudhra.esign.pdf.model.BorderSpec border, PDImageXObject sharedImg) throws IOException {
        float w = desiredRect.getWidth();
        float h = desiredRect.getHeight();
        widget.setRectangle(new PDRectangle(desiredRect.llx, desiredRect.lly, w, h));

        boolean hasImage = sharedImg != null;
        boolean hasText  = ap != null && ap.layer2Text != null && !ap.layer2Text.trim().isEmpty();

        // Image draw width and text start X depend on layout mode
        float imgDrawW  = fullBackground ? w : (hasImage && hasText ? w * 0.45f : (hasImage ? w : 0f));
        float textX     = fullBackground ? 3f : (hasImage ? imgDrawW + 4f : 3f);
        float textAreaW = fullBackground ? (w - 6f) : (w - textX - 3f);

        String[] lines = hasText ? ap.layer2Text.split("\n") : new String[0];
        int lineCount = 0;
        for (String l : lines) if (!l.trim().isEmpty()) lineCount++;
        lineCount = Math.max(1, lineCount);

        float margin   = 3f;
        float availH   = h - 2 * margin;
        float fontSize = (ap != null && ap.fontSize > 0) ? (float) ap.fontSize : 8f;
        float leading  = fontSize * 1.5f;

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);

        // Shrink until all lines fit vertically
        while (lineCount * leading > availH && fontSize > 4f) {
            fontSize -= 0.5f;
            leading   = fontSize * 1.5f;
        }

        // Shrink until the widest line fits horizontally in the text area
        if (hasText && textAreaW > 10) {
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                try {
                    float lineW = font.getStringWidth(line.trim()) / 1000f * fontSize;
                    while (lineW > textAreaW && fontSize > 4f) {
                        fontSize -= 0.5f;
                        leading   = fontSize * 1.5f;
                        lineW     = font.getStringWidth(line.trim()) / 1000f * fontSize;
                    }
                } catch (IOException ignored) {}
            }
            // Re-check height after width-driven shrink
            while (lineCount * leading > availH && fontSize > 4f) {
                fontSize -= 0.5f;
                leading   = fontSize * 1.5f;
            }
        }

        PDAppearanceStream apStream = new PDAppearanceStream(doc);
        apStream.setResources(new org.apache.pdfbox.pdmodel.PDResources());
        apStream.setBBox(new PDRectangle(w, h));

        final float fs = fontSize;
        final float ld = leading;

        try (PDPageContentStream cs = new PDPageContentStream(doc, apStream)) {
            // White base
            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.addRect(0, 0, w, h);
            cs.fill();

            // Embed the shared image XObject (decoded once for all widgets)
            if (hasImage) {
                try {
                    cs.drawImage(sharedImg, 0, 0, imgDrawW, h);
                } catch (Exception ignored) {}
            }

            // Thin separator line for split layout
            if (!fullBackground && hasImage && hasText) {
                cs.setStrokingColor(0.75f, 0.75f, 0.75f);
                cs.moveTo(imgDrawW + 2, margin);
                cs.lineTo(imgDrawW + 2, h - margin);
                cs.stroke();
            }

            // Vector text
            if (hasText) {
                if (ap.fontColor != null) {
                    cs.setNonStrokingColor(
                        ap.fontColor.r / 255f, ap.fontColor.g / 255f, ap.fontColor.b / 255f);
                } else {
                    cs.setNonStrokingColor(0f, 0f, 0f);
                }
                cs.beginText();
                cs.setFont(font, fs);
                cs.setLeading(ld);
                cs.newLineAtOffset(textX, h - margin - fs);
                for (String line : lines) {
                    String l = line.trim();
                    cs.showText(l.isEmpty() ? " " : l);
                    cs.newLine();
                }
                cs.endText();
            }
            drawBorder(cs, w, h, border);
        }

        PDAppearanceDictionary apDict = new PDAppearanceDictionary();
        apDict.getCOSObject().setDirect(true);
        apDict.setNormalAppearance(apStream);
        widget.setAppearance(apDict);
        widget.getCOSObject().setNeedToBeUpdated(true);
    }

    // -----------------------------------------------------------------------
    // advanceSignature: full-background image + left/right vector text
    // -----------------------------------------------------------------------

    /**
     * Image (SVG or raster) fills the entire box; leftSideText on the left half,
     * rightSideText on the right half — all drawn as native PDF operators on top.
     */
    private void buildNativeAdvanceAppearance(PDDocument doc, PDAnnotationWidget widget,
            AppearanceSpec ap, PdfRect desiredRect,
            com.emudhra.esign.pdf.model.BorderSpec border, PDImageXObject sharedAdv) throws IOException {
        float w = desiredRect.getWidth();
        float h = desiredRect.getHeight();
        widget.setRectangle(new PDRectangle(desiredRect.llx, desiredRect.lly, w, h));

        boolean hasLeft  = ap != null && ap.leftSideText  != null && !ap.leftSideText.trim().isEmpty();
        boolean hasRight = ap != null && ap.rightSideText != null && !ap.rightSideText.trim().isEmpty();

        String[] leftLines  = hasLeft  ? ap.leftSideText.split("\n")  : new String[0];
        String[] rightLines = hasRight ? ap.rightSideText.split("\n") : new String[0];

        int leftCount  = 0; for (String l : leftLines)  if (!l.trim().isEmpty()) leftCount++;
        int rightCount = 0; for (String l : rightLines) if (!l.trim().isEmpty()) rightCount++;
        int lineCount  = Math.max(Math.max(leftCount, rightCount), 1);

        float margin    = 3f;
        float halfW     = w / 2f;
        float availH    = h - 2 * margin;
        float colW      = halfW - 2 * margin;   // max width per text column

        float fontSize = (ap != null && ap.fontSize > 0) ? (float) ap.fontSize : 8f;
        float leading  = fontSize * 1.5f;

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);

        // Shrink for height
        while (lineCount * leading > availH && fontSize > 4f) {
            fontSize -= 0.5f;
            leading   = fontSize * 1.5f;
        }

        // Shrink for width — check every line in both columns
        List<String> allLines = new ArrayList<>();
        for (String l : leftLines)  if (!l.trim().isEmpty()) allLines.add(l.trim());
        for (String l : rightLines) if (!l.trim().isEmpty()) allLines.add(l.trim());
        for (String line : allLines) {
            try {
                float lw = font.getStringWidth(line) / 1000f * fontSize;
                while (lw > colW && fontSize > 4f) {
                    fontSize -= 0.5f;
                    leading   = fontSize * 1.5f;
                    lw        = font.getStringWidth(line) / 1000f * fontSize;
                }
            } catch (IOException ignored) {}
        }
        // Re-check height after width-driven shrink
        while (lineCount * leading > availH && fontSize > 4f) {
            fontSize -= 0.5f;
            leading   = fontSize * 1.5f;
        }

        final float fs = fontSize;
        final float ld = leading;

        PDAppearanceStream apStream = new PDAppearanceStream(doc);
        apStream.setResources(new org.apache.pdfbox.pdmodel.PDResources());
        apStream.setBBox(new PDRectangle(w, h));

        try (PDPageContentStream cs = new PDPageContentStream(doc, apStream)) {
            // White base
            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.addRect(0, 0, w, h);
            cs.fill();

            // Draw the shared background image XObject (SVG rasterised / raster decoded once)
            if (sharedAdv != null) {
                try {
                    cs.drawImage(sharedAdv, 0, 0, w, h);
                } catch (Exception ignored) {}
            }

            // Left-side text (left half, left-aligned)
            if (hasLeft) {
                cs.setNonStrokingColor(0f, 0f, 0f);
                cs.beginText();
                cs.setFont(font, fs);
                cs.setLeading(ld);
                cs.newLineAtOffset(margin, h - margin - fs);
                for (String line : leftLines) {
                    String l = line.trim();
                    cs.showText(l.isEmpty() ? " " : l);
                    cs.newLine();
                }
                cs.endText();
            }

            // Right-side text (right half, left-aligned within that half)
            if (hasRight) {
                cs.setNonStrokingColor(0f, 0f, 0f);
                cs.beginText();
                cs.setFont(font, fs);
                cs.setLeading(ld);
                cs.newLineAtOffset(halfW + margin, h - margin - fs);
                for (String line : rightLines) {
                    String l = line.trim();
                    cs.showText(l.isEmpty() ? " " : l);
                    cs.newLine();
                }
                cs.endText();
            }
            drawBorder(cs, w, h, border);
        }

        PDAppearanceDictionary apDict = new PDAppearanceDictionary();
        apDict.getCOSObject().setDirect(true);
        apDict.setNormalAppearance(apStream);
        widget.setAppearance(apDict);
        widget.getCOSObject().setNeedToBeUpdated(true);
    }

    /**
     * Rasterises an SVG byte array to PNG at the given pixel dimensions using Batik.
     * Rendered at the requested size for crisp appearance in the PDF.
     */
    private byte[] rasterizeSvgToPng(byte[] svgBytes, int w, int h) {
        try {
            String parser = XMLResourceDescriptor.getXMLParserClassName();
            SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
            org.w3c.dom.svg.SVGDocument svgDoc = (org.w3c.dom.svg.SVGDocument)
                    factory.createDocument(null, new ByteArrayInputStream(svgBytes));
            org.apache.batik.bridge.UserAgent ua = new UserAgentAdapter();
            DocumentLoader loader = new DocumentLoader(ua);
            BridgeContext ctx = new BridgeContext(ua, loader);
            ctx.setDynamicState(BridgeContext.STATIC);
            GraphicsNode gvtRoot = new GVTBuilder().build(ctx, svgDoc);

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,    RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);

            double scaleX = w / gvtRoot.getBounds().getWidth();
            double scaleY = h / gvtRoot.getBounds().getHeight();
            double scale  = Math.min(scaleX, scaleY);
            g.scale(scale, scale);
            gvtRoot.paint(g);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Appearance template creation (Java2D → PNG → PDVisibleSignDesigner)
    // -----------------------------------------------------------------------

    private InputStream buildAppearanceTemplate(PDDocument doc, SignatureFieldSpec spec,
                                                 byte[] pdfBytes) throws Exception {
        if (spec.rects.isEmpty()) return null;
        PdfRect rect = spec.rects.get(0);
        int w = Math.max(10, (int) rect.getWidth());
        int h = Math.max(10, (int) rect.getHeight());
        int pageNum = spec.pages.get(0); // 1-based

        BufferedImage img = renderAppearance(spec.appearance, w, h);

        // Convert to PNG
        ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", pngOut);
        byte[] pngBytes = pngOut.toByteArray();

        // Page height needed to flip Y axis
        PDPage page = doc.getPage(pageNum - 1);
        PDRectangle pageBox = page.getMediaBox();
        float pageH = pageBox.getHeight();

        PDVisibleSignDesigner designer = new PDVisibleSignDesigner(doc,
                new ByteArrayInputStream(pngBytes), pageNum);
        designer.xAxis(rect.llx)
                .yAxis(pageH - rect.ury)   // PDF Y=0 is bottom; flip
                .width(w)
                .height(h)
                .signatureFieldName("sig");

        return new PDFTemplateCreator(new PDVisibleSigBuilder()).buildPDF(designer);
    }

    /**
     * Renders the signature appearance to a BufferedImage using Java2D.
     * All 6 modes are handled here.
     */
    private BufferedImage renderAppearance(AppearanceSpec ap, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        try {
            switch (ap.mode) {
                case StandardSignature:  renderStandard(g, ap, w, h);     break;
                case OneLiner:           renderOneLiner(g, ap, w, h);     break;
                case SignatureImage:     renderSigImage(g, ap, w, h);     break;
                case advanceSignature:   renderAdvance(g, ap, w, h);      break;
                case ColoredGraphic:     renderColoredGraphic(g, ap, w, h); break;
                case BackgroundImage:    renderBackground(g, ap, w, h);   break;
                default:                 renderStandard(g, ap, w, h);     break;
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    private void renderStandard(Graphics2D g, AppearanceSpec ap, int w, int h) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);

        Color textColor = ap.fontColor != null
            ? new Color(ap.fontColor.r, ap.fontColor.g, ap.fontColor.b) : Color.BLACK;

        // Dynamic font size scaled to box height
        int fontSize = ap.fontSize > 0 ? ap.fontSize : Math.max(6, Math.min(11, h / 4));
        g.setFont(new Font("Times New Roman", Font.ITALIC, fontSize));
        FontMetrics fm = g.getFontMetrics();
        int lineH = fm.getHeight();
        int margin = Math.max(2, (int)(h * 0.06f));

        if (ap.layer2Text != null && !ap.layer2Text.isEmpty()) {
            g.setColor(textColor);
            int y = margin + fm.getAscent();
            for (String raw : ap.layer2Text.split("\n")) {
                String line = raw.trim();
                if (line.isEmpty()) continue;
                if (y + fm.getDescent() > h) break;
                g.drawString(line, margin, y);
                y += lineH;
            }
        }
    }

    private void renderOneLiner(Graphics2D g, AppearanceSpec ap, int w, int h) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        int fontSize = ap.fontSize > 0 ? ap.fontSize : 10;
        g.setFont(new Font("Times New Roman", Font.ITALIC, fontSize));
        FontMetrics fm = g.getFontMetrics();
        String text = ap.oneLinerText != null ? ap.oneLinerText : "";
        g.drawString(text, 3, (h + fm.getAscent() - fm.getDescent()) / 2);
    }

    private void renderSigImage(Graphics2D g, AppearanceSpec ap, int w, int h) throws IOException {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        if (ap.imageBytes != null && ap.imageBytes.length > 0) {
            BufferedImage sigImg = ImageIO.read(new ByteArrayInputStream(ap.imageBytes));
            if (sigImg != null) {
                // Image on left half, text on right half
                int imgW = w / 2;
                g.drawImage(sigImg, 0, 0, imgW, h, null);
            }
        }
        // Text on right half
        g.setColor(Color.BLACK);
        g.setFont(new Font("Times New Roman", Font.ITALIC, 7));
        FontMetrics fm = g.getFontMetrics();
        int x = w / 2 + 2;
        int y = fm.getHeight();
        if (ap.layer2Text != null) {
            for (String line : ap.layer2Text.split("\n")) {
                if (y > h) break;
                g.drawString(line, x, y);
                y += fm.getHeight();
            }
        }
    }

    private void renderAdvance(Graphics2D g, AppearanceSpec ap, int w, int h) throws Exception {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);

        if (ap.advanceImageType == ImageType.SVG && ap.advanceSvgBytes != null) {
            // Render SVG via Batik
            String parser = XMLResourceDescriptor.getXMLParserClassName();
            SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
            org.w3c.dom.svg.SVGDocument svgDoc = (org.w3c.dom.svg.SVGDocument)
                    factory.createDocument(null, new ByteArrayInputStream(ap.advanceSvgBytes));
            org.apache.batik.bridge.UserAgent ua = new UserAgentAdapter();
            DocumentLoader loader = new DocumentLoader(ua);
            BridgeContext ctx = new BridgeContext(ua, loader);
            ctx.setDynamicState(BridgeContext.STATIC);
            GraphicsNode gvtRoot = new GVTBuilder().build(ctx, svgDoc);
            // Scale to fit left half
            int imgW = w / 2;
            double scaleX = imgW / gvtRoot.getBounds().getWidth();
            double scaleY = h / gvtRoot.getBounds().getHeight();
            double scale = Math.min(scaleX, scaleY);
            g.scale(scale, scale);
            gvtRoot.paint(g);
            g.scale(1.0 / scale, 1.0 / scale);
        } else if (ap.imageBytes != null && ap.imageBytes.length > 0) {
            BufferedImage sigImg = ImageIO.read(new ByteArrayInputStream(ap.imageBytes));
            if (sigImg != null) {
                g.drawImage(sigImg, 0, 0, w / 2, h, null);
            }
        }

        // Right side text
        g.setColor(Color.BLACK);
        g.setFont(new Font("Times New Roman", Font.ITALIC, 7));
        FontMetrics fm = g.getFontMetrics();
        int x = w / 2 + 2;
        int y = fm.getHeight();
        if (ap.leftSideText != null) {
            for (String line : ap.leftSideText.split("\n")) {
                if (y > h) break;
                g.drawString(line, x, y);
                y += fm.getHeight();
            }
        }
        if (ap.rightSideText != null) {
            y = fm.getHeight();
            int rx = w - 2;
            for (String line : ap.rightSideText.split("\n")) {
                if (y > h) break;
                int tw = fm.stringWidth(line);
                g.drawString(line, rx - tw, y);
                y += fm.getHeight();
            }
        }
    }

    private void renderColoredGraphic(Graphics2D g, AppearanceSpec ap, int w, int h) {
        // White background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);

        // Left colored bar
        int barW = Math.max(4, w / 12);
        int[] lb = ap.leftBorderRgb != null ? ap.leftBorderRgb : new int[]{222, 35, 2};
        g.setColor(new Color(lb[0], lb[1], lb[2]));
        g.fillRect(0, 0, barW, h);

        // Right colored bar
        int[] rb = ap.rightBorderRgb != null ? ap.rightBorderRgb : new int[]{148, 0, 211};
        g.setColor(new Color(rb[0], rb[1], rb[2]));
        g.fillRect(w - barW, 0, barW, h);

        // Text in center
        g.setColor(Color.BLACK);
        g.setFont(new Font("Times New Roman", Font.ITALIC, 8));
        FontMetrics fm = g.getFontMetrics();
        int lineH = fm.getHeight();
        int y = lineH;
        int cx = barW + 2;
        if (ap.layer2Text != null) {
            for (String line : ap.layer2Text.split("\n")) {
                if (y > h) break;
                g.drawString(line, cx, y);
                y += lineH;
            }
        }
    }

    private void renderBackground(Graphics2D g, AppearanceSpec ap, int w, int h) throws IOException {
        if (ap.imageBytes != null && ap.imageBytes.length > 0) {
            BufferedImage bg = ImageIO.read(new ByteArrayInputStream(ap.imageBytes));
            if (bg != null) {
                g.drawImage(bg, 0, 0, w, h, null);
            } else {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
            }
        } else {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
        }
        // Overlay text
        g.setColor(Color.BLACK);
        int fontSize = ap.fontSize > 0 ? ap.fontSize : 8;
        g.setFont(new Font("Times New Roman", Font.ITALIC, fontSize));
        FontMetrics fm = g.getFontMetrics();
        int y = fm.getHeight();
        if (ap.layer2Text != null) {
            for (String line : ap.layer2Text.split("\n")) {
                if (y > h) break;
                g.drawString(line, 3, y);
                y += fm.getHeight();
            }
        }
    }

    // -----------------------------------------------------------------------
    // MDP (certification) helper
    // -----------------------------------------------------------------------

    /**
     * Applies DocMDP (certification) to the document.
     * accessPermissions: 1 = no changes, 2 = form fill, 3 = annotations + form fill.
     * Must be called after addSignature() and before saveIncrementalForExternalSigning().
     */
    private void setMDPPermission(PDDocument doc, PDSignature sig, int accessPermissions) {
        try {
            org.apache.pdfbox.cos.COSDictionary transformParameters =
                    new org.apache.pdfbox.cos.COSDictionary();
            transformParameters.setItem(org.apache.pdfbox.cos.COSName.TYPE,
                    org.apache.pdfbox.cos.COSName.getPDFName("TransformParams"));
            transformParameters.setInt(org.apache.pdfbox.cos.COSName.P, accessPermissions);
            transformParameters.setName(org.apache.pdfbox.cos.COSName.V, "1.2");
            transformParameters.setBoolean(
                    org.apache.pdfbox.cos.COSName.getPDFName("Document"), true);

            org.apache.pdfbox.cos.COSDictionary referenceDict =
                    new org.apache.pdfbox.cos.COSDictionary();
            referenceDict.setItem(org.apache.pdfbox.cos.COSName.TYPE,
                    org.apache.pdfbox.cos.COSName.getPDFName("SigRef"));
            referenceDict.setItem(org.apache.pdfbox.cos.COSName.getPDFName("TransformMethod"),
                    org.apache.pdfbox.cos.COSName.getPDFName("DocMDP"));
            referenceDict.setItem(org.apache.pdfbox.cos.COSName.getPDFName("TransformParams"),
                    transformParameters);

            org.apache.pdfbox.cos.COSArray referenceArray =
                    new org.apache.pdfbox.cos.COSArray();
            referenceArray.add(referenceDict);
            sig.getCOSObject().setItem(
                    org.apache.pdfbox.cos.COSName.getPDFName("Reference"), referenceArray);

            org.apache.pdfbox.cos.COSDictionary permsDict =
                    new org.apache.pdfbox.cos.COSDictionary();
            permsDict.setItem(org.apache.pdfbox.cos.COSName.getPDFName("DocMDP"), sig);
            doc.getDocumentCatalog().getCOSObject().setItem(
                    org.apache.pdfbox.cos.COSName.PERMS, permsDict);
        } catch (Exception ignored) {}
    }

    // -----------------------------------------------------------------------
    // Border helper
    // -----------------------------------------------------------------------

    private void drawBorder(PDPageContentStream cs, float w, float h,
            com.emudhra.esign.pdf.model.BorderSpec border) throws IOException {
        if (border == null || border.colored) return;
        float bw = Math.max(border.lineWidth, 0.25f);
        com.emudhra.esign.pdf.model.PdfColor bc = border.color != null
            ? border.color : new com.emudhra.esign.pdf.model.PdfColor(255, 0, 0);
        cs.setStrokingColor(bc.r / 255f, bc.g / 255f, bc.b / 255f);
        cs.setLineWidth(bw);
        cs.addRect(bw / 2f, bw / 2f, w - bw, h - bw);
        cs.stroke();
    }

    // -----------------------------------------------------------------------
    // ByteRange parsing helpers
    // -----------------------------------------------------------------------

    /**
     * Scans the PDF bytes (as ASCII text) for "/ByteRange [a b c d ]" and returns
     * the four long values, or null if not found.
     */
    private long[] parseByteRange(byte[] pdf) {
        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        int idx = text.lastIndexOf("/ByteRange");
        if (idx < 0) return null;
        int bracketOpen = text.indexOf('[', idx);
        if (bracketOpen < 0) return null;
        int bracketClose = text.indexOf(']', bracketOpen);
        if (bracketClose < 0) return null;
        String[] parts = text.substring(bracketOpen + 1, bracketClose).trim().split("\\s+");
        if (parts.length < 4) return null;
        try {
            return new long[]{
                Long.parseLong(parts[0].trim()),
                Long.parseLong(parts[1].trim()),
                Long.parseLong(parts[2].trim()),
                Long.parseLong(parts[3].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extracts the bytes covered by /ByteRange into a single concatenated array.
     */
    private byte[] extractRangeBytes(byte[] pdf, long[] byteRange) {
        int offset0 = (int) byteRange[0];
        int len0    = (int) byteRange[1];
        int offset1 = (int) byteRange[2];
        int len1    = (int) byteRange[3];

        byte[] result = new byte[len0 + len1];
        System.arraycopy(pdf, offset0, result, 0,    len0);
        System.arraycopy(pdf, offset1, result, len0, len1);
        return result;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
