package com.emudhra.esign;

import com.emudhra.esign.pdf.model.PdfColor;

public class CustomStyle {

    private String border;
    private String fontSize;
    private PdfColor fontColor;

    public PdfColor getFontColor() {
        return fontColor;
    }

    public void setFontColor(PdfColor fontColor) {
        this.fontColor = fontColor;
    }

    public String getBorder() {
        return border;
    }

    public void setBorder(String border) {
        this.border = border;
    }

    public String getFontSize() {
        return fontSize;
    }

    public void setFontSize(String fontSize) {
        this.fontSize = fontSize;
    }
}
