package com.javamail;

import com.javamail.smtp.server.SmtpServer;
import com.javamail.smtp.client.SmtpClient;
import com.javamail.pop3.server.Pop3Server;
import com.javamail.pop3.client.Pop3Client;
import com.javamail.shared.model.MimeMessage;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: Gửi email qua SMTP rồi lấy về qua POP3.
 * Chạy cả SMTP Server và POP3 Server trên localhost.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmailIntegrationTest {

    private static final int SMTP_PORT    = 9025;  // Port test riêng để tránh conflict
    private static final int POP3_PORT    = 9110;
    private static final String MAILBOX   = "test_mailboxes";
    private static final String RECIPIENT = "alice@localhost";

    private static SmtpServer smtpServer;
    private static Pop3Server  pop3Server;
    private static Thread      smtpThread;
    private static Thread      pop3Thread;

    @BeforeAll
    static void startServers() throws Exception {
        // Tạo thư mục mailbox test
        Files.createDirectories(Paths.get(MAILBOX));

        // Khởi động SMTP Server
        smtpServer = new SmtpServer(SMTP_PORT, MAILBOX);
        smtpThread = new Thread(() -> {
            try { smtpServer.start(); } catch (IOException e) { /* expected on shutdown */ }
        });
        smtpThread.setDaemon(true);
        smtpThread.start();

        // Khởi động POP3 Server
        pop3Server = new Pop3Server(POP3_PORT, MAILBOX);
        pop3Thread = new Thread(() -> {
            try { pop3Server.start(); } catch (IOException e) { /* expected on shutdown */ }
        });
        pop3Thread.setDaemon(true);
        pop3Thread.start();

        // Đợi server khởi động
        Thread.sleep(500);
    }

    @AfterAll
    static void stopServers() throws Exception {
        smtpServer.stop();
        pop3Server.stop();

        // Dọn dẹp thư mục test
        deleteDirectory(Paths.get(MAILBOX));
    }

    @Test
    @Order(1)
    @DisplayName("Gửi email plain text qua SMTP")
    void testSendPlainTextEmail() throws IOException {
        MimeMessage message = new MimeMessage();
        message.setFrom("bob@localhost");
        message.addTo(RECIPIENT);
        message.setSubject("Test Email #1");
        message.setBodyText("Xin chào! Đây là email test từ JavaMail.");

        SmtpClient client = new SmtpClient("localhost", SMTP_PORT);
        assertDoesNotThrow(() -> client.sendEmail(message));
    }

    @Test
    @Order(2)
    @DisplayName("Gửi email với nhiều người nhận")
    void testSendMultiRecipient() throws IOException {
        MimeMessage message = new MimeMessage();
        message.setFrom("bob@localhost");
        message.addTo(RECIPIENT);
        message.addTo("charlie@localhost");
        message.addCc("dave@localhost");
        message.setSubject("Multi-recipient Test");
        message.setBodyText("Email gửi cho nhiều người.");

        SmtpClient client = new SmtpClient("localhost", SMTP_PORT);
        assertDoesNotThrow(() -> client.sendEmail(message));
    }

    @Test
    @Order(3)
    @DisplayName("Nhận email qua POP3 — kiểm tra số lượng")
    void testReceiveEmailsViaPop3() throws Exception {
        Thread.sleep(300); // Đảm bảo email đã được lưu

        Pop3Client client = new Pop3Client("localhost", POP3_PORT, RECIPIENT, "anypassword");
        client.connect();

        int[] stat = client.getStat();
        assertTrue(stat[0] >= 1, "Phải có ít nhất 1 email trong mailbox");

        List<Pop3Client.EmailInfo> list = client.listEmails();
        assertFalse(list.isEmpty(), "Danh sách email không được rỗng");

        client.disconnect();
    }

    @Test
    @Order(4)
    @DisplayName("Đọc nội dung email qua POP3")
    void testRetrieveEmailContent() throws Exception {
        Pop3Client client = new Pop3Client("localhost", POP3_PORT, RECIPIENT, "anypassword");
        client.connect();

        MimeMessage msg = client.retrieveEmail(1);
        assertNotNull(msg, "Email không được null");

        client.disconnect();
    }

    @Test
    @Order(5)
    @DisplayName("Xóa email qua POP3")
    void testDeleteEmail() throws Exception {
        Pop3Client client = new Pop3Client("localhost", POP3_PORT, RECIPIENT, "anypassword");
        client.connect();

        int[] statBefore = client.getStat();
        client.deleteEmail(1);
        client.disconnect(); // QUIT thực hiện xóa

        // Kết nối lại kiểm tra số lượng
        Thread.sleep(200);
        Pop3Client client2 = new Pop3Client("localhost", POP3_PORT, RECIPIENT, "anypassword");
        client2.connect();
        int[] statAfter = client2.getStat();
        client2.disconnect();

        assertEquals(statBefore[0] - 1, statAfter[0], "Số email phải giảm đi 1 sau khi xóa");
    }

    // ==================== Helpers ====================

    private static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (IOException ignored) {}
                });
        }
    }
}
