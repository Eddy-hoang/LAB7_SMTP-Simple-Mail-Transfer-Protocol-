package com.javamail.shared.model;

/**
 * Đại diện cho một file đính kèm trong email (MIME attachment).
 */
public class AttachmentPart {

    private String filename;
    private String contentType;   // e.g., "image/png", "application/pdf"
    private byte[] data;          // Raw file bytes
    private String base64Data;    // Base64-encoded content (for MIME)
    private long size;

    public AttachmentPart() {}

    public AttachmentPart(String filename, String contentType, byte[] data) {
        this.filename = filename;
        this.contentType = contentType;
        this.data = data;
        this.size = data != null ? data.length : 0;
    }

    // ==================== Getters & Setters ====================

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public byte[] getData() { return data; }
    public void setData(byte[] data) {
        this.data = data;
        this.size = data != null ? data.length : 0;
    }

    public String getBase64Data() { return base64Data; }
    public void setBase64Data(String base64Data) { this.base64Data = base64Data; }

    public long getSize() { return size; }

    /**
     * Trả về kích thước file dưới dạng string đọc được (KB, MB).
     */
    public String getReadableSize() {
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        else return String.format("%.1f MB", size / (1024.0 * 1024));
    }

    @Override
    public String toString() {
        return "AttachmentPart{filename='" + filename + "', type='" + contentType + "', size=" + getReadableSize() + "}";
    }
}
