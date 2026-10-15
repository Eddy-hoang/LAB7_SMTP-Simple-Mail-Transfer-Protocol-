package com.javamail.smtp.client;

import com.javamail.shared.mime.MimeBuilder;
import com.javamail.shared.model.MimeMessage;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * SMTP Client - kết nối tới SMTP Server và gửi email.
 *
 * Hỗ trợ:
 * - Kết nối thông thường (port 25, 2525)
 * - STARTTLS (port 587 - Gmail, Outlook)
 * - AUTH LOGIN / AUTH PLAIN
 * - Gửi email MIME (plain text, HTML, attachment)
 */
public class SmtpClient {

    private static final Logger logger = Logger.getLogger(SmtpClient.class.getName());

    private final String host;
    private final int port;
    private final boolean useTLS;
    private final String username;
    private final String password;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    public SmtpClient(String host, int port, boolean useTLS, String username, String password) {
        this.host = host;
        this.port = port;
        this.useTLS = useTLS;
        this.username = username;
        this.password = password;
    }

    /** Constructor không cần xác thực (dùng với local server) */
    public SmtpClient(String host, int port) {
        this(host, port, false, null, null);
    }

    /**
     * Gửi một email qua SMTP.
     *
     * @param message email cần gửi
     * @throws IOException nếu có lỗi kết nối hoặc giao thức
     */
    public void sendEmail(MimeMessage message) throws IOException {
        connect();
        try {
            // 1. Đọc greeting từ server
            String greeting = readLine();
            logger.info("S: " + greeting);
            assertCode(greeting, "220");

            // 2. EHLO
            sendCommand("EHLO " + getLocalHostname());
            String ehloResponse = readMultilineResponse();
            logger.info("S: " + ehloResponse);

            // 3. STARTTLS nếu cần
            if (useTLS) {
                sendCommand("STARTTLS");
                String tlsResponse = readLine();
                assertCode(tlsResponse, "220");
                upgradeToTLS();
                // Gửi lại EHLO sau TLS
                sendCommand("EHLO " + getLocalHostname());
                readMultilineResponse();
            }

            // 4. AUTH nếu có username/password
            if (username != null && !username.isEmpty()) {
                authenticate();
            }

            // 5. MAIL FROM
            sendCommand("MAIL FROM:<" + message.getFrom() + ">");
            assertCode(readLine(), "250");

            // 6. RCPT TO (gửi cho tất cả người nhận)
            for (String recipient : message.getTo()) {
                sendCommand("RCPT TO:<" + recipient + ">");
                assertCode(readLine(), "250");
            }
            for (String cc : message.getCc()) {
                sendCommand("RCPT TO:<" + cc + ">");
                assertCode(readLine(), "250");
            }

            // 7. DATA
            sendCommand("DATA");
            assertCode(readLine(), "354");

            // 8. Gửi nội dung MIME
            String mimeContent = MimeBuilder.build(message);
            writer.print(mimeContent);
            // Kết thúc bằng CRLF.CRLF
            writer.print("\r\n.\r\n");
            writer.flush();
            assertCode(readLine(), "250");

            // 9. QUIT
            sendCommand("QUIT");
            readLine();

            logger.info("Email gửi thành công tới: " + message.getTo());

        } finally {
            disconnect();
        }
    }

    /**
     * Kết nối tới SMTP Server.
     */
    private void connect() throws IOException {
        if (useTLS && port == 465) {
            // SSL ngay từ đầu (SMTPS)
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = factory.createSocket(host, port);
        } else {
            socket = new Socket(host, port);
        }
        socket.setSoTimeout(30000); // 30 giây timeout

        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

        logger.info("🔌 Đã kết nối tới " + host + ":" + port);
    }

    /**
     * Nâng cấp kết nối lên TLS sau STARTTLS.
     */
    private void upgradeToTLS() throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, host, port, true);
        sslSocket.startHandshake();

        socket = sslSocket;
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

        logger.info("🔒 TLS handshake thành công");
    }


    private void authenticate() throws IOException {
        sendCommand("AUTH LOGIN");
        assertCode(readLine(), "334"); // "VXNlcm5hbWU6" = "Username:"

        // Gửi username Base64
        sendCommand(Base64.getEncoder().encodeToString(username.getBytes()));
        assertCode(readLine(), "334"); // "UGFzc3dvcmQ6" = "Password:"

        // Gửi password Base64 (tự động lọc khoảng trắng nếu người dùng copy từ Google)
        String cleanPassword = password != null ? password.replace(" ", "") : "";
        sendCommand(Base64.getEncoder().encodeToString(cleanPassword.getBytes()));
        assertCode(readLine(), "235");

        logger.info("🔐 Xác thực thành công");
    }

    private void sendCommand(String command) {
        logger.info("C: " + command);
        writer.print(command + "\r\n");
        writer.flush();
    }

    private String readLine() throws IOException {
        return reader.readLine();
    }

    /**
     * Đọc response nhiều dòng (EHLO trả về nhiều dòng 250-...).
     */
    private String readMultilineResponse() throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = readLine()) != null) {
            sb.append(line).append("\n");
            // Dòng cuối của multiline response không có dấu '-' sau mã
            if (line.length() >= 4 && line.charAt(3) == ' ') break;
        }
        return sb.toString();
    }

    /**
     * Kiểm tra mã response SMTP.
     */
    private void assertCode(String response, String expectedCode) throws IOException {
        if (response == null || !response.startsWith(expectedCode)) {
            throw new IOException("Lỗi SMTP - mong đợi " + expectedCode + " nhưng nhận: " + response);
        }
    }

    private void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    private String getLocalHostname() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "localhost"; }
    }

    // ==================== Getters ====================
    public String getHost() { return host; }
    public int getPort() { return port; }
    public boolean isUseTLS() { return useTLS; }
}
