package com.emudhra.esign.pdf.model;

import java.util.Calendar;

public final class SignatureMetadata {
    public final String reason;
    public final String location;
    public final String signerName;
    public final String creator;
    public final Calendar signDate;

    public SignatureMetadata(String reason, String location, String signerName,
                             String creator, Calendar signDate) {
        this.reason = reason;
        this.location = location;
        this.signerName = signerName;
        this.creator = creator;
        this.signDate = signDate;
    }
}
