package com.javamail.pop3.server;

import com.javamail.smtp.server.MailboxStorage;

import java.io.*;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Xử lý 1 phiên giao tiếp POP3 với 1 client.
 * Implements Runnable để chạy trong thread riêng.
 *
 * Trạng thái POP3 (RFC 1939):
 * AUTHORIZATION → TRANSACTION → UPDATE
 */
public class Pop3Session implements Runnable {

    private static final Logger logger = Logger.getLogger(Pop3Session.class.getName());

    // POP3 Responses
    private static final String OK        = "+OK";
    private static final String ERR       = "-ERR";

    private final Socket socket;
    private final String mailboxBasePath;

    // Trạng thái phiên
    private enum State { AUTHORIZATION, TRANSACTION, UPDATE }
    private State state = State.AUTHORIZATION;

    private String currentUser = null;
    private List<Path> emails = new ArrayList<>();
    private Set<Integer> deletedIndexes = new HashSet<>(); // 0-based index

    public Pop3Session(Socket socket, String mailboxBasePath) {
        this.socket = socket;
        this.mailboxBasePath = mailboxBasePath;
    }

    @Override
    public void run() {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            // Gửi greeting
            send(writer, OK + " JavaMail POP3 Server Ready");

            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("C: " + line);
                String response = handleCommand(line.trim());
                logger.info("S: " + response);

                // Multi-line response đã được xử lý bên trong handleCommand
                // Ở đây chỉ gửi dòng đầu
                writer.println(response);
                writer.flush();

                if (response.startsWith(OK + " Bye")) break;
            }

        } catch (IOException e) {
            logger.warning("Lỗi trong POP3 session: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * Xử lý một lệnh POP3 và trả về response.
     */
    private String handleCommand(String line) {
        if (line.isEmpty()) return ERR + " Empty command";

        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toUpperCase();
        String arg = parts.length > 1 ? parts[1] : "";

        return switch (cmd) {
            case "USER" -> handleUser(arg);
            case "PASS" -> handlePass(arg);
            case "STAT" -> handleStat();
            case "LIST" -> handleList(arg);
            case "RETR" -> handleRetr(arg);
            case "DELE" -> handleDele(arg);
            case "NOOP" -> OK + " Nothing happened";
            case "RSET" -> handleRset();
            case "UIDL" -> handleUidl(arg);
            case "QUIT" -> handleQuit();
            default     -> ERR + " Unknown command";
        };
    }

    // ==================== AUTHORIZATION state ====================

    private String handleUser(String username) {
        if (state != State.AUTHORIZATION) return ERR + " Wrong state";
        if (username.isEmpty()) return ERR + " Missing username";
        currentUser = username;
        return OK + " User " + username + " accepted";
    }

    private String handlePass(String password) {
        if (state != State.AUTHORIZATION) return ERR + " Wrong state";
        if (currentUser == null) return ERR + " USER command required first";

        // Server không xác thực — chấp nhận mọi mật khẩu
        try {
            MailboxStorage storage = new MailboxStorage(mailboxBasePath);
            emails = storage.listEmails(currentUser);
            state = State.TRANSACTION;
            return OK + " Mailbox open, " + emails.size() + " messages";
        } catch (IOException e) {
            return ERR + " Mailbox error: " + e.getMessage();
        }
    }

    // ==================== TRANSACTION state ====================

    private String handleStat() {
        if (state != State.TRANSACTION) return ERR + " Not in TRANSACTION state";
        int count = 0;
        long size = 0;
        MailboxStorage storage = new MailboxStorage(mailboxBasePath);
        for (int i = 0; i < emails.size(); i++) {
            if (!deletedIndexes.contains(i)) {
                count++;
                try { size += java.nio.file.Files.size(emails.get(i)); }
                catch (IOException ignored) {}
            }
        }
        return OK + " " + count + " " + size;
    }

    private String handleList(String arg) {
        if (state != State.TRANSACTION) return ERR + " Not in TRANSACTION state";

        if (!arg.isEmpty()) {
            // LIST <n> — thông tin 1 email
            try {
                int idx = Integer.parseInt(arg) - 1; // POP3 là 1-based
                if (idx < 0 || idx >= emails.size()) return ERR + " No such message";
                if (deletedIndexes.contains(idx)) return ERR + " Message deleted";
                long size = java.nio.file.Files.size(emails.get(idx));
                return OK + " " + (idx + 1) + " " + size;
            } catch (Exception e) {
                return ERR + " Invalid argument";
            }
        }

        // LIST — tất cả email (multi-line)
        StringBuilder sb = new StringBuilder(OK + " " + emails.size() + " messages\r\n");
        for (int i = 0; i < emails.size(); i++) {
            if (!deletedIndexes.contains(i)) {
                try {
                    long size = java.nio.file.Files.size(emails.get(i));
                    sb.append(i + 1).append(" ").append(size).append("\r\n");
                } catch (IOException ignored) {}
            }
        }
        sb.append(".");
        return sb.toString();
    }

    private String handleRetr(String arg) {
        if (state != State.TRANSACTION) return ERR + " Not in TRANSACTION state";
        try {
            int idx = Integer.parseInt(arg.trim()) - 1;
            if (idx < 0 || idx >= emails.size()) return ERR + " No such message";
            if (deletedIndexes.contains(idx)) return ERR + " Message deleted";

            MailboxStorage storage = new MailboxStorage(mailboxBasePath);
            String content = storage.readEmail(emails.get(idx));
            long size = java.nio.file.Files.size(emails.get(idx));

            return OK + " " + size + " octets\r\n" + content + "\r\n.";
        } catch (Exception e) {
            return ERR + " Error reading message: " + e.getMessage();
        }
    }

    private String handleDele(String arg) {
        if (state != State.TRANSACTION) return ERR + " Not in TRANSACTION state";
        try {
            int idx = Integer.parseInt(arg.trim()) - 1;
            if (idx < 0 || idx >= emails.size()) return ERR + " No such message";
            if (deletedIndexes.contains(idx)) return ERR + " Already deleted";

            deletedIndexes.add(idx);
            return OK + " Message " + (idx + 1) + " marked for deletion";
        } catch (NumberFormatException e) {
            return ERR + " Invalid message number";
        }
    }

    private String handleRset() {
        if (state != State.TRANSACTION) return ERR + " Not in TRANSACTION state";
        deletedIndexes.clear();
        return OK + " Maildrop has " + emails.size() + " messages";
    }

    private String handleUidl(String arg) {
        if (state != State.TRANSACTION) return ERR + " Not in TRANSACTION state";

        if (!arg.isEmpty()) {
            try {
                int idx = Integer.parseInt(arg.trim()) - 1;
                if (idx < 0 || idx >= emails.size()) return ERR + " No such message";
                String uid = emails.get(idx).getFileName().toString().replace(".eml", "");
                return OK + " " + (idx + 1) + " " + uid;
            } catch (Exception e) {
                return ERR + " Invalid argument";
            }
        }

        StringBuilder sb = new StringBuilder(OK + "\r\n");
        for (int i = 0; i < emails.size(); i++) {
            if (!deletedIndexes.contains(i)) {
                String uid = emails.get(i).getFileName().toString().replace(".eml", "");
                sb.append(i + 1).append(" ").append(uid).append("\r\n");
            }
        }
        sb.append(".");
        return sb.toString();
    }

    // ==================== UPDATE state ====================

    private String handleQuit() {
        // Xóa các email đã đánh dấu
        if (state == State.TRANSACTION) {
            MailboxStorage storage = new MailboxStorage(mailboxBasePath);
            for (int idx : deletedIndexes) {
                try {
                    storage.deleteEmail(emails.get(idx));
                } catch (IOException e) {
                    logger.warning("Không thể xóa email: " + e.getMessage());
                }
            }
        }
        state = State.UPDATE;
        return OK + " Bye";
    }

    private void send(PrintWriter writer, String message) {
        writer.println(message);
        writer.flush();
    }
}
