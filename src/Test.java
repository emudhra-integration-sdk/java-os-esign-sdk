
import com.emudhra.esign.ContentSearch;
import com.emudhra.esign.ReturnDocument;
import com.emudhra.esign.eSign;
import com.emudhra.esign.eSignInput;
import com.emudhra.esign.eSignInputBuilder;
import com.emudhra.esign.eSignServiceReturn;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
public class Test {

    public static void main(String[] args) throws Exception {

        // ---- Configuration ----
        String pdfPath    = "C:\\Users\\21335\\Downloads\\SignedDoc.pdf";          // input PDF to sign
        String outputPath = "D:\\ITBA PROJ\\1.signed.pdf";   // signed PDF written here
        String tempFolder = "D:\\env\\eSign\\Temp";          // must exist; holds the {txn}.sig file
        int    signatureContents = 21000;                    // bytes reserved for the PKCS7; bump to 30000-40000 if injection fails
        String transactionID = "TXN-" + System.currentTimeMillis();

        // ---- Build the signing input ----
        byte[] pdfBytes  = Files.readAllBytes(new File(pdfPath).toPath());
        String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);

        // ---- DIAGNOSTIC: dump the extractable text so we know what to search for ----
        // Prints, per page, the human-readable text AND the raw glyph stream that the
        // SDK's findText() actually matches against (no spaces inserted between glyphs).
        // Pick your ContentSearch.setSearchText(...) value from the "findText sees" line.
        dumpPdfText(pdfBytes);

        // ---- Mode selector ----
        // Pick which placement family to exercise. If a CLI arg is given it wins; otherwise
        // prompt on the console. Blank input (just Enter) defaults to "pagelevel".
        // Every case below produces a fully-built eSignInput; the rest of main() (prepare ->
        // sign -> append) is identical regardless of which mode you choose.
        String mode;
        if (args.length > 0) {
            mode = args[0].trim().toLowerCase();
        } else {
            System.out.println("Select mode: first | last | all | even | odd | specify | "
                    + "pagelevel | contentsearch");
            System.out.print("Mode [pagelevel]: ");
            Scanner scanner = new Scanner(System.in);
            String entered = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            mode = entered.isEmpty() ? "pagelevel" : entered.toLowerCase();
        }
        System.out.println("Test mode: " + mode);
        
        eSignInput input;
        switch (mode) {
            case "first":        input = buildFirst(pdfBase64);        break; // first page only
            case "last":         input = buildLast(pdfBase64);         break; // last page only
            case "all":          input = buildAll(pdfBase64);          break; // every page
            case "even":         input = buildEven(pdfBase64);         break; // pages 2,4,6...
            case "odd":          input = buildOdd(pdfBase64);          break; // pages 1,3,5...
            case "specify":      input = buildSpecify(pdfBase64);      break; // pages you list
            case "pagelevel":    input = buildPageLevel(pdfBase64);    break; // exact x/y per page
            case "contentsearch":input = buildContentSearch(pdfBase64);break; // text-relative box
            default:
                System.out.println("Unknown mode '" + mode + "'. Valid: first|last|all|even|odd|"
                        + "specify|pagelevel|contentsearch");
                return;
        }

        ArrayList<eSignInput> inputs = new ArrayList<>();
        inputs.add(input);

//         // Content-search anchor for the inline input below (PageLevel + text-relative box).
//         ContentSearch contentSearch = new ContentSearch();
//         contentSearch.setSearchText("Annual Work"); // must appear in the PDF's [findText sees] dump
//         contentSearch.setHeight(60);
//         contentSearch.setWidth(120);
//         contentSearch.setOffset("0|0");
//         contentSearch.setPosition(ContentSearch.Position.OBR);

//         inputs.add(eSignInputBuilder.init()
//  .setDocBase64(pdfBase64)
//  .setDocInfo("Contract Agreement")
//  .setAppearanceType(eSign.AppearanceType.StandardSignature)
// .setPageTobeSigned(eSign.PageTobeSigned.PageLevel) // content search requires PageLevel
//  .setContentSearch(contentSearch)
// .setPageLevelCoordinates("a-425,100,545,160;")// dummy: clears the ESS-120 guard only
//  .build());


    //     inputs.add(eSignInputBuilder.init()
    // .setDocBase64(pdfBase64)
    // .setDocInfo("Contract Agreement")
    // .setAppearanceType(eSign.AppearanceType.StandardSignature)
    // .setPageTobeSigned(eSign.PageTobeSigned.Last)
    // .setCoordinates(eSign.Coordinates.BottomRight)
    //  .setPageLevelCoordinates("a-425,100,545,160;")
    // .build());

        // No gateway credentials required for vendor-agnostic mode
        eSign esignObj = new eSign(signatureContents);

        // ---- Step 1: prepare PDFs and get SHA-256 hashes ----
        eSignServiceReturn prepared = esignObj.prepareDocuments(inputs, transactionID, tempFolder);
        if (prepared.getStatus() != 1) {
            System.out.println("prepareDocuments failed: " + prepared.getErrorMessage());
            return;
        }

        String tempFile = prepared.getPreSignedTempFile();

        // ---- Step 2: sign each document hash with your own signing service ----
        ArrayList<String> pkcs7List = new ArrayList<>();
        for (ReturnDocument doc : prepared.getReturnDocuments()) {
            String hash = doc.getDocumentHash(); // 64-char hex SHA-256
            System.out.println("Hash to sign: " + hash);

            String pkcs7Base64 = signHashWithYourService(hash);
            pkcs7List.add(pkcs7Base64);
        }

        // ---- Step 3: inject the PKCS7 signatures back into the PDFs ----
        eSignServiceReturn signed = esignObj.appendSignatures(tempFile, pkcs7List);
        if (signed.getStatus() != 1) {
            System.out.println("appendSignatures failed: " + signed.getErrorMessage());
            return;
        }

        String signedPdfBase64 = signed.getReturnDocuments().get(0).getSignedDocument();
        Files.write(new File(outputPath).toPath(), Base64.getDecoder().decode(signedPdfBase64));
        System.out.println("Signed PDF written to: " + outputPath);
    }

    /**
     * Prints the extractable text of every page two ways:
     *   [readable] — PDFTextStripper output (with spaces) so you can eyeball the content.
     *   [findText sees] — every glyph's unicode concatenated with NO spaces, which is
     *                     exactly the string the SDK's findText() runs its regex against.
     * If a page prints empty here, the PDF has no text layer (scanned image) and content
     * search can never work on it.
     */
    private static void dumpPdfText(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int pages = doc.getNumberOfPages();
            System.out.println("==== PDF TEXT DUMP (" + pages + " page(s)) ====");
            for (int p = 1; p <= pages; p++) {
                final StringBuilder glyphs = new StringBuilder();
                PDFTextStripper stripper = new PDFTextStripper() {
                    @Override
                    protected void writeString(String text, List<TextPosition> textPositions) {
                        for (TextPosition tp : textPositions) glyphs.append(tp.getUnicode());
                    }
                };
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String readable = stripper.getText(doc).replaceAll("\\s+", " ").trim();

                System.out.println("--- page " + p + " ---");
                System.out.println("[readable]      " + readable);
                System.out.println("[findText sees] " + glyphs.toString().trim());
            }
            System.out.println("==== END DUMP ====");
        }
    }

    /**
     * TODO: Replace this stub with a call to your real signing service
     * (HSM / TSP / corporate CA). It must accept the 64-char hex SHA-256
     * hash and return a Base64-encoded PKCS7/CMS signature over that hash.
     */
    private static String signHashWithYourService(String hexSha256Hash) {
        return "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";  }

    // =====================================================================
    //  PageTobeSigned examples — one builder per mode.
    //  Swap any of these into main():  eSignInput input = buildFirst(pdfBase64);
    //
    //  Two placement families:
    //    * Fixed-corner modes (First/Last/All/Even/Odd/Specify): the mode picks
    //      the PAGES, setCoordinates(...) picks a named 120x60 anchor box that is
    //      reused on every chosen page (eSignImplimentation.java:313-326, 378-381).
    //    * PageLevel: you give an explicit "page-llx,lly,urx,ury" rectangle per
    //      page (eSignImplimentation.java:332-362).
    //
    //  GOTCHA baked into every fixed-corner helper below:
    //  prepareDocuments() rejects an EMPTY pageLevelCoordinates with ESS-120
    //  "Unable to find content" (eSignImplimentation.java:854) BEFORE the named
    //  Coordinates value is resolved. So even fixed-corner modes must pass a
    //  non-empty pageLevelCoordinates string just to clear that guard — the
    //  named Coordinates box still does the actual placement.
    // =====================================================================

    /** Common fields shared by every example (appearance, signer, border). */
    private static eSignInputBuilder baseBuilder(String pdfBase64) {
        return eSignInputBuilder.init()
                .setDocBase64(pdfBase64)
                .setSignedBy("ABC N")
                .setReason("Approval")
                .setLocation("Bengaluru")
                .setAppearanceType(eSign.AppearanceType.StandardSignature)
                .setBorderRequired(true);
    }

    /** First page only, bottom-right corner. */
    private static eSignInput buildFirst(String pdfBase64) {
        return baseBuilder(pdfBase64)
                .setPageTobeSigned(eSign.PageTobeSigned.First)
                .setCoordinates(eSign.Coordinates.BottomRight)
                .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                .build();
    }

    /** Last page only, bottom-right corner. */
    private static eSignInput buildLast(String pdfBase64) {
        return baseBuilder(pdfBase64)
                .setPageTobeSigned(eSign.PageTobeSigned.Last)
                .setCoordinates(eSign.Coordinates.BottomRight)
                .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                .build();
    }

    /** Every page, bottom-right corner. */
    private static eSignInput buildAll(String pdfBase64) {
        return baseBuilder(pdfBase64)
                .setPageTobeSigned(eSign.PageTobeSigned.All)
                .setCoordinates(eSign.Coordinates.BottomRight)
                .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                .build();
    }

    /** Even pages (2,4,6...), bottom-right corner. */
    private static eSignInput buildEven(String pdfBase64) {
        return baseBuilder(pdfBase64)
                .setPageTobeSigned(eSign.PageTobeSigned.Even)
                .setCoordinates(eSign.Coordinates.BottomRight)
                .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                .build();
    }

    /** Odd pages (1,3,5...), bottom-right corner. */
    private static eSignInput buildOdd(String pdfBase64) {
        return baseBuilder(pdfBase64)
                .setPageTobeSigned(eSign.PageTobeSigned.Odd)
                .setCoordinates(eSign.Coordinates.BottomRight)
                .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                .build();
    }

    /**
     * Specific pages you list, bottom-right corner.
     * Specify reads the page list from setPageNumbers (comma-separated, 1-based;
     * eSignImplimentation.java:300-306). Placement is still the named Coordinates box.
     */
    private static eSignInput buildSpecify(String pdfBase64) {
        return baseBuilder(pdfBase64)
                .setPageTobeSigned(eSign.PageTobeSigned.Specify)
                .setPageNumbers("1,3,5")                        // which pages to sign
                .setCoordinates(eSign.Coordinates.BottomRight)
                .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                .build();
    }

    /**
     * Text-relative placement: the box is anchored to a string found in the PDF.
     * The search text MUST appear in the page's [findText sees] dump (run dumpPdfText()).
     * Position/offset decide where the box sits relative to the matched text; PageLevel
     * is the required page mode for content search.
     */
    private static eSignInput buildContentSearch(String pdfBase64) {
        ContentSearch contentSearch = new ContentSearch();
        contentSearch.setSearchText("Annual Work"); // must appear in the PDF's [findText sees] dump
        contentSearch.setHeight(60);
        contentSearch.setWidth(120);
        contentSearch.setOffset("0|0");
        contentSearch.setPosition(ContentSearch.Position.OBR);
        return baseBuilder(pdfBase64)
                .setPageTobeSigned(eSign.PageTobeSigned.PageLevel)
                .setContentSearch(contentSearch)
                .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                .build();
    }

    /**
     * Exact x/y rectangles, one per page (full control — no named anchor used).
     * Format: "page-llx,lly,urx,ury" entries joined by ';'. Page token may also be
     * a shorthand: f=first, s=second, l=last, sl=second-last, a=all
     * (reformatPagelevelCoordinates, eSignImplimentation.java:1199-1217).
     * Values are PDF points, origin bottom-left.
     */
    private static eSignInput buildPageLevel(String pdfBase64) {
        return baseBuilder(pdfBase64)
                .setPageTobeSigned(eSign.PageTobeSigned.PageLevel)
                // page 1 box at (400,80)-(540,150); page 3 box at (50,700)-(200,760)
                .setPageLevelCoordinates("1-400,80,540,150;3-50,700,200,760")
                .build();
    }
}
