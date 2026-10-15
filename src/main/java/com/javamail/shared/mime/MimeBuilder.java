package com.javamail.shared.mime;

import com.javamail.shared.model.AttachmentPart;
import com.javamail.shared.model.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Xây dựng nội dung email theo chuẩn MIME (RFC 2045).
 * Hỗ trợ: plain text, HTML, multipart/mixed (có attachment).
 */
public class MimeBuilder {

    private static final String BOUNDARY = "----=_JavaMailBoundary";
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z");

    /**
     * Tạo nội dung email đầy đủ theo chuẩn MIME để gửi qua SMTP.
     */
    public static String build(MimeMessage message) {
        StringBuilder sb = new StringBuilder();

        // ===== HEADERS =====
        sb.append("Message-ID: ").append(message.getMessageId()).append("\r\n");
        sb.append("From: ").append(message.getFrom()).append("\r\n");
        sb.append("To: ").append(String.join(", ", message.getTo())).append("\r\n");

        if (!message.getCc().isEmpty()) {
            sb.append("Cc: ").append(String.join(", ", message.getCc())).append("\r\n");
        }

        sb.append("Subject: ").append(encodeHeader(message.getSubject())).append("\r\n");
        sb.append("Date: ").append(
                message.getDate().format(DateTimeFormatter.RFC_1123_DATE_TIME)
        ).append("\r\n");
        sb.append("MIME-Version: 1.0").append("\r\n");

        // ===== BODY =====
        boolean hasHtml = message.getBodyHtml() != null && !message.getBodyHtml().isEmpty();
        boolean hasAttachments = message.hasAttachments();

        if (hasAttachments) {
            // multipart/mixed — text + attachments
            sb.append("Content-Type: multipart/mixed; boundary=\"").append(BOUNDARY).append("\"\r\n");
            sb.append("\r\n");
            sb.append("This is a multi-part message in MIME format.\r\n");

            // Text/HTML part
            sb.append("--").append(BOUNDARY).append("\r\n");
            if (hasHtml) {
                sb.append("Content-Type: text/html; charset=UTF-8\r\n");
                sb.append("Content-Transfer-Encoding: quoted-printable\r\n");
                sb.append("\r\n");
                sb.append(message.getBodyHtml()).append("\r\n");
            } else {
                sb.append("Content-Type: text/plain; charset=UTF-8\r\n");
                sb.append("Content-Transfer-Encoding: 8bit\r\n");
                sb.append("\r\n");
                sb.append(message.getBodyText()).append("\r\n");
            }

            // Attachment parts
            for (AttachmentPart attachment : message.getAttachments()) {
                sb.append("--").append(BOUNDARY).append("\r\n");
                sb.append("Content-Type: ").append(attachment.getContentType())
                  .append("; name=\"").append(attachment.getFilename()).append("\"\r\n");
                sb.append("Content-Transfer-Encoding: base64\r\n");
                sb.append("Content-Disposition: attachment; filename=\"")
                  .append(attachment.getFilename()).append("\"\r\n");
                sb.append("\r\n");
                // Encode to Base64
                String encoded = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.UTF_8))
                        .encodeToString(attachment.getData());
                sb.append(encoded).append("\r\n");
            }

            sb.append("--").append(BOUNDARY).append("--\r\n");

        } else if (hasHtml) {
            // Simple HTML email
            sb.append("Content-Type: text/html; charset=UTF-8\r\n");
            sb.append("Content-Transfer-Encoding: quoted-printable\r\n");
            sb.append("\r\n");
            sb.append(message.getBodyHtml()).append("\r\n");
        } else {
            // Simple plain text email
            sb.append("Content-Type: text/plain; charset=UTF-8\r\n");
            sb.append("Content-Transfer-Encoding: 8bit\r\n");
            sb.append("\r\n");
            sb.append(message.getBodyText() != null ? message.getBodyText() : "").append("\r\n");
        }

        return sb.toString();
    }

    /**
     * Encode header value theo RFC 2047 (UTF-8 Base64) nếu có ký tự đặc biệt.
     */
    private static String encodeHeader(String value) {
        if (value == null) return "";
        // Kiểm tra có ký tự non-ASCII không
        for (char c : value.toCharArray()) {
            if (c > 127) {
                String encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
                return "=?UTF-8?B?" + encoded + "?=";
            }
        }
        return value;
    }
}
