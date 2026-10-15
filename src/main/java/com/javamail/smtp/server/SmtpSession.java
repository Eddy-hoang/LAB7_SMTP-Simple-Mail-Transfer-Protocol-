package com.javamail.smtp.server;

import com.javamail.shared.model.MimeMessage;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Xử lý 1 phiên giao tiếp SMTP với 1 client.
 * Implements Runnable để chạy trong thread riêng.
 *
 * Trạng thái SMTP: INIT → GREETED → MAIL_FROM → RCPT_TO → DATA → QUIT
 */
public class SmtpSession implements Runnable {

    private static final Logger logger = Logger.getLogger(SmtpSession.class.getName());

    // SMTP Response Codes
    private static final String READY        = "220 JavaMail SMTP Server Ready\r\n";
    private static final String OK           = "250 OK\r\n";
    private static final String START_INPUT  = "354 Start mail input; end with <CRLF>.<CRLF>\r\n";
    private static final String BYE          = "221 Bye\r\n";
    private static final String ERR_SEQUENCE = "503 Bad sequence of commands\r\n";
    private static final String ERR_UNKNOWN  = "500 Command unrecognized\r\n";
    private static final String ERR_SYNTAX   = "501 Syntax error\r\n";

    private final Socket socket;
    private final String mailboxBasePath;

    // Trạng thái phiên
    private enum State { INIT, GREETED, MAIL_FROM, RCPT_TO }
    private State state = State.INIT;

    // Dữ liệu email đang xây dựng
    private String mailFrom;
    private final List<String> rcptTo = new ArrayList<>();

    public SmtpSession(Socket socket, String mailboxBasePath) {
        this.socket = socket;
        this.mailboxBasePath = mailboxBasePath;
    }

    @Override
    public void run() {
        try (
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
        ) {
            // Gửi greeting
            writer.print(READY);
            writer.flush();

            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("C: " + line);
                String response = handleCommand(line.trim(), reader, writer);
                if (response != null) {
                    logger.info("S: " + response.trim());
                    writer.print(response);
                    writer.flush();
                }
                if (BYE.equals(response)) break;
            }
        } catch (IOException e) {
            logger.warning("Lỗi trong SMTP session: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * Xử lý một lệnh SMTP và trả về response.
     * Trả về null nếu response đã được gửi trực tiếp (trường hợp DATA).
     */
    private String handleCommand(String line, BufferedReader reader, PrintWriter writer) throws IOException {
        if (line.isEmpty()) return ERR_UNKNOWN;

        String cmd = line.length() >= 4 ? line.substring(0, 4).toUpperCase() : line.toUpperCase();

        return switch (cmd) {
            case "EHLO", "HELO" -> handleEhlo();
            case "MAIL"         -> handleMailFrom(line);
            case "RCPT"         -> handleRcptTo(line);
            case "DATA"         -> handleData(reader, writer);
            case "RSET"         -> handleRset();
            case "NOOP"         -> "250 OK\r\n";
            case "QUIT"         -> BYE;
            default             -> ERR_UNKNOWN;
        };
    }

    private String handleEhlo() {
        state = State.GREETED;
        return "250-JavaMail SMTP Server\r\n" +
               "250-SIZE 10240000\r\n" +
               "250-8BITMIME\r\n" +
               "250 HELP\r\n";
    }

    private String handleMailFrom(String line) {
        if (state != State.GREETED) return ERR_SEQUENCE;
        int start = line.indexOf('<');
        int end   = line.indexOf('>');
        if (start == -1 || end == -1) return ERR_SYNTAX;
        mailFrom = line.substring(start + 1, end);
        state = State.MAIL_FROM;
        return "250 OK - Mail from <" + mailFrom + ">\r\n";
    }

    private String handleRcptTo(String line) {
        if (state != State.MAIL_FROM && state != State.RCPT_TO) return ERR_SEQUENCE;
        int start = line.indexOf('<');
        int end   = line.indexOf('>');
        if (start == -1 || end == -1) return ERR_SYNTAX;
        String recipient = line.substring(start + 1, end);
        rcptTo.add(recipient);
        state = State.RCPT_TO;
        return "250 OK - Recipient <" + recipient + ">\r\n";
    }

    /**
     * Xử lý lệnh DATA: gửi 354, đọc nội dung, lưu file.
     * Response được gửi trực tiếp trong phương thức này.
     */
    private String handleData(BufferedReader reader, PrintWriter writer) throws IOException {
        if (state != State.RCPT_TO) return ERR_SEQUENCE;

        // Gửi 354 để báo client bắt đầu gửi nội dung
        writer.print(START_INPUT);
        writer.flush();
        logger.info("S: " + START_INPUT.trim());

        // Đọc nội dung email cho đến dòng "." đứng một mình
        StringBuilder rawEmail = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.equals(".")) break;
            // RFC 5321 dot-stuffing: dòng bắt đầu ".." → "."
            if (line.startsWith(".")) line = line.substring(1);
            rawEmail.append(line).append("\r\n");
        }

        // Lưu email vào mailbox của từng người nhận
        saveEmail(rawEmail.toString());
        resetSession();

        // Trả về response 250 sau khi lưu xong
        return "250 OK - Message accepted for delivery\r\n";
    }

    private String handleRset() {
        resetSession();
        state = State.GREETED;
        return "250 OK - Session reset\r\n";
    }

    /**
     * Lưu email vào file .eml trong mailbox của từng người nhận.
     */
    private void saveEmail(String rawEmail) {
        MailboxStorage storage = new MailboxStorage(mailboxBasePath);
        for (String recipient : rcptTo) {
            try {
                storage.save(recipient, rawEmail);
                logger.info("📬 Email đã lưu cho: " + recipient);
            } catch (IOException e) {
                logger.warning("❌ Không thể lưu email cho " + recipient + ": " + e.getMessage());
            }
        }
    }

    private void resetSession() {
        mailFrom = null;
        rcptTo.clear();
    }
}
