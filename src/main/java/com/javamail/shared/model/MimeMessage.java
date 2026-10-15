package com.javamail.shared.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Model chung đại diện cho một email message theo chuẩn MIME.
 * Được dùng bởi cả SMTP Server/Client và POP3 Server/Client.
 */
public class MimeMessage {

    private String messageId;
    private String from;
    private List<String> to;
    private List<String> cc;
    private List<String> bcc;
    private String subject;
    private String bodyText;       // plain text body
    private String bodyHtml;       // HTML body (optional)
    private List<AttachmentPart> attachments;
    private LocalDateTime date;
    private String rawContent;     // raw .eml content

    public MimeMessage() {
        this.to = new ArrayList<>();
        this.cc = new ArrayList<>();
        this.bcc = new ArrayList<>();
        this.attachments = new ArrayList<>();
        this.date = LocalDateTime.now();
        this.messageId = generateMessageId();
    }

    private String generateMessageId() {
        return "<" + System.currentTimeMillis() + "@javamail.local>";
    }

    // ==================== Getters & Setters ====================

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public List<String> getTo() { return to; }
    public void setTo(List<String> to) { this.to = to; }
    public void addTo(String recipient) { this.to.add(recipient); }

    public List<String> getCc() { return cc; }
    public void setCc(List<String> cc) { this.cc = cc; }
    public void addCc(String recipient) { this.cc.add(recipient); }

    public List<String> getBcc() { return bcc; }
    public void setBcc(List<String> bcc) { this.bcc = bcc; }
    public void addBcc(String recipient) { this.bcc.add(recipient); }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }

    public String getBodyHtml() { return bodyHtml; }
    public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }

    public List<AttachmentPart> getAttachments() { return attachments; }
    public void addAttachment(AttachmentPart attachment) { this.attachments.add(attachment); }
    public boolean hasAttachments() { return !attachments.isEmpty(); }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }

    @Override
    public String toString() {
        return "MimeMessage{" +
                "from='" + from + '\'' +
                ", to=" + to +
                ", subject='" + subject + '\'' +
                ", date=" + date +
                ", attachments=" + attachments.size() +
                '}';
    }
}
