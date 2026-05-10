package com.emudhra.esign.pdf.model;

public final class PreSignResult {
    /** Byte offset of the /Contents hex placeholder inside preSignedPdfBytes */
    public final int placeholderPosition;
    /** Total length of preSignedPdfBytes */
    public final int outputBufferSize;
    /** Complete incremental PDF with zeroed /Contents placeholder */
    public final byte[] preSignedPdfBytes;
    /** Exact bytes that SHA-256 is computed over (concatenation of the two ByteRange regions) */
    public final byte[] bytesToHash;
    /** /ByteRange [a b c d] values parsed from the saved incremental PDF */
    public final long[] byteRange;

    public PreSignResult(int placeholderPosition, int outputBufferSize,
                         byte[] preSignedPdfBytes, byte[] bytesToHash, long[] byteRange) {
        this.placeholderPosition = placeholderPosition;
        this.outputBufferSize = outputBufferSize;
        this.preSignedPdfBytes = preSignedPdfBytes;
        this.bytesToHash = bytesToHash;
        this.byteRange = byteRange;
    }
}
