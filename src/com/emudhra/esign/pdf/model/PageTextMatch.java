package com.emudhra.esign.pdf.model;

public final class PageTextMatch {
    public final int page;
    public final float x1, y1, x2, y2;

    public PageTextMatch(int page, float x1, float y1, float x2, float y2) {
        this.page = page;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }
}
