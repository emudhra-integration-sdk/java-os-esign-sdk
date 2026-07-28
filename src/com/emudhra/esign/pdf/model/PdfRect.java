package com.emudhra.esign.pdf.model;

public final class PdfRect {
    public final float llx;
    public final float lly;
    public final float urx;
    public final float ury;

    public PdfRect(float llx, float lly, float urx, float ury) {
        this.llx = llx;
        this.lly = lly;
        this.urx = urx;
        this.ury = ury;
    }

    public float getWidth()  { return urx - llx; }
    public float getHeight() { return ury - lly; }

    @Override
    public String toString() {
        return "PdfRect[" + llx + "," + lly + "," + urx + "," + ury + "]";
    }
}
