package com.emudhra.esign.pdf.model;

import com.emudhra.esign.Enums.ImageType;
import com.emudhra.esign.eSign.AppearanceType;

public final class AppearanceSpec {
    public final AppearanceType mode;

    // StandardSignature
    public final String layer2Text;
    public final int fontSize;
    public final PdfColor fontColor;
    public final boolean acro6Layers;
    public final boolean certified;

    // OneLiner
    public final String oneLinerText;

    // SignatureImage / BackgroundImage
    public final byte[] imageBytes;

    // AdvanceSignature
    public final ImageType advanceImageType;
    public final byte[] advanceSvgBytes;
    public final String leftSideText;
    public final String rightSideText;

    // ColoredGraphic
    public final int[] rightBorderRgb;
    public final int[] leftBorderRgb;

    public AppearanceSpec(AppearanceType mode,
                          String layer2Text, int fontSize, PdfColor fontColor,
                          boolean acro6Layers, boolean certified,
                          String oneLinerText,
                          byte[] imageBytes,
                          ImageType advanceImageType, byte[] advanceSvgBytes,
                          String leftSideText, String rightSideText,
                          int[] rightBorderRgb, int[] leftBorderRgb) {
        this.mode = mode;
        this.layer2Text = layer2Text;
        this.fontSize = fontSize;
        this.fontColor = fontColor;
        this.acro6Layers = acro6Layers;
        this.certified = certified;
        this.oneLinerText = oneLinerText;
        this.imageBytes = imageBytes;
        this.advanceImageType = advanceImageType;
        this.advanceSvgBytes = advanceSvgBytes;
        this.leftSideText = leftSideText;
        this.rightSideText = rightSideText;
        this.rightBorderRgb = rightBorderRgb;
        this.leftBorderRgb = leftBorderRgb;
    }
}
