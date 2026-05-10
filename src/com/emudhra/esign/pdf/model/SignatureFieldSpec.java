package com.emudhra.esign.pdf.model;

import java.util.List;

public final class SignatureFieldSpec {
    /** One rectangle per page entry in pages[]. */
    public final List<PdfRect> rects;
    /** 1-based page numbers where the signature field appears. */
    public final List<Integer> pages;
    public final boolean isCoSign;
    public final AppearanceSpec appearance;
    public final SignatureMetadata metadata;
    /** null = no border */
    public final BorderSpec border;

    public SignatureFieldSpec(List<PdfRect> rects, List<Integer> pages, boolean isCoSign,
                              AppearanceSpec appearance, SignatureMetadata metadata,
                              BorderSpec border) {
        this.rects = rects;
        this.pages = pages;
        this.isCoSign = isCoSign;
        this.appearance = appearance;
        this.metadata = metadata;
        this.border = border;
    }
}
