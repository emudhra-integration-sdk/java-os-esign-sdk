package com.emudhra.esign;

import com.emudhra.esign.pdf.PdfEngine;
import com.emudhra.esign.pdf.model.PageTextMatch;
import java.io.IOException;
import java.util.List;

/**
 * @deprecated Logic now lives in {@link PdfEngine#findText}.
 */
@Deprecated
public class TextCoordinates {

    public String getCoordinates(byte[] pdfBytes, String textToSearch, String offset, int height, int width, ContentSearch.Position position) throws IOException {
        String[] offsets = offset.split("\\|");
        int offX = Integer.parseInt(offsets[0]);
        int offY = Integer.parseInt(offsets[1]);

        PdfEngine engine = new PdfEngine();
        List<PageTextMatch> matches = engine.findText(pdfBytes, textToSearch);

        StringBuilder coordinates = new StringBuilder();
        for (PageTextMatch m : matches) {
            String coord = getCordFromPosition(m.x1, m.y1, m.x2, m.y2, position, offX, offY, height, width);
            coordinates.append(m.page).append('-').append(coord).append(';');
        }
        return coordinates.toString();
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
        }
        return "";
    }
}

class Coord {
    float X1, Y1, X2, Y2;
    public Coord(float X1, float Y1, float X2, float Y2) {
        this.X1 = X1; this.Y1 = Y1; this.X2 = X2; this.Y2 = Y2;
    }
}
