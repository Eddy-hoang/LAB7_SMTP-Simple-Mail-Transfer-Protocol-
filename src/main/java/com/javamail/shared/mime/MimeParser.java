package com.javamail.shared.mime;

import com.javamail.shared.model.AttachmentPart;
import com.javamail.shared.model.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse nội dung raw email (.eml) thành đối tượng MimeMessage.
 * Hỗ trợ: plain text, HTML, multipart/mixed (có attachment).
 */
public class MimeParser {

    private static final Pattern HEADER_PATTERN = Pattern.compile("^([\\w-]+):\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern BOUNDARY_PATTERN = Pattern.compile("boundary=\"?([^\"\\r\\n;]+)\"?");
    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=\"?([^\"\\s;]+)\"?");
    private static final Pattern FILENAME_PATTERN = Pattern.compile("filename=\"?([^\"\\r\\n]+)\"?");

    /**
     * Parse raw email string thành MimeMessage.
     */
    public static MimeMessage parse(String rawEmail) {
        MimeMessage message = new MimeMessage();
        message.setRawContent(rawEmail);

        // Tách headers và body
        int headerEnd = rawEmail.indexOf("\r\n\r\n");
        if (headerEnd == -1) headerEnd = rawEmail.indexOf("\n\n");
        if (headerEnd == -1) return message;

        String headerSection = rawEmail.substring(0, headerEnd);
        String body = rawEmail.substring(headerEnd).trim();

        // Parse headers
        parseHeaders(headerSection, message);

        // Parse body theo Content-Type
        String contentType = getHeaderValue(headerSection, "Content-Type");
        if (contentType != null && contentType.toLowerCase().contains("multipart")) {
            parseMultipart(body, contentType, message);
        } else if (contentType != null && contentType.toLowerCase().contains("text/html")) {
            message.setBodyHtml(body);
        } else {
            message.setBodyText(body);
        }

        return message;
    }

    /**
     * Parse các header chính vào MimeMessage.
     */
    private static void parseHeaders(String headerSection, MimeMessage message) {
        // Unfold headers (RFC 2822 line folding)
        String unfolded = headerSection.replaceAll("\r\n[ \t]+", " ").replaceAll("\n[ \t]+", " ");

        Matcher m = HEADER_PATTERN.matcher(unfolded);
        while (m.find()) {
            String name = m.group(1).toLowerCase(Locale.ROOT);
            String value = m.group(2).trim();

            switch (name) {
                case "from"       -> message.setFrom(value);
                case "subject"    -> message.setSubject(decodeHeader(value));
                case "message-id" -> message.setMessageId(value);
                case "to"         -> { for (String r : value.split(",")) message.addTo(r.trim()); }
                case "cc"         -> { for (String r : value.split(",")) message.addCc(r.trim()); }
                case "bcc"        -> { for (String r : value.split(",")) message.addBcc(r.trim()); }
                case "date"       -> message.setDate(parseDate(value));
            }
        }
    }

    /**
     * Parse multipart body, tách text/html/attachments.
     */
    private static void parseMultipart(String body, String contentType, MimeMessage message) {
        Matcher bm = BOUNDARY_PATTERN.matcher(contentType);
        if (!bm.find()) return;

        String boundary = bm.group(1);
        String[] parts = body.split("--" + Pattern.quote(boundary));

        for (String part : parts) {
            if (part.trim().isEmpty() || part.trim().equals("--")) continue;

            int partHeaderEnd = part.indexOf("\r\n\r\n");
            if (partHeaderEnd == -1) partHeaderEnd = part.indexOf("\n\n");
            if (partHeaderEnd == -1) continue;

            String partHeader = part.substring(0, partHeaderEnd);
            String partBody = part.substring(partHeaderEnd).trim();

            String partContentType = getHeaderValue(partHeader, "Content-Type");
            String contentDisposition = getHeaderValue(partHeader, "Content-Disposition");
            String encoding = getHeaderValue(partHeader, "Content-Transfer-Encoding");

            if (partContentType == null) continue;

            String pct = partContentType.toLowerCase();

            if (pct.contains("text/plain") && contentDisposition == null) {
                message.setBodyText(decodeBody(partBody, encoding));
            } else if (pct.contains("text/html")) {
                message.setBodyHtml(decodeBody(partBody, encoding));
            } else if (contentDisposition != null && contentDisposition.toLowerCase().contains("attachment")) {
                // Attachment
                Matcher fm = FILENAME_PATTERN.matcher(partHeader);
                String filename = fm.find() ? fm.group(1) : "attachment";

                AttachmentPart att = new AttachmentPart();
                att.setFilename(filename);
                att.setContentType(partContentType.split(";")[0].trim());
                att.setBase64Data(partBody.replaceAll("\\s", ""));

                if ("base64".equalsIgnoreCase(encoding)) {
                    try {
                        byte[] decoded = Base64.getDecoder().decode(att.getBase64Data());
                        att.setData(decoded);
                    } catch (Exception e) {
                        // Giữ lại base64 nếu không decode được
                    }
                }
                message.addAttachment(att);
            }
        }
    }

    /**
     * Decode body theo encoding (base64, quoted-printable, hoặc plain).
     */
    private static String decodeBody(String body, String encoding) {
        if (encoding == null) return body;
        return switch (encoding.toLowerCase().trim()) {
            case "base64" -> new String(Base64.getMimeDecoder().decode(body.replaceAll("\\s", "")), StandardCharsets.UTF_8);
            default -> body;
        };
    }

    /**
     * Decode RFC 2047 encoded header (=?UTF-8?B?...?=).
     */
    private static String decodeHeader(String value) {
        if (value == null) return "";
        Pattern encoded = Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?([^?]+)\\?=");
        Matcher m = encoded.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String charset = m.group(1);
            String type = m.group(2);
            String data = m.group(3);
            try {
                byte[] bytes = "B".equalsIgnoreCase(type)
                        ? Base64.getDecoder().decode(data)
                        : data.getBytes(StandardCharsets.UTF_8);
                m.appendReplacement(sb, new String(bytes, charset));
            } catch (Exception e) {
                m.appendReplacement(sb, data);
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Lấy giá trị của một header theo tên.
     */
    private static String getHeaderValue(String headers, String name) {
        Pattern p = Pattern.compile("^" + Pattern.quote(name) + ":\\s*(.+)$",
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(headers);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * Parse date string từ email header.
     */
    private static LocalDateTime parseDate(String dateStr) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
            return LocalDateTime.parse(dateStr, formatter);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
