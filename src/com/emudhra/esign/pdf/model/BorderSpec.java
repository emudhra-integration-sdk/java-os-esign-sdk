package com.emudhra.esign.pdf.model;

public final class BorderSpec {
    public final boolean colored;
    public final float lineWidth;
    public final PdfColor color;

    public BorderSpec(boolean colored, float lineWidth, PdfColor color) {
        this.colored = colored;
        this.lineWidth = lineWidth;
        this.color = color;
    }
}
