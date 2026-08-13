
import com.emudhra.esign.ContentSearch;
import com.emudhra.esign.EncryptedAadhaarConfig;
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
        String pdfPath    = "C:\\path\\to\\input.pdf";       // input PDF to sign
        String outputPath = "C:\\path\\to\\signed.pdf";      // signed PDF written here
        String tempFolder = "C:\\path\\to\\temp";            // must exist; holds the {txn}.sig file
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
        // Each case builds a fully-formed eSignInput directly; the rest of main() (prepare ->
        // sign -> append) is identical regardless of which mode you choose.
        String mode;
        if (args.length > 0) {
            mode = args[0].trim().toLowerCase();
        } else {
            System.out.println("Select mode: first | last | all | even | odd | specify | "
                    + "pagelevel | contentsearch | encryptedaadhaar");
            System.out.print("Mode [pagelevel]: ");
            Scanner scanner = new Scanner(System.in);
            String entered = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            mode = entered.isEmpty() ? "pagelevel" : entered.toLowerCase();
        }
        System.out.println("Test mode: " + mode);

        // Common appearance/signer fields are repeated in every case so each branch is a
        // complete, copy-pasteable example.
        // GOTCHA: prepareDocuments() rejects an EMPTY pageLevelCoordinates with ESS-120
        // "Unable to find content" BEFORE the named Coordinates value is resolved, so even
        // fixed-corner modes must pass a non-empty (dummy) pageLevelCoordinates string just
        // to clear that guard — the named Coordinates box still does the actual placement.
        eSignInput input;
        switch (mode) {
            case "first": // first page only, bottom-right corner
                input = eSignInputBuilder.init()
                        .setDocBase64(pdfBase64)
                        .setSignedBy("ABC N")
                        .setReason("Approval")
                        .setLocation("Bengaluru")
                        .setAppearanceType(eSign.AppearanceType.StandardSignature)
                        .setBorderRequired(true)
                        .setPageTobeSigned(eSign.PageTobeSigned.First)
                        .setCoordinates(eSign.Coordinates.BottomRight)
                        .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                        .build();
                break;
            case "last": // last page only, bottom-right corner
                input = eSignInputBuilder.init()
                        .setDocBase64(pdfBase64)
                        .setSignedBy("ABC N")
                        .setReason("Approval")
                        .setLocation("Bengaluru")
                        .setAppearanceType(eSign.AppearanceType.StandardSignature)
                        .setBorderRequired(true)
                        .setPageTobeSigned(eSign.PageTobeSigned.Last)
                        .setCoordinates(eSign.Coordinates.BottomRight)
                        .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                        .build();
                break;
            case "all": // every page, bottom-right corner
                input = eSignInputBuilder.init()
                        .setDocBase64(pdfBase64)
                        .setSignedBy("ABC N")
                        .setReason("Approval")
                        .setLocation("Bengaluru")
                        .setAppearanceType(eSign.AppearanceType.StandardSignature)
                        .setBorderRequired(true)
                        .setPageTobeSigned(eSign.PageTobeSigned.All)
                        .setCoordinates(eSign.Coordinates.BottomRight)
                        .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                        .build();
                break;
            case "even": // even pages (2,4,6...), bottom-right corner
                input = eSignInputBuilder.init()
                        .setDocBase64(pdfBase64)
                        .setSignedBy("ABC N")
                        .setReason("Approval")
                        .setLocation("Bengaluru")
                        .setAppearanceType(eSign.AppearanceType.StandardSignature)
                        .setBorderRequired(true)
                        .setPageTobeSigned(eSign.PageTobeSigned.Even)
                        .setCoordinates(eSign.Coordinates.BottomRight)
                        .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                        .build();
                break;
            case "odd": // odd pages (1,3,5...), bottom-right corner
                input = eSignInputBuilder.init()
                        .setDocBase64(pdfBase64)
                        .setSignedBy("ABC N")
                        .setReason("Approval")
                        .setLocation("Bengaluru")
                        .setAppearanceType(eSign.AppearanceType.StandardSignature)
                        .setBorderRequired(true)
                        .setPageTobeSigned(eSign.PageTobeSigned.Odd)
                        .setCoordinates(eSign.Coordinates.BottomRight)
                        .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                        .build();
                break;
            case "specify": // specific pages you list, bottom-right corner
                input = eSignInputBuilder.init()
                        .setDocBase64(pdfBase64)
                        .setSignedBy("ABC N")
                        .setReason("Approval")
                        .setLocation("Bengaluru")
                        .setAppearanceType(eSign.AppearanceType.StandardSignature)
                        .setBorderRequired(true)
                        .setPageTobeSigned(eSign.PageTobeSigned.Specify)
                        .setPageNumbers("1,3,5")                        // which pages to sign (1-based, comma-separated)
                        .setCoordinates(eSign.Coordinates.BottomRight)
                        .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                        .build();
                break;
            case "pagelevel": // exact x/y rectangles per page (full control, no named anchor)
                // Format: "page-llx,lly,urx,ury" joined by ';'. Page token may be shorthand:
                // f=first, s=second, l=last, sl=second-last, a=all. Values are PDF points,
                // origin bottom-left. Here: page 1 box (400,80)-(540,150); page 3 box (50,700)-(200,760).
                input = eSignInputBuilder.init()
                        .setDocBase64(pdfBase64)
                        .setSignedBy("ABC N")
                        .setReason("Approval")
                        .setLocation("Bengaluru")
                        .setAppearanceType(eSign.AppearanceType.StandardSignature)
                        .setBorderRequired(true)
                        .setPageTobeSigned(eSign.PageTobeSigned.PageLevel)
                        .setPageLevelCoordinates("1-400,80,540,150;3-50,700,200,760")
                        .build();
                break;
            case "contentsearch": // text-relative box anchored to a string found in the PDF
                // The search text MUST appear in the page's [findText sees] dump (run dumpPdfText()).
                // Position/offset decide where the box sits relative to the matched text;
                // PageLevel is the required page mode for content search.
                ContentSearch contentSearch = new ContentSearch();
                contentSearch.setSearchText("Annual Work"); // must appear in the PDF's [findText sees] dump
                contentSearch.setHeight(60);
                contentSearch.setWidth(120);
                contentSearch.setOffset("0|0");
                contentSearch.setPosition(ContentSearch.Position.OBR);
                input = eSignInputBuilder.init()
                        .setDocBase64(pdfBase64)
                        .setSignedBy("ABC N")
                        .setReason("Approval")
                        .setLocation("Bengaluru")
                        .setAppearanceType(eSign.AppearanceType.StandardSignature)
                        .setBorderRequired(true)
                        .setPageTobeSigned(eSign.PageTobeSigned.PageLevel)
                        .setContentSearch(contentSearch)
                        .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                        .build();
                break;
            case "encryptedaadhaar": // Aadhaar pre-filled at the eMudhra gateway (GATEWAY flow)
                // Completely different lifecycle from every case above: this one talks to the
                // eMudhra gateway instead of returning a hash for your own signer, so it runs
                // end to end in its own method and never reaches the prepare/sign/append path
                // below.
                runEncryptedAadhaarFlow(pdfBase64, tempFolder, transactionID);
                return;
            default:
                System.out.println("Unknown mode '" + mode + "'. Valid: first|last|all|even|odd|"
                        + "specify|pagelevel|contentsearch|encryptedaadhaar");
                return;
        }

        ArrayList<eSignInput> inputs = new ArrayList<>();
        inputs.add(input);

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
     * The Encrypted Aadhaar eSign flow, step by step.
     *
     * <p>This is a GATEWAY flow, not the vendor-agnostic one main() otherwise demonstrates.
     * The signer authenticates on eMudhra's page in between, so the lifecycle splits across
     * two separate HTTP requests: everything below is phase 1, and phase 2 is sketched at the
     * bottom because it can only run inside your callback endpoint.
     *
     * <p>What it buys you: the signer's Aadhaar number arrives pre-filled at the gateway
     * instead of being typed there. Sharing an Aadhaar number from an ASP to an ESP requires
     * UIDAI approval - do not enable this in production without it.
     *
     * <p>The flow is OFF by default and cannot fire by accident: it needs BOTH
     * setEncryptedAadhaarFlowEnabled(true) AND setEncryptedAadhaarConfig(...) on the input.
     */
    private static void runEncryptedAadhaarFlow(String pdfBase64, String tempFolder, String transactionID) throws Exception {

        // ---- Step 0: configuration ----
        // ASP credentials come from your eMudhra onboarding. The PFX signs the request XML that
        // travels inside the wrapper. Both gateway URLs are still required by the constructor
        // even though phase 1 of this flow never POSTs to either of them.
        String aspID       = "YOUR_ASP_ID";
        String eSignURL    = "https://authenticate.sandbox.emudhra.com/eSignV3";      // V3/PAN - unused here
        String eSignURLV2  = "https://authenticate.sandbox.emudhra.com/eSignV2";      // V2/Aadhaar
        String pfxPath     = "C:\\path\\to\\asp-signing.pfx";
        String pfxPassword = "changeit";
        String pfxAlias    = "asp-alias";
        int    signatureContents = 21000;

        String signerID    = "signer@example.com";                                      // your own reference for the signer
        String responseUrl = "https://your-app.example.com/ResponseEncryptedAadhaar";   // eMudhra POSTs the result here
        String redirectUrl = "https://your-app.example.com/done";                       // browser lands here afterwards

        // PII: never log this, never commit a real one.
        String aadhaarNumber = "123456789012";   // exactly 12 digits, no spaces or separators -> else ESS-130

        // The RSA public-key certificate whose PRIVATE half eMudhra's HSM holds. Get it from your
        // onboarding contact and confirm the fingerprint with them. The SDK is issuer-neutral: it
        // checks only that the file parses as X.509 and that the key is RSA - no chain, no issuer,
        // no revocation check, and an EXPIRED certificate is accepted with a log warning. So the
        // wrong certificate still produces a well-formed request that getGatewayParameter()
        // reports as SUCCESS; the failure surfaces only on the gateway page.
        //
        // MUST come from trusted server-side configuration. Never from an HTTP request parameter:
        // a path beginning with two separators (\\host\share, //host/share, \/host/...) is
        // rejected with ESS-131 because on Windows it would trigger an outbound SMB fetch.
        String cerFilePath = "C:\\path\\to\\aadhaar-encryption.cer";   // DER or PEM both parse

        // ---- Step 1: describe the Aadhaar number and the certificate to the SDK ----
        EncryptedAadhaarConfig aadhaarConfig = new EncryptedAadhaarConfig();
        aadhaarConfig.setAadhaarNumber(aadhaarNumber);
        aadhaarConfig.setCerFilePath(cerFilePath);
        // Alternative for request-driven scenarios, where cerFilePath would be unsafe:
        //     byte[] raw = Files.readAllBytes(new File(cerFilePath).toPath());
        //     aadhaarConfig.setCerBase64(Base64.getEncoder().encodeToString(raw));
        // That is Base64 of the raw FILE BYTES. Pasting PEM text straight in, with its
        // "-----BEGIN CERTIFICATE-----" header, is not valid Base64 and returns ESS-132.

        // ---- Step 2: build the input with the flow switched on ----
        // Both calls are required; either one alone leaves the standard gateway path untouched.
        eSignInput input = eSignInputBuilder.init()
                .setDocBase64(pdfBase64)
                .setDocInfo("Agreement")
                .setSignedBy("ABC N")
                .setReason("Approval")
                .setLocation("Bengaluru")
                .setAppearanceType(eSign.AppearanceType.StandardSignature)
                .setBorderRequired(true)
                .setPageTobeSigned(eSign.PageTobeSigned.Last)
                .setCoordinates(eSign.Coordinates.BottomRight)
                .setPageLevelCoordinates("a-425,100,545,160;") // dummy: clears the ESS-120 guard only
                .setEncryptedAadhaarFlowEnabled(true)
                .setEncryptedAadhaarConfig(aadhaarConfig)
                .build();

        // One document per Aadhaar transaction. The SDK does not enforce this - it will happily
        // build a wrapper for several - but the gateway is not expected to accept them.
        ArrayList<eSignInput> inputs = new ArrayList<>();
        inputs.add(input);

        // ---- Step 3: gateway-mode eSign object ----
        // Note this takes ASP credentials, unlike the credential-free constructor main() uses.
        eSign esignObj = new eSign(aspID, eSignURL, eSignURLV2, pfxPath, pfxPassword, pfxAlias, signatureContents);

        // ---- Step 4: phase 1 - pre-sign the PDF and build the encrypted wrapper ----
        // With the flow on, getGatewayParameter() does NOT call the gateway. It pre-signs the
        // PDF, RSA-encrypts the Aadhaar (RSA/ECB/PKCS1Padding, Base64) with your certificate,
        // and returns the URL-encoded <eSignXML> wrapper locally. V2/Aadhaar is the intended
        // combination.
        eSignServiceReturn prepared = esignObj.getGatewayParameter(
                inputs, signerID, transactionID, responseUrl, redirectUrl, tempFolder,
                eSign.eSignAPIVersion.V2, eSign.AuthMode.OTP);

        if (prepared.getStatus() != 1) {
            // ESS-130  Aadhaar number is not exactly 12 digits
            // ESS-131  no certificate configured, public key is not RSA, or cerFilePath is a
            //          UNC/network path
            // ESS-132  certificate could not be read, decoded or parsed (missing file, corrupt
            //          base64, malformed DER), or the encryption call itself failed
            // The returned message is deliberately vague - exception text and filesystem paths
            // are kept out of it on purpose. The real cause is in logs/eSign.log.
            System.out.println("getGatewayParameter failed: " + prepared.getErrorCode()
                    + " - " + prepared.getErrorMessage());
            return;
        }

        String gatewayParam = prepared.getGatewayParameter();   // URL-encoded <eSignXML> wrapper
        String tempFile     = prepared.getPreSignedTempFile();  // MUST survive until phase 2

        System.out.println("Transaction ID    : " + prepared.getTransactionID());
        System.out.println("Temp .sig file    : " + tempFile);
        System.out.println("Gateway param len : " + gatewayParam.length());

        // Verify WHICH certificate actually encrypted the Aadhaar. The SDK logs subject, issuer,
        // serial and SHA-256 on every call precisely so a substituted key is detectable. Pin any
        // monitoring on the SHA-256, not the DN - the DN is chosen by whoever issued the cert.
        // This record is written at WARNING level, so LogType.NoLog suppresses it entirely.
        System.out.println("Certificate used  : see logs/eSign.log -> "
                + "\"Encrypted Aadhaar flow: encrypting with certificate\"");

        // ---- Step 5: POST the wrapper to the gateway ----
        // Render this as a self-submitting form in the signer's browser. They authenticate with
        // Aadhaar OTP on eMudhra's page, with the Aadhaar field already filled in.
        System.out.println("<form method=\"POST\" action=\"https://authenticate.sandbox.emudhra.com/AadhaareSign.jsp\">");
        System.out.println("  <input type=\"hidden\" name=\"txnref\" value=\"" + gatewayParam + "\" />");
        System.out.println("  <input type=\"submit\" value=\"Sign with Aadhaar\" />");
        System.out.println("</form>");

        // ---- Step 6: phase 2 - inject the returned signature (A SEPARATE HTTP REQUEST) ----
        // eMudhra POSTs to your responseUrl with form parameters "txnref" and "XML". That handler
        // needs the tempFile path from step 4, so persist it across the two requests (session,
        // cache, or a DB row keyed by transaction ID). Phase 2 is identical to the ordinary
        // Aadhaar flow - the encrypted flow changes nothing here:
        //
        //     String responseXML = request.getParameter("XML");
        //     eSignServiceReturn signed = esignObj.getSigedDocument(responseXML, tempFile);
        //     if (signed.getStatus() == 1) {
        //         String signedPdfBase64 = signed.getReturnDocuments().get(0).getSignedDocument();
        //         Files.write(new File(outputPath).toPath(), Base64.getDecoder().decode(signedPdfBase64));
        //     }
        System.out.println("Phase 2 runs in your callback at " + responseUrl
                + " - see the commented block in " + Test.class.getSimpleName() + ".");
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
        return "MIIIiAYJKoZIhvcNAQcCoIIIeTCCCHUCAQExDTALBglghkgBZQMEAgEwCwYJKoZIhvcNAQcBoIIGMTCCBi0wggUVoAMCAQICBAGLXfswDQYJKoZIhvcNAQELBQAwgYExCzAJBgNVBAYTAklOMRgwFgYDVQQKEw9lTXVkaHJhIExpbWl0ZWQxHTAbBgNVBAsTFENlcnRpZnlpbmcgQXV0aG9yaXR5MTkwNwYDVQQDEzBlLU11ZGhyYSBTdWIgQ0EgZm9yIENsYXNzIDMgRG9jdW1lbnQgU2lnbmVyIDIwMjIwHhcNMjUwMjEwMDg0MDE2WhcNMjgwMjA5MDg0MDE2WjCB1DELMAkGA1UEBhMCSU4xGDAWBgNVBAoTD2VNdWRocmEgTGltaXRlZDEdMBsGA1UECxMUQ2VydGlmeWluZyBBdXRob3JpdHkxDzANBgNVBBETBjU2MDEwMzESMBAGA1UECBMJS2FybmF0YWthMRIwEAYDVQQJEwlCYW5nYWxvcmUxNjA0BgNVBDMTLU5vIDU2IFNhaSBBcmNhZGUgM3JkIEZsb29yIERldmFyYWJlZXNhbmFoYWxsaTEbMBkGA1UEAxMSRFMgZU11ZGhyYSB0ZXN0IDEzMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAx9JebIgjhegobwV50JL80XinWTTszOcXiCRtY3twD6bpsXIowC+M98SvUQeWdgPrh3Qbh54WV0e0IKiV2jTbdWXdAj3rNwoqcPclFTjfq0iDu7OiCJHZCqio/Bp/cLBqLQX5zcKtzxZLeQQRFlTs/eiMwzMoCAZ+w8UPAFx6DbG95K3aZ1841r6nhSHm9zgmMiz9ITl0FUZx7bCb09LUZHtvXqCwMB6bnYy18CFlXcSSKP9Mctt/H7Id0jamJZ1Vzb3MCdg9ub2L2ZaWHJS9D++eTL55oUYgumfzELAV59MBAvdP0GNnVemW5SXt0Kx0ew0WREQYYeflQ9K7mctP5QIDAQABo4ICVjCCAlIwHwYDVR0jBBgwFoAUHnwkYEfYJo4vEbu/aNFVpZTMfygwHQYDVR0OBBYEFELltJ6dUmWlA0vuPk7w/etA3hHPMAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgbAMB8GA1UdEQQYMBaBFHRlc3Rkc2NAZS1tdWRocmEuY29tMDQGA1UdJQQtMCsGCCsGAQUFBwMEBgorBgEEAYI3CgMMBgkqhkiG9y8BAQUGCCsGAQUFBwMCMIHSBgNVHSAEgcowgccwLQYGYIJkZAIDMCMwIQYIKwYBBQUHAgIwFRoTQ2xhc3MgMyBDZXJ0aWZpY2F0ZTBEBgZggmRkCgEwOjA4BggrBgEFBQcCAjAsGipPcmdhbmlzYXRpb25hbCBEb2N1bWVudCBTaWduZXIgQ2VydGlmaWNhdGUwUAYHYIJkZAEIAjBFMEMGCCsGAQUFBwIBFjdodHRwOi8vd3d3LmUtbXVkaHJhLmNvbS9yZXBvc2l0b3J5L2Nwcy9lLU11ZGhyYV9DUFMucGRmMHwGCCsGAQUFBwEBBHAwbjAkBggrBgEFBQcwAYYYaHR0cDovL29jc3AuZS1tdWRocmEuY29tMEYGCCsGAQUFBzAChjpodHRwOi8vd3d3LmUtbXVkaHJhLmNvbS9yZXBvc2l0b3J5L2NhY2VydHMvZW1jbDNkczIwMjIuY3J0MEgGA1UdHwRBMD8wPaA7oDmGN2h0dHA6Ly93d3cuZS1tdWRocmEuY29tL3JlcG9zaXRvcnkvY3Jscy9lbWNsM2RzMjAyMi5jcmwwDQYJKoZIhvcNAQELBQADggEBAESgxSdfI/ac+uRYKkPrS7r80Xku3x2xCz0Ljz2rnok5suowZ9NmFJPoau9dg4mK6Cs+YfTWlEU7FNweqc85t5X7pdb8BLS+mKzs3gPQpBp3dnw2EWjAXNqIZfulhb+ppcrErSFjq9DA3+wtsS89lo43StjKwf2lDQH66pkQBO7OQh6Prbt2D7YxcHf6s/OFvNantZlZ/r4yxvkGio871xhqrq2/HO6aSclUl2zPb+uu/+CPfFGd6ImLRJ3P4b+/53su9ttAT0R1naUVripRPLW2WzuGwcBMhPkMDaQv2W5P+AqOTlkQbyIjL50m2NA5UbRBdvMQUtyhjEKJz+s2Z+ExggIdMIICGQIBATCBijCBgTELMAkGA1UEBhMCSU4xGDAWBgNVBAoTD2VNdWRocmEgTGltaXRlZDEdMBsGA1UECxMUQ2VydGlmeWluZyBBdXRob3JpdHkxOTA3BgNVBAMTMGUtTXVkaHJhIFN1YiBDQSBmb3IgQ2xhc3MgMyBEb2N1bWVudCBTaWduZXIgMjAyMgIEAYtd+zALBglghkgBZQMEAgGgaTAYBgkqhkiG9w0BCQMxCwYJKoZIhvcNAQcBMBwGCSqGSIb3DQEJBTEPFw0yNjA2MjYwNzI0MzlaMC8GCSqGSIb3DQEJBDEiBCCSpweZ4Z2HNP7PuOWaqTDp84b3ZFBZofLwTrhIPJtfHTALBgkqhkiG9w0BAQEEggEAWb9fvwjmlc4oyc5GSPI1usE4quCEb7DJ44qb8tyFhwUVBi/ocKq75JRSHtiv+sFscWi77++3ofIWEn2XXpcSmBf6rLSzU2dYVZVj1dqmSYo+ExbxPd/oFjUAKzbw28fkXFCEDmVVtqQgOTbNi7mokgQQicX8i0yMKIdvDmjttgOZA0g9CMfrqw45m+ykb122A5l0Umj0RdrdSNZ+hAWHhsBHm7+yID203lephsPlRkJr/95Z4L02yfBqidvjK20oDbWK5n1uw9dQhqetteCNP1C3JPEl6weUn6PqwI3GEhvqZ0RNCkHiAMSOF4dhL0OCD7FQOG5SpGU4dPePeysBRA==";  }
}
