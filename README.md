# eSign Java SDK

A Java SDK for PDF digital signing. Supports two modes:

- **eMudhra Gateway** — Aadhaar-based (V2) and PAN-based (V3) eSign via eMudhra's gateway. PDFs are pre-signed locally (SHA-256 hash computed), the user authenticates on eMudhra's portal (OTP/Fingerprint/IRIS/Face), and the returned PKCS7 signature is injected back into the PDF.
- **Vendor-Agnostic** — Use `prepareDocuments()` to get the SHA-256 hash, sign it with any HSM, TSP, or corporate CA, then inject the resulting PKCS7 back using `appendSignatures()`. No dependency on eMudhra's gateway.

## Features

- **Aadhaar Signing (V2 API)** — OTP, Fingerprint, IRIS, and Face authentication
- **PAN Signing (V3 API)** — Username, Mobile, or PAN-based authentication
- **Vendor-Agnostic Signing** — `prepareDocuments()` + `appendSignatures()` lets you use any HSM, TSP, or corporate CA instead of eMudhra's gateway
- **Multiple Signature Appearances** — Standard, Image, OneLiner, Advanced, ColoredGraphic, BackgroundImage
- **Multi-Document Signing** — Sign up to 5 documents in a single request
- **Hash-Based Signing** — Sign using pre-computed SHA-256 hashes without sending the full PDF
- **Flexible Placement** — Named coordinates, page-level coordinates, or content-search-based positioning
- **Co-Signing Support** — Add multiple signatures to the same document
- **Signature Appearance Patching** — Automatically updates the visual appearance of signed signature fields with the signer's name and masked Aadhaar number extracted from the gateway-returned certificate
- **Bank KYC** — Perform Bank KYC verification through eMudhra
- **Configurable Logging** — File-based logging with rotation and multiple log levels
- **Proxy Support** — HTTP proxy with optional authentication

## Prerequisites

**For all modes:**
- Java 11 or higher
- All dependency JARs from the `lib/` folder (see [Dependencies](#dependencies))

**Additional requirements for eMudhra Gateway mode:**
- PFX certificate file (.pfx) provided by eMudhra for XML signing
- ASP ID (Application Service Provider ID) from eMudhra
- eSign gateway URLs (v1 and v2 endpoints) from eMudhra

**For Vendor-Agnostic mode:**
- Your own signing service (HSM, TSP, or corporate CA) that accepts a SHA-256 hash and returns a PKCS7/CMS signature
- No ASP ID, gateway URLs, or PFX certificate required

## Quick Start

### Option 1 — eMudhra Gateway Signing (Aadhaar / PAN)

The gateway flow runs in two phases. Phase 1 runs when the user initiates signing; Phase 2 runs in your callback handler after eMudhra redirects the user back.

```java
import com.emudhra.esign.*;
import java.util.ArrayList;

eSign esignObj = new eSign(
    "YOUR_ASP_ID",
    "https://esigngateway.emudhra.com/eSignRequest",
    "https://esigngateway.emudhra.com/v2/eSignRequest",
    "/path/to/certificate.pfx", "pfxPassword", "pfxAlias",
    21000  // reserved bytes for the PKCS7 signature in the PDF
);

ArrayList<eSignInput> inputs = new ArrayList<>();
inputs.add(eSignInputBuilder.init()
    .setDocBase64(pdfBase64)
    .setDocInfo("Contract Agreement")
    .setDocURL("https://yourapp.com/doc.pdf")
    .setSignedBy("John Doe")
    .setLocation("Bangalore")
    .setReason("Agreement Signing")
    .setAppearanceType(eSign.AppearanceType.StandardSignature)
    .setPageTobeSigned(eSign.PageTobeSigned.Last)
    .setCoordinates(eSign.Coordinates.BottomRight)
    .build());

// Phase 1: pre-sign locally and get the gateway redirect parameter
eSignServiceReturn result = esignObj.getGatewayParameter(
    inputs, "", "TXN-" + System.currentTimeMillis(),
    "https://yourapp.com/callback", "https://yourapp.com/redirect",
    "/tmp/esign", eSign.eSignAPIVersion.V2, eSign.AuthMode.OTP
);

if (result.getStatus() == 1) {
    String gatewayParam = result.getGatewayParameter();
    String tempFile = result.getPreSignedTempFile();
    // Redirect the user to eMudhra with gatewayParam; store tempFile for Phase 2
}

// Phase 2: called in your callback handler after eMudhra redirects back
eSignServiceReturn signResult = esignObj.getSigedDocument(eSignResponseXML, tempFile);
if (signResult.getStatus() == 1) {
    String signedPdfBase64 = signResult.getReturnDocuments().get(0).getSignedDocument();
}
```

### Option 2 — Vendor-Agnostic Signing (any HSM / TSP / corporate CA)

No ASP ID, gateway URLs, or PFX certificate needed. The SDK handles PDF pre-processing and PKCS7 injection; you supply the signature from your own signing service.

```java
import com.emudhra.esign.*;
import java.util.ArrayList;

// No gateway credentials required
eSign esignObj = new eSign(21000);

ArrayList<eSignInput> inputs = new ArrayList<>();
inputs.add(eSignInputBuilder.init()
    .setDocBase64(pdfBase64)
    .setDocInfo("Contract Agreement")
    .setAppearanceType(eSign.AppearanceType.StandardSignature)
    .setPageTobeSigned(eSign.PageTobeSigned.Last)
    .setCoordinates(eSign.Coordinates.BottomRight)
    .build());

// Step 1: prepare PDFs and get SHA-256 hashes
eSignServiceReturn prepared = esignObj.prepareDocuments(
    inputs, "TXN-" + System.currentTimeMillis(), "/tmp/esign"
);

if (prepared.getStatus() == 1) {
    String tempFile = prepared.getPreSignedTempFile();
    String hash = prepared.getReturnDocuments().get(0).getDocumentHash(); // 64-char hex SHA-256

    // Step 2: send hash to your signing service and receive PKCS7
    String pkcs7Base64 = yourSigningService.sign(hash);

    // Step 3: inject PKCS7 into the pre-signed PDF
    ArrayList<String> pkcs7List = new ArrayList<>();
    pkcs7List.add(pkcs7Base64);

    eSignServiceReturn signed = esignObj.appendSignatures(tempFile, pkcs7List);
    if (signed.getStatus() == 1) {
        String signedPdfBase64 = signed.getReturnDocuments().get(0).getSignedDocument();
    }
}
```

See the [full Quick Start guide](documentation/QUICK_START.md) for all signing flows, appearance options, and the complete API reference.

## Documentation

| Guide | Description |
|-------|-------------|
| [Quick Start](documentation/QUICK_START.md) | SDK overview, signing flows, API reference, enums, and error codes |
| [Framework Integration](documentation/FRAMEWORK_INTEGRATION.md) | Ready-to-use examples for Spring Boot, Servlet, JSP, Struts, and plain Java |
| [Logging Configuration](documentation/LOGGING_USAGE.md) | Log levels, file location, rotation, and configuration examples |

## Building from Source

This is an Apache Ant project targeting Java 11.

```bash
# Clean and build the JAR
ant clean jar

# Compile only
ant compile

# Clean build artifacts
ant clean
```

Output JAR: `dist/eSignASPLibrary5_10.jar`

## Dependencies

The SDK requires the following libraries (included in `lib/`):

| Library | Version | License |
|---------|---------|---------|
| [Apache Batik](https://xmlgraphics.apache.org/batik/) | 1.13 | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Apache Commons IO](https://commons.apache.org/proper/commons-io/) | 2.4 | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Apache Log4j API](https://logging.apache.org/log4j/2.x/) | 2.20.0 | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Apache XML Security](https://santuario.apache.org/) | 2.3.0 | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Apache XMLGraphics Commons](https://xmlgraphics.apache.org/commons/) | 2.4 | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Bouncy Castle Provider](https://www.bouncycastle.org/) | 1.70 | [MIT](https://opensource.org/licenses/MIT) |
| [Bouncy Castle PKIX](https://www.bouncycastle.org/) | 1.70 | [MIT](https://opensource.org/licenses/MIT) |
| [Woodstox](https://github.com/FasterXML/woodstox) | 5.2.1 | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Stax2 API](https://github.com/FasterXML/stax2-api) | 4.2 | [BSD 2-Clause](https://opensource.org/licenses/BSD-2-Clause) |
| [SLF4J](https://www.slf4j.org/) | 1.7.32 | [MIT](https://opensource.org/licenses/MIT) |
| [W3C SVG DOM](https://www.w3.org/Graphics/SVG/) | 1.1.0 | [W3C License](https://www.w3.org/Consortium/Legal/2015/copyright-software-and-document) |

The SDK also embeds the following libraries as vendored source:

| Library | Vendored As | License |
|---------|-------------|---------|
| [Apache PDFBox 3.x](https://pdfbox.apache.org/) | `org.apache.pdfbox.*` | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Apache FontBox 3.x](https://pdfbox.apache.org/) | `org.apache.fontbox.*` | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Bouncy Castle](https://www.bouncycastle.org/) | `org.emcastle.*` | [MIT](https://opensource.org/licenses/MIT) |

See [NOTICE](NOTICE) for full third-party attribution details.

## Contributing

Contributions are welcome. Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'Add your feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a Pull Request

Please ensure your changes compile with `ant clean jar` before submitting.

## License

This project is licensed under the **Apache License, Version 2.0** — see the [LICENSE](LICENSE) file for details.

### Third-Party Licenses

This project includes third-party libraries under various open-source licenses. See [NOTICE](NOTICE) for complete attribution and license details.
