package com.javamail.smtp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * SMTP Server - lắng nghe kết nối từ SMTP Client.
 * Mỗi client kết nối được xử lý trong 1 thread riêng.
 *
 * Tuân thủ: RFC 5321 (SMTP)
 * Port mặc định: 2525 (dùng port cao để không cần quyền admin)
 */
public class SmtpServer {

    private static final Logger logger = Logger.getLogger(SmtpServer.class.getName());

    private final int port;
    private final String mailboxBasePath;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running = false;

    public SmtpServer(int port, String mailboxBasePath) {
        this.port = port;
        this.mailboxBasePath = mailboxBasePath;
    }

    /**
     * Khởi động SMTP Server.
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        threadPool = Executors.newCachedThreadPool();
        running = true;

        logger.info("SMTP Server đang chạy trên port " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("Kết nối mới từ: " + clientSocket.getInetAddress());

                SmtpSession session = new SmtpSession(clientSocket, mailboxBasePath);
                threadPool.submit(session);
            } catch (IOException e) {
                if (running) {
                    logger.warning("Lỗi chấp nhận kết nối: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Dừng SMTP Server.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (threadPool != null) {
                threadPool.shutdownNow();
            }
            logger.info("SMTP Server đã dừng.");
        } catch (IOException e) {
            logger.warning("Lỗi khi dừng server: " + e.getMessage());
        }
    }

    public boolean isRunning() { return running; }
    public int getPort() { return port; }

    public static void main(String[] args) throws IOException {
        SmtpServer server = new SmtpServer(2525, "mailboxes");
        server.start();
    }
}
