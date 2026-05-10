package com.emudhra.esign.pdf.model;

public final class PdfColor {
    public final int r, g, b;

    public static final PdfColor BLACK = new PdfColor(0, 0, 0);
    public static final PdfColor WHITE = new PdfColor(255, 255, 255);

    public PdfColor(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public static PdfColor of(int r, int g, int b) {
        return new PdfColor(r, g, b);
    }

    public float[] toFloatRGB() {
        return new float[]{ r / 255f, g / 255f, b / 255f };
    }
}
