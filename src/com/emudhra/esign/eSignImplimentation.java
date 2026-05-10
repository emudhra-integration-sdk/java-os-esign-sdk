package com.emudhra.esign;

import static com.emudhra.esign.eSign.PageTobeSigned;
import com.emudhra.esign.Enums.ImageType;
import com.emudhra.esign.pdf.PdfEngine;
import com.emudhra.esign.pdf.model.AppearanceSpec;
import com.emudhra.esign.pdf.model.BorderSpec;
import com.emudhra.esign.pdf.model.PageTextMatch;
import com.emudhra.esign.pdf.model.PdfColor;
import com.emudhra.esign.pdf.model.PdfRect;
import com.emudhra.esign.pdf.model.PreSignResult;
import com.emudhra.esign.pdf.model.SignatureFieldSpec;
import com.emudhra.esign.pdf.model.SignatureMetadata;
import org.emcastle.asn1.x500.RDN;
import org.emcastle.asn1.x500.X500Name;
import org.emcastle.asn1.x500.style.BCStyle;
import org.emcastle.asn1.x500.style.IETFUtils;
import org.emcastle.asn1.x509.X509CertificateStructure;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.logging.Logger;
import javax.crypto.NoSuchPaddingException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.emcastle.jce.provider.emCastleProvider;
import org.emcastle.util.encoders.Base64;
import org.emcastle.util.encoders.Hex;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class eSignImplimentation {

    private final String pfxpath;
    private final String password;
    private final String pfxAlias;
    private final boolean proxyreq;
    private final String proxyIp;
    private final int proxyPort;
    private final PdfEngine pdfEngine = new PdfEngine();

    private final static Logger LOGGER = EsignLoggerFactory.getLogger(eSignImplimentation.class);

    protected eSignImplimentation(String pfxfile, String password, String pfxAlias, String proxyIp, int proxyPort, boolean proxyreq) {
        this.pfxpath = pfxfile;
        this.password = password;
        this.proxyreq = proxyreq;
        this.proxyIp = proxyIp;
        this.proxyPort = proxyPort;
        this.pfxAlias = pfxAlias;
    }

    protected eSignServiceReturn getEncryptedPath(String path) {
        eSignServiceReturn serviceReturnObj = new eSignServiceReturn();
        try {
            serviceReturnObj.setEnCryptedPath(EncryptionHelper.getEncryptedData(path, eSignSettings.getEncryptionKey()));
            serviceReturnObj.setStatus(1);
            return serviceReturnObj;
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException | NoSuchPaddingException ex) {
            serviceReturnObj.setErrorCode("ESS-999");
            serviceReturnObj.setStatus(0);
            serviceReturnObj.setErrorMessage(ex.getMessage());
            return serviceReturnObj;
        }
    }

    @Deprecated
    protected eSignServiceReturn getGatewayParameter(ArrayList<eSignInput> inputs, String signerID, String transactionID, String responseUrl, String redirectUrl, String tempFolder, int SignatureContents) {
        return getGatewayParameterPrivate(inputs, signerID, transactionID, responseUrl, redirectUrl, tempFolder, eSign.eSignAPIVersion.V3, eSign.AuthMode.OTP, 1440, true, SignatureContents);
    }

    protected eSignServiceReturn getGatewayParameter(ArrayList<eSignInput> inputs, String signerID, String transactionID, String responseUrl, String redirectUrl, String tempFolder, eSign.eSignAPIVersion esignType, eSign.AuthMode authMode, int maxWaitPeriodinMin, boolean isLTVRequired, int SignatureContents) {
        return getGatewayParameterPrivate(inputs, signerID, transactionID, responseUrl, redirectUrl, tempFolder, esignType, authMode, maxWaitPeriodinMin, isLTVRequired, SignatureContents);
    }

    private eSignServiceReturn getGatewayParameterPrivate(ArrayList<eSignInput> inputs, String signerID, String transactionID, String responseUrl, String redirectUrl, String tempFolder, eSign.eSignAPIVersion esignType, eSign.AuthMode authMode, int maxWaitPeriodinMin, boolean isLTVRequired, int SignatureContents) {
        eSignServiceReturn serviceReturnObj = new eSignServiceReturn();
        int contentEstimated = 21000;
        if (SignatureContents != 0) {
            contentEstimated = SignatureContents;
        }
        String maxWaitPeriod = "";
        try {
            if (inputs.size() > 5 || inputs.isEmpty()) {
                serviceReturnObj.setResponseXML("");
                serviceReturnObj.setTransactionID(transactionID);
                serviceReturnObj.setErrorCode("ESS-100");
                serviceReturnObj.setStatus(0);
                serviceReturnObj.setErrorMessage("Minimum of 1 and Maximum of 5 Documents can be signed in a single request.");
                return serviceReturnObj;
            }
            if (maxWaitPeriodinMin < 1) {
                serviceReturnObj.setResponseXML("");
                serviceReturnObj.setTransactionID(transactionID);
                serviceReturnObj.setErrorCode("ESS-109");
                serviceReturnObj.setStatus(0);
                serviceReturnObj.setErrorMessage("Invalid value for max wait time period.");
                return serviceReturnObj;
            }
            maxWaitPeriod = Integer.toString(maxWaitPeriodinMin);

            if (eSignUtility.isNullOrWhitespace(signerID)) {
                signerID = "";
            }
            if (eSignUtility.isNullOrWhitespace(tempFolder)) {
                serviceReturnObj.setResponseXML("");
                serviceReturnObj.setTransactionID(transactionID);
                serviceReturnObj.setErrorCode("ESS-103");
                serviceReturnObj.setStatus(0);
                serviceReturnObj.setErrorMessage("temp folder path be empty");
                return serviceReturnObj;
            }
            if (transactionID.length() >= 50) {
                serviceReturnObj.setResponseXML("");
                serviceReturnObj.setTransactionID(transactionID);
                serviceReturnObj.setErrorCode("ESS-114");
                serviceReturnObj.setStatus(0);
                serviceReturnObj.setErrorMessage("transactionID should be less then 50 character.");
                return serviceReturnObj;
            }

            File dir = new File(tempFolder);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, 0);
            SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            TimeZone timeZone = TimeZone.getTimeZone("IST");
            tsFormat.setTimeZone(timeZone);
            Date now = new Date(System.currentTimeMillis());
            String timeStamp = tsFormat.format(now);

            if (eSignUtility.isNullOrWhitespace(transactionID)) {
                transactionID = UUID.randomUUID().toString().replace("-", "");
            }
            serviceReturnObj.setTransactionID(transactionID);
            String tempFilePath = tempFolder + File.separator + transactionID + ".sig";
            int count = 1;
            ArrayList<ReturnDocument> returnDocuments = new ArrayList<>();

            for (eSignInput input : inputs) {
                if (input.getInputType() == eSign.InputType.PDF) {
                    try {
                        String hexHashDocument = "";
                        String preSignedPdf = "";
                        String cordinate = "";

                        if (!input.getDocHash().isEmpty()) {
                            hexHashDocument = input.getDocHash();
                        } else {
                            PageTobeSigned Page = input.getPage();
                            String pagenumber = input.getPageNumbers();
                            byte[] decodePDF = Base64.decode(input.getDocBase64());

                            String pdfPwd = input.getPdfPassword();
                            int numPages = pdfEngine.getPageCount(decodePDF, pdfPwd);

                            // Content search: find text and compute placement coordinates
                            if (input.getContentSearch() != null) {
                                if (input.getContentSearch().getHeight() <= 0) {
                                    if (eSignUtility.isNullOrEmpty(cordinate)) {
                                        serviceReturnObj.setErrorCode("ESS-121");
                                        serviceReturnObj.setErrorMessage("Invalid height");
                                        return serviceReturnObj;
                                    }
                                }
                                if (input.getContentSearch().getWidth() <= 0) {
                                    serviceReturnObj.setErrorCode("ESS-121");
                                    serviceReturnObj.setErrorMessage("Invalid Width");
                                    return serviceReturnObj;
                                }
                                if (input.getContentSearch().getOffset() == "") {
                                    serviceReturnObj.setErrorCode("ESS-121");
                                    serviceReturnObj.setErrorMessage("Offset cannot be empty");
                                    return serviceReturnObj;
                                }
                                if (input.getContentSearch().getPosition() == null) {
                                    serviceReturnObj.setErrorCode("ESS-121");
                                    serviceReturnObj.setErrorMessage("Invalid Postion");
                                    return serviceReturnObj;
                                }
                                List<PageTextMatch> matches = pdfEngine.findText(decodePDF, input.getContentSearch().getSearchText(), pdfPwd);
                                cordinate = buildCoordinateString(matches,
                                        input.getContentSearch().getOffset(),
                                        input.getContentSearch().getHeight(),
                                        input.getContentSearch().getWidth(),
                                        input.getContentSearch().getPosition());
                                input.pageLevelCoordinates(cordinate);
                                if (eSignUtility.isNullOrEmpty(cordinate)) {
                                    serviceReturnObj.setErrorCode("ESS-120");
                                    serviceReturnObj.setErrorMessage("Unable to find content");
                                    return serviceReturnObj;
                                }
                            }

                            // Validate page-level coordinates
                            if (input.getContentSearch() != null) {
                                try {
                                    input.pageLevelCoordinates(eSignUtility.validatePageLevelCordinate(input.getPageLevelCoordinates(), true, pdfEngine, decodePDF));
                                } catch (Exception ex) {
                                    serviceReturnObj.setErrorCode("ESS-120");
                                    serviceReturnObj.setErrorMessage("Unable to find content");
                                    return serviceReturnObj;
                                }
                            } else {
                                try {
                                    String pageLevelCoordinates = input.getPageLevelCoordinates();
                                    if (Page.toString().equalsIgnoreCase("pagelevel")) {
                                        pageLevelCoordinates = reformatPagelevelCoordinates(pageLevelCoordinates, numPages);
                                        input.pageLevelCoordinates(eSignUtility.validatePageLevelCordinate(pageLevelCoordinates, false, pdfEngine, decodePDF));
                                    }
                                } catch (Exception ex) {
                                    serviceReturnObj.setErrorCode("RDSA-120");
                                    serviceReturnObj.setErrorMessage("Invalid Coordinate.");
                                    return serviceReturnObj;
                                }
                            }

                            if (eSignUtility.isNullOrEmpty(input.getPageLevelCoordinates())) {
                                serviceReturnObj.setErrorCode("ESS-120");
                                serviceReturnObj.setErrorMessage("Unable to find content");
                                return serviceReturnObj;
                            }

                            // Appearance-specific validation
                            if (null != input.getAppearanceType()) {
                                switch (input.getAppearanceType()) {
                                    case StandardSignature:
                                        if (input.getSignatureFontSize() < -1) {
                                            serviceReturnObj.setErrorCode("ESS-122");
                                            serviceReturnObj.setErrorMessage("Invalid font size");
                                            return serviceReturnObj;
                                        }
                                        break;
                                    case SignatureImage:
                                        if (input.getSignatureImage() == null) {
                                            serviceReturnObj.setErrorCode("ESS-126");
                                            serviceReturnObj.setErrorMessage("SignatureImage cannot be empty");
                                            return serviceReturnObj;
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            }

                            // Build pages array
                            int[] pages = null;
                            ArrayList<Integer> ar;
                            switch (Page) {
                                case First: {
                                    pages = new int[1];
                                    pages[0] = 1;
                                }
                                break;
                                case Last: {
                                    pages = new int[1];
                                    pages[0] = numPages;
                                }
                                break;
                                case Even: {
                                    ar = new ArrayList<>();
                                    for (int i = 2; i <= numPages; i = i + 2) ar.add(i);
                                    pages = new int[ar.size()];
                                    for (int j = 0; j < ar.size(); j++) pages[j] = ar.get(j);
                                }
                                break;
                                case Odd: {
                                    ar = new ArrayList<>();
                                    for (int i = 1; i <= numPages; i = i + 2) ar.add(i);
                                    pages = new int[ar.size()];
                                    for (int j = 0; j < ar.size(); j++) pages[j] = ar.get(j);
                                }
                                break;
                                case All: {
                                    ar = new ArrayList<>();
                                    pages = new int[numPages];
                                    for (int i = 0; i < numPages; i++) ar.add(i + 1);
                                    for (int j = 0; j < pages.length; j++) pages[j] = ar.get(j);
                                }
                                break;
                                case Specify:
                                    String[] Pagelevel = pagenumber.split(",");
                                    pages = new int[Pagelevel.length];
                                    for (int j = 0; j < Pagelevel.length; j++) {
                                        pages[j] = Integer.parseInt(Pagelevel[j]);
                                    }
                                    break;
                                default:
                                    break;
                            }

                            // Coordinate string for non-pagelevel modes
                            String coord = null;
                            if (!Page.toString().equalsIgnoreCase("pagelevel")) {
                                switch (input.getCoordinates()) {
                                    case TopLeft:    coord = "25,725,145,785";  break;
                                    case TopMiddle:  coord = "225,725,345,785"; break;
                                    case TopRight:   coord = "425,725,545,785"; break;
                                    case CenterLeft: coord = "25,425,145,485";  break;
                                    case CenterMiddle: coord = "225,425,345,485"; break;
                                    case CenterRight: coord = "425,425,545,485"; break;
                                    case BottomLeft: coord = "25,100,145,160";  break;
                                    case BottomMiddle: coord = "225,100,345,160"; break;
                                    case BottomRight: coord = "425,100,545,160"; break;
                                    default: coord = "exception in case"; break;
                                }
                            }

                            // Build rects and page list
                            List<PdfRect> rects = new ArrayList<>();
                            List<Integer> pageList = new ArrayList<>();

                            if (Page.toString().equalsIgnoreCase("pagelevel")) {
                                String pageLevelCoordinates = input.getPageLevelCoordinates();
                                pageLevelCoordinates = reformatPagelevelCoordinates(pageLevelCoordinates, numPages);
                                String[] pl = pageLevelCoordinates.split(";");
                                pages = new int[pl.length];
                                int y = 0;
                                for (String pl1 : pl) {
                                    if ("".equals(pl1.trim())) continue;
                                    if (!pl1.contains("-")) pl1 = y + "-" + pl1;
                                    String[] newpages = pl1.split("-");
                                    String[] numbers = newpages[1].split(",");
                                    float x11, y1, x2, y2;
                                    try {
                                        x11 = Float.valueOf(numbers[0]);
                                        y1  = Float.valueOf(numbers[1]);
                                        x2  = Float.valueOf(numbers[2]);
                                        y2  = Float.valueOf(numbers[3]);
                                        if (input.isRightOrigin()) {
                                            PdfRect pageRect = pdfEngine.getPageSize(decodePDF, Integer.parseInt(newpages[0]), pdfPwd);
                                            x11 = pageRect.getWidth() - Float.valueOf(numbers[2]);
                                            x2  = pageRect.getWidth() - Float.valueOf(numbers[0]);
                                        }
                                    } catch (NumberFormatException ex) {
                                        LOGGER.warning(ex.getLocalizedMessage());
                                        LOGGER.info("Entered into default coordinates - bottom,right");
                                        x11 = 425; y1 = 100; x2 = 555; y2 = 160;
                                    }
                                    pages[y] = Integer.parseInt(newpages[0]);
                                    rects.add(new PdfRect(x11, y1, x2, y2));
                                    pageList.add(pages[y]);
                                    y++;
                                }
                            } else {
                                String[] numbers1 = coord != null ? coord.split(",") : new String[]{"1"};
                                float x11, y11, x21, y21;
                                try {
                                    x11 = Float.valueOf(numbers1[0]);
                                    y11 = Float.valueOf(numbers1[1]);
                                    x21 = Float.valueOf(numbers1[2]);
                                    y21 = Float.valueOf(numbers1[3]);
                                } catch (NumberFormatException ex) {
                                    LOGGER.warning(ex.getLocalizedMessage());
                                    LOGGER.info("Entered into default coordinates - bottom,right");
                                    x11 = 425; y11 = 100; x21 = 555; y21 = 160;
                                }
                                PdfRect pdfRect = new PdfRect(x11, y11, x21, y21);
                                for (int pg : pages) {
                                    rects.add(pdfRect);
                                    pageList.add(pg);
                                }
                            }

                            // Build appearance, metadata, border and field spec
                            AppearanceSpec appearanceSpec = buildAppearanceSpec(input, timeStamp, cal, tsFormat, timeZone, now);
                            SignatureMetadata metadata = new SignatureMetadata(
                                    input.getReason(), input.getLocation(), input.getSignedBy(), "eMudhra", cal);
                            BorderSpec border = buildBorderSpec(input);
                            SignatureFieldSpec fieldSpec = new SignatureFieldSpec(
                                    rects, pageList, input.isCoSign(), appearanceSpec, metadata, border);

                            // Phase 1: prepare signature placeholder
                            PreSignResult preSignResult = pdfEngine.prepareSignature(decodePDF, fieldSpec, contentEstimated, pdfPwd);

                            // Compute SHA-256 hash over the signed byte ranges
                            Security.addProvider(new emCastleProvider());
                            MessageDigest digest = MessageDigest.getInstance("SHA256", "EM");
                            digest.update(preSignResult.bytesToHash);
                            byte[] hash = digest.digest();
                            String hashData = new String(Base64.encode(hash));
                            byte[] hashdata = Base64.decode(hashData);
                            hexHashDocument = Hex.toHexString(hashdata);

                            // Serialize PreSignResult to the wire format: position|bufferSize|base64pdf
                            String preSignedBytes = new String(Base64.encode(preSignResult.preSignedPdfBytes), "UTF-8");
                            preSignedPdf = preSignResult.placeholderPosition + "|" + preSignResult.outputBufferSize + "|" + preSignedBytes;
                            preSignedPdf = org.emcastle.util.encoders.Base64.toBase64String(preSignedPdf.getBytes("utf-8"));
                        }

                        ReturnDocument returnDocument = new ReturnDocument("", count, input.getDocInfo(), input.getDocURL(), hexHashDocument, preSignedPdf, eSign.InputType.PDF, input.isPatchSignatureAppearance());
                        returnDocuments.add(returnDocument);
                        count++;
                    } catch (Exception e) {
                        LOGGER.warning("" + e);
                        ReturnDocument returnDocument = new ReturnDocument(0, "Unable to generate appreance - " + e.getMessage(), "ESS-108", 0);
                        returnDocuments.add(returnDocument);
                    }
                } else if (input.getInputType() == eSign.InputType.HASH) {
                    if (input.getDocHash().matches("^[a-fA-F0-9]{64}$")) {
                        ReturnDocument returnDocument = new ReturnDocument("", count, input.getDocInfo(), input.getDocURL(), input.getDocHash(), "", eSign.InputType.HASH);
                        returnDocuments.add(returnDocument);
                        count++;
                    } else {
                        ReturnDocument returnDocument = new ReturnDocument(0, "Only SHA-256 hash is allowed ", "ESS-108", 0);
                        returnDocuments.add(returnDocument);
                        count++;
                    }
                }
            }

            if (!eSignUtility.allDocumentHaveError(returnDocuments)) {
                serviceReturnObj.setTransactionID(transactionID);
                serviceReturnObj.setErrorCode("ESS-108");
                serviceReturnObj.setStatus(0);
                serviceReturnObj.setReturnValues(returnDocuments);
                serviceReturnObj.setErrorMessage("Unable to generate appreance");
                return serviceReturnObj;
            }
            String tempData = eSignUtility.generateTempTransactionData(returnDocuments);
            try (PrintWriter writer = new PrintWriter(new File(tempFilePath))) {
                writer.print(tempData);
            }
            serviceReturnObj.setPreSignedTempFile(tempFilePath);

            String requestXML = "";
            if (esignType == eSign.eSignAPIVersion.V2) {
                requestXML = eSignUtility.generateRequestXMLV2(returnDocuments, eSignSettings.getASPID(), responseUrl, redirectUrl, transactionID, timeStamp, authMode, isLTVRequired);
            } else {
                requestXML = eSignUtility.generateRequestXML(returnDocuments, signerID, eSignSettings.getASPID(), responseUrl, redirectUrl, transactionID, timeStamp, maxWaitPeriod, isLTVRequired);
            }
            String signedRequestXML = eSignUtility.signXMLAndroid(requestXML, pfxpath, password, pfxAlias);
            String URLEncodedsignedRequestXML = URLEncoder.encode(signedRequestXML, "UTF-8");
            serviceReturnObj.setRequestXML(signedRequestXML);
            String responseXML = "";
            try {
                String url = (esignType == eSign.eSignAPIVersion.V2) ? eSignSettings.getESIGNURLV2() : eSignSettings.getESIGNURL();
                responseXML = HttpsConnection_weblogic.excutePostHttpsXml(url, URLEncodedsignedRequestXML, proxyIp, proxyPort, proxyreq, transactionID);
            } catch (Exception e) {
                serviceReturnObj.setPreSignedTempFile(tempFilePath);
                serviceReturnObj.setRequestXML(signedRequestXML);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setTransactionID(transactionID);
                serviceReturnObj.setErrorCode("ESS-103");
                serviceReturnObj.setStatus(0);
                serviceReturnObj.setErrorMessage("Unable to call eSign Url" + e.getMessage());
                return serviceReturnObj;
            }
            if (responseXML.isEmpty()) {
                serviceReturnObj.setPreSignedTempFile(tempFilePath);
                serviceReturnObj.setRequestXML(signedRequestXML);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setTransactionID(transactionID);
                serviceReturnObj.setErrorCode("ESS-104");
                serviceReturnObj.setStatus(0);
                serviceReturnObj.setErrorMessage("empty response from eSign Url");
                return serviceReturnObj;
            }
            Document doc = eSignUtility.convertStringToDocument(responseXML);
            if (doc == null) {
                serviceReturnObj.setPreSignedTempFile(tempFilePath);
                serviceReturnObj.setRequestXML(signedRequestXML);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setTransactionID(transactionID);
                serviceReturnObj.setErrorCode("ESS-104");
                serviceReturnObj.setErrorMessage("Unable to Parse response XMl document");
                serviceReturnObj.setStatus(0);
                return serviceReturnObj;
            }
            XPath xPath = XPathFactory.newInstance().newXPath();
            String status = eSignUtility.GetXpathValue(xPath, "/EsignResp/@status", doc);
            ArrayList<ReturnDocument> docsToReturn = new ArrayList<>();
            if (status.equals("0")) {
                String errormessage = eSignUtility.GetXpathValue(xPath, (esignType == eSign.eSignAPIVersion.V2) ? "/EsignResp/@errMsg" : "/EsignResp/@errorMessage", doc);
                String errorCode = eSignUtility.GetXpathValue(xPath, "/EsignResp/@errorCode", doc);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setPreSignedTempFile(tempFilePath);
                serviceReturnObj.setTransactionID(transactionID);
                serviceReturnObj.setErrorCode(errorCode);
                serviceReturnObj.setErrorMessage(errormessage);
                serviceReturnObj.setStatus(0);
                return serviceReturnObj;
            } else if (status.equals("2")) {
                String responseCode = eSignUtility.GetXpathValue(xPath, "/EsignResp/@resCode", doc);
                String gateWayParamter = transactionID + "|" + responseCode;
                gateWayParamter = org.emcastle.util.encoders.Base64.toBase64String(gateWayParamter.getBytes("utf-8"));
                serviceReturnObj.setRequestXML(signedRequestXML);
                serviceReturnObj.setPreSignedTempFile(tempFilePath);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setTransactionID(transactionID);
                serviceReturnObj.setResponseCode(responseCode);
                serviceReturnObj.setReturnValues(returnDocuments);
                serviceReturnObj.setGatewayParameter(gateWayParamter);
                serviceReturnObj.setStatus(1);
                return serviceReturnObj;
            }
            serviceReturnObj.setRequestXML(signedRequestXML);
            serviceReturnObj.setResponseXML(responseXML);
            serviceReturnObj.setTransactionID(transactionID);
            serviceReturnObj.setStatus(0);
            serviceReturnObj.setReturnValues(docsToReturn);
            return serviceReturnObj;
        } catch (Exception e) {
            serviceReturnObj.setTransactionID(transactionID);
            serviceReturnObj.setErrorCode("ESS-999");
            serviceReturnObj.setStatus(0);
            serviceReturnObj.setErrorMessage(e.getMessage());
            return serviceReturnObj;
        }
    }

    protected eSignServiceReturn getSigedDocument(String responseXML, String tempFilePath, int SignatureContents) {
        eSignServiceReturn serviceReturnObj = new eSignServiceReturn();
        try {
            if (responseXML.isEmpty()) {
                serviceReturnObj.setPreSignedTempFile(tempFilePath);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setErrorCode("ESS-104");
                serviceReturnObj.setStatus(0);
                serviceReturnObj.setErrorMessage("empty response xml");
                return serviceReturnObj;
            }
            Document doc = eSignUtility.convertStringToDocument(responseXML);
            if (doc == null) {
                serviceReturnObj.setPreSignedTempFile(tempFilePath);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setErrorCode("ESS-104");
                serviceReturnObj.setErrorMessage("Unable to Parse response XMl document");
                serviceReturnObj.setStatus(0);
                return serviceReturnObj;
            }

            File tempfile = new File(tempFilePath);
            if (!tempfile.exists()) {
                serviceReturnObj.setPreSignedTempFile(tempFilePath);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setErrorCode("ESS-108");
                serviceReturnObj.setErrorMessage("TempFile does not exist in path");
                serviceReturnObj.setStatus(0);
                return serviceReturnObj;
            }
            byte[] preSignedBytes = null;
            try {
                preSignedBytes = Files.readAllBytes(tempfile.toPath());
            } catch (Exception e) {
                serviceReturnObj.setPreSignedTempFile(tempFilePath);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setErrorCode("ESS-108");
                serviceReturnObj.setErrorMessage("Unable to read temp File");
                serviceReturnObj.setStatus(0);
                return serviceReturnObj;
            }
            XPath xPath = XPathFactory.newInstance().newXPath();
            String status = eSignUtility.GetXpathValue(xPath, "/EsignResp/@status", doc);
            String transactionID = eSignUtility.GetXpathValue(xPath, "/EsignResp/@txn", doc);
            serviceReturnObj.setTransactionID(transactionID);
            ArrayList<ReturnDocument> docsToReturn = new ArrayList<>();
            if (status.equals("0")) {
                String errormessage = eSignUtility.GetXpathValue(xPath, "/EsignResp/@errorMessage", doc);
                String errorCode = eSignUtility.GetXpathValue(xPath, "/EsignResp/@errorCode", doc);
                transactionID = eSignUtility.GetXpathValue(xPath, "/EsignResp/@txn", doc);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setPreSignedTempFile(tempFilePath);
                serviceReturnObj.setTransactionID(transactionID);
                serviceReturnObj.setErrorCode(errorCode);
                serviceReturnObj.setErrorMessage(errormessage);
                serviceReturnObj.setStatus(0);
                return serviceReturnObj;
            } else if (status.equals("1")) {
                String responseCode = eSignUtility.GetXpathValue(xPath, "/EsignResp/@resCode", doc);
                serviceReturnObj.setResponseCode(responseCode);
                ArrayList<ReturnDocument> returnDocuments = eSignUtility.getReturnDocumentsFromPreSignedPDFFile(preSignedBytes);
                NodeList signatureNodes = doc.getElementsByTagName("DocSignature");
                if (signatureNodes.getLength() <= 0) {
                    serviceReturnObj.setResponseXML(responseXML);
                    serviceReturnObj.setTransactionID(transactionID);
                    serviceReturnObj.setErrorCode("ESS-105");
                    serviceReturnObj.setErrorMessage("Signature element not found");
                    serviceReturnObj.setStatus(0);
                    return serviceReturnObj;
                }
                NodeList tempNodeList = doc.getElementsByTagName("Signatures");
                if (tempNodeList.getLength() <= 0) {
                    throw new IllegalArgumentException("No document signatures found in response xml");
                }
                if (tempNodeList.item(0) == null) {
                    throw new IllegalArgumentException("No document signatures found in response xml");
                }
                String userX509CertBase64 = "";
                NodeList certNodes = doc.getElementsByTagName("UserX509Certificate");
                if (certNodes.getLength() > 0 && certNodes.item(0) != null) {
                    userX509CertBase64 = eSignUtility.getCharacterDataFromElement((Element) certNodes.item(0));
                }

                NodeList docSignatureNodes = tempNodeList.item(0).getChildNodes();
                for (int itrCount = 0; itrCount < docSignatureNodes.getLength(); itrCount++) {
                    Node signatureNode = signatureNodes.item(itrCount);
                    if (signatureNode.getNodeType() != Node.ELEMENT_NODE) continue;
                    Element sigElement = (Element) signatureNode;
                    String docID = sigElement.getAttribute("id");
                    String errorCode = sigElement.getAttribute("errorCode");
                    String errorMessage = sigElement.getAttribute("errorMessage");
                    int docId = 0;
                    if (eSignUtility.tryParseInt(docID)) {
                        docId = Integer.parseInt(docID);
                    }
                    ReturnDocument returnDocument = eSignUtility.getReturnDocumentById(docId, returnDocuments);
                    if (returnDocument == null) {
                        docsToReturn.add(new ReturnDocument(0, "ESS-113", "Unable to get document from temp path", docId));
                        continue;
                    }
                    if (!eSignUtility.isNullOrWhitespace(returnDocument.getErrorMessage())) {
                        docsToReturn.add(returnDocument);
                        continue;
                    }
                    if (!(errorCode.isEmpty() && errorMessage.isEmpty())) {
                        returnDocument.setErrorMessage(errorMessage);
                        returnDocument.setErrorCode(errorCode);
                        returnDocument.setStatus(0);
                        docsToReturn.add(returnDocument);
                    } else {
                        String PKCS7ResponseBase64 = eSignUtility.getCharacterDataFromElement(sigElement);
                        try {
                            if (eSignUtility.isNullOrWhitespace(returnDocument.getPreSignedDocument())) {
                                returnDocument.setSignedData(PKCS7ResponseBase64);
                                returnDocument.setStatus(1);
                            } else {
                                byte[] array = signClose(PKCS7ResponseBase64, returnDocument.getPreSignedDocument(), SignatureContents);
                                if (returnDocument.isPatchSignatureAppearance()) {
                                    array = patchSignatureAppearance(array, userX509CertBase64);
                                }
                                String pdfBase64 = org.emcastle.util.encoders.Base64.toBase64String(array);
                                returnDocument.setSignedDocument(pdfBase64);
                                returnDocument.setStatus(1);
                            }
                            docsToReturn.add(returnDocument);
                        } catch (Exception e) {
                            docsToReturn.add(new ReturnDocument(0, "ESS-112", "Unable to get Append signature to document", docId));
                            continue;
                        }
                    }
                }
                serviceReturnObj.setPreSignedTempFile(tempFilePath);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setTransactionID(transactionID);
                serviceReturnObj.setReturnValues(docsToReturn);
                serviceReturnObj.setResponseCode(responseCode);
                serviceReturnObj.setStatus(1);
                return serviceReturnObj;
            }
            serviceReturnObj.setErrorCode("ESS-999");
            serviceReturnObj.setStatus(0);
            serviceReturnObj.setErrorMessage("Unknown error invalid status in xml");
            return serviceReturnObj;
        } catch (Exception e) {
            serviceReturnObj.setErrorCode("ESS-999");
            serviceReturnObj.setStatus(0);
            serviceReturnObj.setErrorMessage(e.getMessage());
            return serviceReturnObj;
        }
    }

    private byte[] signClose(String pkcs7, String preSignedValue, int SignatureContents) throws Exception {
        int contentEstimated = SignatureContents != 0 ? SignatureContents : 21000;
        byte[] preSignedDecoded = org.emcastle.util.encoders.Base64.decode(preSignedValue);
        String preSignedDoc = new String(preSignedDecoded, StandardCharsets.UTF_8);
        String[] parts = preSignedDoc.split("\\|");
        int position = Integer.parseInt(parts[0]);
        int bufferSize = Integer.parseInt(parts[1]);
        byte[] pdfBytes = org.emcastle.util.encoders.Base64.decode(parts[2]);
        byte[] pkcs7Bytes = org.emcastle.util.encoders.Base64.decode(pkcs7);
        PreSignResult preSign = new PreSignResult(position, bufferSize, pdfBytes, null, null);
        return pdfEngine.injectSignature(pkcs7Bytes, preSign, contentEstimated);
    }

    private byte[] patchSignatureAppearance(byte[] signedPdfBytes, String userX509CertBase64) {
        try {
            if (userX509CertBase64 == null || userX509CertBase64.trim().isEmpty())
                return signedPdfBytes;

            byte[] certBytes = org.emcastle.util.encoders.Base64.decode(userX509CertBase64.trim());
            X509CertificateStructure cert = X509CertificateStructure.getInstance(
                    org.emcastle.asn1.ASN1Primitive.fromByteArray(certBytes));
            X500Name subject = cert.getSubject();

            String certName = "Unknown";
            RDN[] cnRDNs = subject.getRDNs(BCStyle.CN);
            if (cnRDNs != null && cnRDNs.length > 0) {
                certName = IETFUtils.valueToString(cnRDNs[0].getFirst().getValue());
            }

            String aadhaarLast4 = "XXXX";
            try {
                RDN[] titleRDNs = subject.getRDNs(BCStyle.T);
                if (titleRDNs != null && titleRDNs.length > 0) {
                    String val = IETFUtils.valueToString(titleRDNs[0].getFirst().getValue());
                    if (val != null && val.length() >= 4) {
                        aadhaarLast4 = val.substring(val.length() - 4);
                    }
                }
            } catch (Exception ignored) { }

            return pdfEngine.patchSignatureAppearance(signedPdfBytes, certName, aadhaarLast4);
        } catch (Exception e) {
            return signedPdfBytes;
        }
    }

    protected eSignServiceReturn getStatus(String transactionId) {
        eSignServiceReturn serviceReturnObj = new eSignServiceReturn();
        try {
            if (eSignUtility.isNullOrWhitespace(transactionId)) {
                serviceReturnObj.setErrorCode("ESS-105");
                serviceReturnObj.setStatus(0);
                serviceReturnObj.setErrorMessage("transaction ID is required");
                return serviceReturnObj;
            }
            SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            TimeZone timeZone = TimeZone.getTimeZone("IST");
            tsFormat.setTimeZone(timeZone);
            Date now = new Date(System.currentTimeMillis());
            String timeStamp = tsFormat.format(now);
            String requestXML = eSignUtility.checkeSignStatus(timeStamp, transactionId, eSignSettings.getASPID());
            String signedRequestXML = eSignUtility.signXML(requestXML, pfxpath, password, pfxAlias);
            String URLEncodedsignedRequestXML = URLEncoder.encode(signedRequestXML, "UTF-8");
            serviceReturnObj.setRequestXML(signedRequestXML);
            String responseXML = "";
            try {
                responseXML = HttpsConnection.excutePostHttpsXml(eSignSettings.getESIGNStatusURL(), URLEncodedsignedRequestXML, proxyIp, proxyPort, proxyreq, transactionId);
            } catch (Exception e) {
                serviceReturnObj.setRequestXML(signedRequestXML);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setErrorCode("ESS-103");
                serviceReturnObj.setStatus(0);
                serviceReturnObj.setErrorMessage("Unable to call eSign Url" + e.getMessage());
                return serviceReturnObj;
            }
            if (responseXML.isEmpty()) {
                serviceReturnObj.setRequestXML(signedRequestXML);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setTransactionID(transactionId);
                serviceReturnObj.setErrorCode("ESS-104");
                serviceReturnObj.setStatus(0);
                serviceReturnObj.setErrorMessage("empty response from eSign Url");
                return serviceReturnObj;
            }
            Document doc = eSignUtility.convertStringToDocument(responseXML);
            if (doc == null) {
                serviceReturnObj.setRequestXML(signedRequestXML);
                serviceReturnObj.setResponseXML(responseXML);
                serviceReturnObj.setTransactionID(transactionId);
                serviceReturnObj.setErrorCode("ESS-104");
                serviceReturnObj.setErrorMessage("Unable to Parse response XMl document");
                serviceReturnObj.setStatus(0);
                return serviceReturnObj;
            }
            XPath xPath = XPathFactory.newInstance().newXPath();
            String status = eSignUtility.GetXpathValue(xPath, "/EsignResp/@status", doc);
            switch (status) {
                case "0":
                    String errormessage = eSignUtility.GetXpathValue(xPath, "/EsignResp/@errorMessage", doc);
                    String errorCode = eSignUtility.GetXpathValue(xPath, "/EsignResp/@errorCode", doc);
                    serviceReturnObj.setResponseXML(responseXML);
                    serviceReturnObj.setTransactionID(transactionId);
                    serviceReturnObj.setErrorCode(errorCode);
                    serviceReturnObj.setErrorMessage(errormessage);
                    serviceReturnObj.setStatus(0);
                    return serviceReturnObj;
                case "2":
                    serviceReturnObj.setRequestXML(signedRequestXML);
                    serviceReturnObj.setResponseXML(responseXML);
                    serviceReturnObj.setStatus(2);
                    return serviceReturnObj;
                default:
                    serviceReturnObj.setRequestXML(signedRequestXML);
                    serviceReturnObj.setResponseXML(responseXML);
                    serviceReturnObj.setStatus(1);
                    return serviceReturnObj;
            }
        } catch (Exception e) {
            serviceReturnObj.setErrorCode("ESS-999");
            serviceReturnObj.setStatus(0);
            serviceReturnObj.setErrorMessage(e.getMessage());
            return serviceReturnObj;
        }
    }

    private static String reformatPagelevelCoordinates(String pageLevelCoordinates, int totalPages) {
        String[] plArray = pageLevelCoordinates.split(";");
        ArrayList<String> newPageLevel = new ArrayList<>();
        for (String pl : plArray) {
            if (pl.isEmpty()) continue;
            String pageNumber = pl.split("-")[0];
            String coordinates = pl.split("-")[1];
            switch (pageNumber.trim().toLowerCase()) {
                case "l":   newPageLevel.add(totalPages + "-" + coordinates);     break;
                case "all":
                    for (int i = 1; i <= totalPages; i++) newPageLevel.add(i + "-" + coordinates);
                    break;
                case "sl":  newPageLevel.add((totalPages - 1) + "-" + coordinates); break;
                case "f":   newPageLevel.add("1-" + coordinates);                 break;
                case "s":   newPageLevel.add("2-" + coordinates);                 break;
                default:    newPageLevel.add(pl);                                  break;
            }
        }
        return String.join(";", newPageLevel);
    }

    protected eSignServiceReturn isValidPdf(String docBase64) {
        eSignServiceReturn resp = new eSignServiceReturn();
        resp.setStatus(0);
        try {
            byte[] decodePDF = org.emcastle.util.encoders.Base64.decode(docBase64);
            if (pdfEngine.isValidPdf(decodePDF)) {
                resp.setStatus(1);
            } else {
                resp.setErrorMessage("Invalid PDF document");
            }
        } catch (Exception e) {
            LOGGER.warning(e.getLocalizedMessage());
            resp.setErrorMessage("Something went wrong : " + e.getMessage());
        }
        return resp;
    }

    protected eSignServiceReturn performBankKYC(String transactionID, String IFSCCode, String bankName, String accountNumber, UserInfo userInfo, String BankKYCURL) {
        eSignServiceReturn serviceReturnObj = new eSignServiceReturn();
        try {
            if (eSignUtility.isNullOrWhitespace(transactionID)) {
                transactionID = UUID.randomUUID().toString().replace("-", "");
            }
            SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            TimeZone timeZone = TimeZone.getTimeZone("IST");
            tsFormat.setTimeZone(timeZone);
            Date now = new Date(System.currentTimeMillis() + 3 * 60 * 1000);
            String timeStamp = tsFormat.format(now);
            serviceReturnObj.setTransactionID(transactionID);
            String requestXML = eSignUtility.generateBankKYCXML(timeStamp, transactionID, IFSCCode, bankName, accountNumber, userInfo);
            String signedRequestXML = eSignUtility.signXML(requestXML, pfxpath, password, pfxAlias);
            String URLEncodedsignedRequestXML = URLEncoder.encode(signedRequestXML, "UTF-8");
            serviceReturnObj.setRequestXML(signedRequestXML);
            String responseXML = "";
            try {
                responseXML = HttpsConnection.excutePostHttpsXml(BankKYCURL, URLEncodedsignedRequestXML, proxyIp, proxyPort, proxyreq, transactionID);
            } catch (Exception e) {
                serviceReturnObj.setErrorCode("ESS-103");
                serviceReturnObj.setErrorMessage("Unable to call eSign Url");
                return serviceReturnObj;
            }
            if (responseXML.isEmpty()) {
                serviceReturnObj.setErrorCode("ESS-104");
                serviceReturnObj.setErrorMessage("empty response from eSign Url");
                return serviceReturnObj;
            }
            serviceReturnObj.setResponseXML(responseXML);
            Document doc = eSignUtility.convertStringToDocument(responseXML);
            if (doc == null) {
                serviceReturnObj.setErrorCode("ESS-104");
                serviceReturnObj.setErrorMessage("Unable to Parse response XMl document");
                return serviceReturnObj;
            }
            XPath xPath = XPathFactory.newInstance().newXPath();
            String status = eSignUtility.GetXpathValue(xPath, "/BankKYCResp/@status", doc);
            if (status.equals("0")) {
                String errormessage = eSignUtility.GetXpathValue(xPath, "/BankKYCResp/@error", doc);
                String errorCode = eSignUtility.GetXpathValue(xPath, "/BankKYCResp/@resCode", doc);
                serviceReturnObj.setErrorCode(errorCode);
                serviceReturnObj.setErrorMessage(errormessage);
                serviceReturnObj.setStatus(0);
                return serviceReturnObj;
            } else if (status.equals("1")) {
                String responseCode = eSignUtility.GetXpathValue(xPath, "/BankKYCResp/@resCode", doc);
                serviceReturnObj.setResponseCode(responseCode);
                serviceReturnObj.setStatus(1);
            }
            return serviceReturnObj;
        } catch (Exception e) {
            serviceReturnObj.setErrorCode("ESS-999");
            serviceReturnObj.setErrorMessage(e.getMessage());
            return serviceReturnObj;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers: build model objects from eSignInput
    // -----------------------------------------------------------------------

    private AppearanceSpec buildAppearanceSpec(eSignInput input, String timeStamp,
            Calendar cal, SimpleDateFormat tsFormat, TimeZone timeZone, Date now) throws Exception {
        eSign.AppearanceType mode = input.getAppearanceType();
        if (mode == null) mode = eSign.AppearanceType.StandardSignature;

        int fontSize = input.getSignatureFontSize();
        PdfColor fontColor = (input.getCustomStyle() != null) ? input.getCustomStyle().getFontColor() : null;
        boolean acro6Layers = !(input.isTickRequired());
        String layer2Text = null;
        String oneLinerText = null;
        byte[] imageBytes = null;
        ImageType advanceImageType = null;
        byte[] advanceSvgBytes = null;
        String leftSideText = null;
        String rightSideText = null;
        int[] rightBorderRgb = null;
        int[] leftBorderRgb = null;

        switch (mode) {
            case StandardSignature: {
                if (!eSignUtility.isNullOrEmpty(input.getAppearanceText())) {
                    layer2Text = input.getAppearanceText() + "\n";
                } else {
                    SimpleDateFormat displayFmt = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
                    displayFmt.setTimeZone(timeZone);
                    StringBuilder sb = new StringBuilder();
                    sb.append("Digitally Signed.\n");
                    if (!eSignUtility.isNullOrEmpty(input.getSignedBy())) {
                        sb.append("Name: ").append(input.getSignedBy()).append("\n");
                    }
                    sb.append("Date: ").append(displayFmt.format(now)).append("\n");
                    if (!eSignUtility.isNullOrEmpty(input.getReason())) {
                        sb.append("Reason: ").append(input.getReason()).append("\n");
                    }
                    if (!eSignUtility.isNullOrEmpty(input.getLocation())) {
                        sb.append("Location: ").append(input.getLocation()).append("\n");
                    }
                    layer2Text = sb.toString();
                }
                break;
            }
            case OneLiner:
                oneLinerText = input.getOneLiner();
                break;

            case SignatureImage: {
                if (input.getSignatureImage() != null) {
                    imageBytes = Base64.decode(input.getSignatureImage());
                }
                if (eSignUtility.isNullOrWhitespace(input.getAppearanceText())) {
                    StringBuilder l2t = new StringBuilder();
                    l2t.append("Digitally Signed.\n");
                    if (!eSignUtility.isNullOrWhitespace(input.getSignedBy())) {
                        l2t.append("Name: ").append(input.getSignedBy()).append("\n");
                    }
                    if (!eSignUtility.isNullOrWhitespace(input.getReason())) {
                        l2t.append("Reason: ").append(input.getReason()).append("\n");
                    }
                    if (!eSignUtility.isNullOrWhitespace(input.getLocation())) {
                        l2t.append("Location: ").append(input.getLocation()).append("\n");
                    }
                    Calendar c2 = Calendar.getInstance();
                    c2.add(Calendar.MINUTE, 1);
                    l2t.append("Date: ").append(tsFormat.format(c2.getTime())).append("\n");
                    layer2Text = l2t.toString();
                } else {
                    layer2Text = input.getAppearanceText();
                }
                break;
            }
            case advanceSignature: {
                advanceImageType = input.getAdvanceSignature().getImageType();
                if (advanceImageType == ImageType.SVG) {
                    String str = new String(Base64.decode(input.getAdvanceSignature().getImagebase64()));
                    str = str.replaceAll("(\\s+)font=\"(.*?)\"", "");
                    str = str.replaceAll("fill='transparent'", "fill='none'");
                    advanceSvgBytes = str.getBytes(StandardCharsets.UTF_8);
                } else {
                    imageBytes = Base64.decode(input.getAdvanceSignature().getImagebase64());
                }
                leftSideText = input.getAdvanceSignature().getLeftSideText();
                rightSideText = input.getAdvanceSignature().getRightSideText();
                break;
            }
            case ColoredGraphic: {
                if (input.getColoredGraphicInputs() != null) {
                    rightBorderRgb = input.getColoredGraphicInputs().getRightBorder();
                    leftBorderRgb  = input.getColoredGraphicInputs().getLeftBorder();
                } else {
                    rightBorderRgb = new int[]{148, 0, 211};
                    leftBorderRgb  = new int[]{255, 165, 0};
                }
                StringBuilder cgText = new StringBuilder();
                if (!eSignUtility.isNullOrEmpty(input.getSignedBy())) {
                    cgText.append("Signed by: ").append(input.getSignedBy()).append("\n");
                }
                if (!eSignUtility.isNullOrEmpty(input.getReason())) {
                    cgText.append("Reason: ").append(input.getReason()).append("\n");
                }
                if (!eSignUtility.isNullOrEmpty(input.getLocation())) {
                    cgText.append("Location: ").append(input.getLocation()).append("\n");
                }
                layer2Text = cgText.toString();
                break;
            }
            case BackgroundImage: {
                if (input.getSignatureImage() != null) {
                    imageBytes = Base64.decode(input.getSignatureImage());
                }
                StringBuilder bgText = new StringBuilder();
                if (!eSignUtility.isNullOrEmpty(input.getAppearanceText())) {
                    bgText.append(input.getAppearanceText()).append("\n");
                } else {
                    if (!eSignUtility.isNullOrEmpty(input.getSignedBy())) {
                        bgText.append("Digitally Signed by:\n");
                        bgText.append("Name: ").append(input.getSignedBy()).append("\n");
                    }
                    if (!eSignUtility.isNullOrEmpty(input.getLocation())) {
                        bgText.append("Location: ").append(input.getLocation()).append("\n");
                    }
                    if (!eSignUtility.isNullOrEmpty(input.getReason())) {
                        bgText.append("Reason: ").append(input.getReason()).append("\n");
                    }
                    bgText.append("Date: ");
                    SimpleDateFormat fmt = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy");
                    fmt.setTimeZone(timeZone);
                    bgText.append(fmt.format(now)).append("\n");
                }
                layer2Text = bgText.toString();
                break;
            }
            default:
                break;
        }

        return new AppearanceSpec(mode, layer2Text, fontSize, fontColor,
                acro6Layers, false, oneLinerText, imageBytes,
                advanceImageType, advanceSvgBytes, leftSideText, rightSideText,
                rightBorderRgb, leftBorderRgb);
    }

    private BorderSpec buildBorderSpec(eSignInput input) {
        if (input.getAppearanceType() == eSign.AppearanceType.ColoredGraphic) {
            return new BorderSpec(true, 5.0f, new PdfColor(0, 0, 0));
        } else if (input.isBorderRequired()) {
            return new BorderSpec(false, 0.5f, new PdfColor(255, 0, 0));
        }
        return null;
    }

    private String buildCoordinateString(List<PageTextMatch> matches, String offset,
            int height, int width, ContentSearch.Position position) {
        String[] offsets = offset.split("\\|");
        int offX = Integer.parseInt(offsets[0]);
        int offY = Integer.parseInt(offsets[1]);
        StringBuilder sb = new StringBuilder();
        for (PageTextMatch m : matches) {
            String coord = getCordFromPosition(m.x1, m.y1, m.x2, m.y2, position, offX, offY, height, width);
            sb.append(m.page).append('-').append(coord).append(';');
        }
        return sb.toString();
    }

    private static String getCordFromPosition(float X1, float Y1, float X2, float Y2,
            ContentSearch.Position position, int offX, int offY, int height, int width) {
        switch (position) {
            case OTL: return Math.round(X1+offX-width)+","+ Math.round(Y2+offY)+","+ Math.round(X1+offX)+","+ Math.round(Y2+offY+height);
            case OTM: return Math.round(X1+offX+(X2-X1-width)/2)+","+ Math.round(Y2+offY)+","+ Math.round(X1+offX+(X2-X1+width)/2)+","+ Math.round(Y2+offY+height);
            case OTR: return Math.round(X2+offX)+","+ Math.round(Y2+offY)+","+ Math.round(X2+offX+width)+","+ Math.round(Y2+offY+height);
            case OBL: return Math.round(X1+offX-width)+","+ Math.round(Y1+offY-height)+","+ Math.round(X1+offX)+","+ Math.round(Y1+offY);
            case OBM: return Math.round(X1+offX+(X2-X1-width)/2)+","+ Math.round(Y1+offY-height)+","+ Math.round(X1+offX+(X2-X1+width)/2)+","+ Math.round(Y1+offY);
            case OBR: return Math.round(X2+offX)+","+ Math.round(Y1+offY-height)+","+ Math.round(X2+offX+width)+","+ Math.round(Y1+offY);
            case ITL: return Math.round(X1+offX)+","+ Math.round(Y2+offY-height)+","+ Math.round(X1+offX+width)+","+ Math.round(Y2+offY);
            case ITM: return Math.round(X1+offX+(X2-X1-width)/2)+","+ Math.round(Y2+offY-height)+","+ Math.round(X1+offX+(X2-X1+width)/2)+","+ Math.round(Y2+offY);
            case ITR: return Math.round(X2+offX-width)+","+ Math.round(Y2+offY-height)+","+ Math.round(X2+offX)+","+ Math.round(Y2+offY);
            case IML: return Math.round(X1+offX)+","+ Math.round(Y1+offY+(Y2-Y1-height)/2)+","+ Math.round(X1+offX+width)+","+ Math.round(Y1+offY+(Y2-Y1+height)/2);
            case IMC: return Math.round(X1+offX+(X2-X1-width)/2)+","+ Math.round(Y1+offY+(Y2-Y1-height)/2)+","+ Math.round(X1+offX+(X2-X1+width)/2)+","+ Math.round(Y1+offY+(Y2-Y1+height)/2);
            case IMR: return Math.round(X2+offX-width)+","+ Math.round(Y1+offY+(Y2-Y1-height)/2)+","+ Math.round(X2+offX)+","+ Math.round(Y1+offY+(Y2-Y1+height)/2);
            case IBL: return Math.round(X1+offX)+","+ Math.round(Y1+offY)+","+ Math.round(X1+offX+width)+","+ Math.round(Y1+offY+height);
            case IBM: return Math.round(X1+offX+(X2-X1-width)/2)+","+ Math.round(Y1+offY)+","+ Math.round(X1+offX+(X2-X1+width)/2)+","+ Math.round(Y1+offY+height);
            case IBR: return Math.round(X2+offX-width)+","+ Math.round(Y1+offY)+","+ Math.round(X2+offX)+","+ Math.round(Y1+offY+height);
            default:  return "";
        }
    }
}
