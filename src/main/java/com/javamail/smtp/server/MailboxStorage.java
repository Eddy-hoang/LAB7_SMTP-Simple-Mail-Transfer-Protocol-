package com.javamail.smtp.server;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Quản lý lưu trữ email dưới dạng file .eml trên đĩa cứng.
 * Cấu trúc: mailboxes/{recipient@domain}/{timestamp}.eml
 */
public class MailboxStorage {

    private static final Logger logger = Logger.getLogger(MailboxStorage.class.getName());
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final String basePath;

    public MailboxStorage(String basePath) {
        this.basePath = basePath;
    }

    /**
     * Lưu email vào mailbox của người nhận.
     *
     * @param recipient địa chỉ email người nhận (vd: alice@localhost)
     * @param rawEmail  nội dung email raw (MIME format)
     * @return đường dẫn file đã lưu
     */
    public Path save(String recipient, String rawEmail) throws IOException {
        // Tạo thư mục mailbox cho recipient (thay ':' và '@' để tạo tên folder hợp lệ)
        String folderName = sanitizeFolderName(recipient);
        Path mailboxDir = Paths.get(basePath, folderName);
        Files.createDirectories(mailboxDir);

        // Tạo tên file với timestamp
        String filename = LocalDateTime.now().format(TIMESTAMP) + ".eml";
        Path filePath = mailboxDir.resolve(filename);

        // Ghi nội dung email
        Files.writeString(filePath, rawEmail, java.nio.charset.StandardCharsets.UTF_8);
        logger.info("Đã lưu email: " + filePath);

        return filePath;
    }

    /**
     * Lấy danh sách tất cả file .eml trong mailbox của người nhận.
     *
     * @param recipient địa chỉ email người nhận
     * @return danh sách đường dẫn đến các file .eml
     */
    public List<Path> listEmails(String recipient) throws IOException {
        String folderName = sanitizeFolderName(recipient);
        Path mailboxDir = Paths.get(basePath, folderName);

        if (!Files.exists(mailboxDir)) return new ArrayList<>();

        List<Path> emails = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mailboxDir, "*.eml")) {
            for (Path entry : stream) {
                emails.add(entry);
            }
        }
        // Sắp xếp theo thời gian (tên file có timestamp)
        emails.sort(Path::compareTo);
        return emails;
    }

    /**
     * Đọc nội dung một file .eml.
     *
     * @param emailPath đường dẫn đến file .eml
     * @return nội dung raw của email
     */
    public String readEmail(Path emailPath) throws IOException {
        return Files.readString(emailPath, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Xóa một file email.
     *
     * @param emailPath đường dẫn đến file cần xóa
     */
    public void deleteEmail(Path emailPath) throws IOException {
        Files.deleteIfExists(emailPath);
        logger.info("Đã xóa email: " + emailPath);
    }

    /**
     * Đếm số email trong mailbox.
     */
    public int countEmails(String recipient) throws IOException {
        return listEmails(recipient).size();
    }

    /**
     * Tính tổng kích thước mailbox (bytes).
     */
    public long getTotalSize(String recipient) throws IOException {
        long total = 0;
        for (Path p : listEmails(recipient)) {
            total += Files.size(p);
        }
        return total;
    }

    /**
     * Làm sạch tên thư mục (bỏ ký tự không hợp lệ trên Windows/Linux).
     */
    private String sanitizeFolderName(String recipient) {
        return recipient.replaceAll("[<>:\"/\\\\|?*]", "_");
    }
}
