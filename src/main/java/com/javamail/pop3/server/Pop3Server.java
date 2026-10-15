package com.javamail.pop3.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * POP3 Server - lắng nghe kết nối từ POP3 Client.
 * Mỗi client kết nối được xử lý trong 1 thread riêng.
 *
 * Tuân thủ: RFC 1939 (POP3)
 * Port mặc định: 1100 (dùng port cao để không cần quyền admin)
 */
public class Pop3Server {

    private static final Logger logger = Logger.getLogger(Pop3Server.class.getName());

    private final int port;
    private final String mailboxBasePath;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running = false;

    public Pop3Server(int port, String mailboxBasePath) {
        this.port = port;
        this.mailboxBasePath = mailboxBasePath;
    }

    /**
     * Khởi động POP3 Server.
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        threadPool = Executors.newCachedThreadPool();
        running = true;

        logger.info("✅ POP3 Server đang chạy trên port " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("📫 Kết nối POP3 mới từ: " + clientSocket.getInetAddress());

                Pop3Session session = new Pop3Session(clientSocket, mailboxBasePath);
                threadPool.submit(session);
            } catch (IOException e) {
                if (running) {
                    logger.warning("Lỗi chấp nhận kết nối POP3: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Dừng POP3 Server.
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
            logger.info("🛑 POP3 Server đã dừng.");
        } catch (IOException e) {
            logger.warning("Lỗi khi dừng POP3 server: " + e.getMessage());
        }
    }

    public boolean isRunning() { return running; }
    public int getPort() { return port; }

    public static void main(String[] args) throws IOException {
        Pop3Server server = new Pop3Server(1100, "mailboxes");
        server.start();
    }
}
