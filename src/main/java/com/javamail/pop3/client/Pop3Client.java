package com.javamail.pop3.client;

import com.javamail.shared.mime.MimeParser;
import com.javamail.shared.model.MimeMessage;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * POP3 Client - kết nối tới POP3 Server và lấy email.
 *
 * Hỗ trợ:
 * - Liệt kê email (LIST, UIDL)
 * - Đọc email (RETR)
 * - Xóa email (DELE)
 * - Lưu email (download)
 */
public class Pop3Client {

    private static final Logger logger = Logger.getLogger(Pop3Client.class.getName());

    private final String host;
    private final int port;
    private final String username;
    private final String password;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    public Pop3Client(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    /**
     * Kết nối và xác thực với POP3 Server.
     */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(30000);

        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

        // Đọc greeting
        String greeting = readLine();
        logger.info("S: " + greeting);
        assertOk(greeting);

        // USER
        sendCommand("USER " + username);
        assertOk(readLine());

        // PASS
        sendCommand("PASS " + password);
        assertOk(readLine());

        logger.info("✅ Đã kết nối và xác thực POP3");
    }

    /**
     * Lấy thống kê mailbox (số lượng và tổng kích thước email).
     *
     * @return int[0] = số email, int[1] = tổng kích thước bytes
     */
    public int[] getStat() throws IOException {
        sendCommand("STAT");
        String response = readLine();
        assertOk(response);
        // Response: +OK 3 1234
        String[] parts = response.split("\\s+");
        return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    /**
     * Lấy danh sách email với kích thước.
     *
     * @return danh sách EmailInfo (số thứ tự + kích thước)
     */
    public List<EmailInfo> listEmails() throws IOException {
        sendCommand("LIST");
        String firstLine = readLine();
        assertOk(firstLine);

        List<EmailInfo> list = new ArrayList<>();
        String line;
        while ((line = readLine()) != null && !line.equals(".")) {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                list.add(new EmailInfo(
                        Integer.parseInt(parts[0]),
                        Long.parseLong(parts[1])
                ));
            }
        }
        return list;
    }

    /**
     * Tải nội dung một email từ server.
     *
     * @param messageNumber số thứ tự email (1-based)
     * @return MimeMessage đã parse
     */
    public MimeMessage retrieveEmail(int messageNumber) throws IOException {
        sendCommand("RETR " + messageNumber);
        String firstLine = readLine();
        assertOk(firstLine);

        // Đọc nội dung email cho đến dòng "."
        StringBuilder rawEmail = new StringBuilder();
        String line;
        while ((line = readLine()) != null && !line.equals(".")) {
            // Unstuff: dòng bắt đầu bằng ".." → "."
            if (line.startsWith("..")) line = line.substring(1);
            rawEmail.append(line).append("\r\n");
        }

        return MimeParser.parse(rawEmail.toString());
    }

    /**
     * Tải raw content một email (chưa parse).
     *
     * @param messageNumber số thứ tự email (1-based)
     * @return raw email string
     */
    public String retrieveRaw(int messageNumber) throws IOException {
        sendCommand("RETR " + messageNumber);
        String firstLine = readLine();
        assertOk(firstLine);

        StringBuilder raw = new StringBuilder();
        String line;
        while ((line = readLine()) != null && !line.equals(".")) {
            if (line.startsWith("..")) line = line.substring(1);
            raw.append(line).append("\r\n");
        }
        return raw.toString();
    }

    /**
     * Đánh dấu xóa email trên server (thực hiện khi QUIT).
     *
     * @param messageNumber số thứ tự email (1-based)
     */
    public void deleteEmail(int messageNumber) throws IOException {
        sendCommand("DELE " + messageNumber);
        assertOk(readLine());
        logger.info("🗑️ Đã đánh dấu xóa email #" + messageNumber);
    }

    /**
     * Hủy tất cả lệnh DELE trong phiên này (RSET).
     */
    public void resetDeletions() throws IOException {
        sendCommand("RSET");
        assertOk(readLine());
        logger.info("↩️ Đã hủy tất cả đánh dấu xóa");
    }

    /**
     * Lấy unique ID của từng email (UIDL).
     */
    public List<UidlEntry> getUidl() throws IOException {
        sendCommand("UIDL");
        String firstLine = readLine();
        assertOk(firstLine);

        List<UidlEntry> list = new ArrayList<>();
        String line;
        while ((line = readLine()) != null && !line.equals(".")) {
            String[] parts = line.split("\\s+", 2);
            if (parts.length >= 2) {
                list.add(new UidlEntry(Integer.parseInt(parts[0]), parts[1]));
            }
        }
        return list;
    }

    /**
     * Ngắt kết nối và thực hiện xóa email đã đánh dấu.
     */
    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                sendCommand("QUIT");
                try { readLine(); } catch (Exception ignored) {}
                socket.close();
                logger.info("🔌 Đã ngắt kết nối POP3");
            }
        } catch (IOException ignored) {}
    }

    private void sendCommand(String command) {
        logger.info("C: " + command);
        writer.println(command);
        writer.flush();
    }

    private String readLine() throws IOException {
        return reader.readLine();
    }

    private void assertOk(String response) throws IOException {
        if (response == null || !response.startsWith("+OK")) {
            throw new IOException("POP3 Error: " + response);
        }
    }

    // ==================== Inner Classes ====================

    public static class EmailInfo {
        public final int number;
        public final long size;

        public EmailInfo(int number, long size) {
            this.number = number;
            this.size = size;
        }

        public String getReadableSize() {
            if (size < 1024) return size + " B";
            else if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            else return String.format("%.1f MB", size / (1024.0 * 1024));
        }
    }

    public static class UidlEntry {
        public final int number;
        public final String uid;

        public UidlEntry(int number, String uid) {
            this.number = number;
            this.uid = uid;
        }
    }

    // Getters
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
}
