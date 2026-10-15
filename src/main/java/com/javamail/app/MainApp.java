package com.javamail.app;

import com.javamail.pop3.server.Pop3Server;
import com.javamail.pop3.ui.Pop3ClientWindow;
import com.javamail.smtp.server.SmtpServer;
import com.javamail.smtp.ui.SmtpClientWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Ứng dụng JavaFX chính - JavaMail
 * Quản lý SMTP Server, POP3 Server, và cung cấp launcher cho Client UI.
 */
public class MainApp extends Application {

    private static final Logger logger = Logger.getLogger(MainApp.class.getName());

    private static final String MAILBOX_PATH = "mailboxes";
    private static final int SMTP_PORT = 2525;
    private static final int POP3_PORT = 1100;

    private SmtpServer smtpServer;
    private Pop3Server pop3Server;
    private Thread smtpThread;
    private Thread pop3Thread;

    // UI elements
    private Label smtpStatusLabel;
    private Label pop3StatusLabel;
    private Button smtpToggleBtn;
    private Button pop3ToggleBtn;
    private TextArea logArea;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("JavaMail — SMTP & POP3 Email System");

        VBox root = buildUI();
        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(750);
        primaryStage.setMinHeight(550);
        primaryStage.show();
    }

    private VBox buildUI() {
        VBox root = new VBox(0);
        root.getStyleClass().add("root-container");

        // Header
        root.getChildren().add(buildHeader());

        // Content
        HBox content = new HBox(20);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("content-area");
        VBox.setVgrow(content, Priority.ALWAYS);

        // Left: Server controls
        content.getChildren().add(buildServerPanel());

        // Right: Log area
        VBox logPanel = buildLogPanel();
        HBox.setHgrow(logPanel, Priority.ALWAYS);
        content.getChildren().add(logPanel);

        root.getChildren().add(content);

        // Bottom: Client launcher buttons
        root.getChildren().add(buildClientLauncher());

        return root;
    }

    private HBox buildHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("header");
        header.setPadding(new Insets(20, 30, 20, 30));
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label title = new Label("✉ JavaMail");
        title.getStyleClass().add("header-title");

        Label subtitle = new Label("SMTP & POP3 Email System — Java Implementation");
        subtitle.getStyleClass().add("header-subtitle");

        titleBox.getChildren().addAll(title, subtitle);
        header.getChildren().add(titleBox);
        return header;
    }

    private VBox buildServerPanel() {
        VBox panel = new VBox(15);
        panel.getStyleClass().add("server-panel");
        panel.setPrefWidth(250);

        Label title = new Label("⚙ Server Control");
        title.getStyleClass().add("panel-title");
        panel.getChildren().add(title);

        // SMTP Server card
        panel.getChildren().add(buildServerCard(
                "SMTP Server",
                "Port " + SMTP_PORT,
                "📤",
                true
        ));

        // POP3 Server card
        panel.getChildren().add(buildServerCard(
                "POP3 Server",
                "Port " + POP3_PORT,
                "📥",
                false
        ));

        return panel;
    }

    private VBox buildServerCard(String name, String portInfo, String icon, boolean isSmtp) {
        VBox card = new VBox(10);
        card.getStyleClass().add("server-card");

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font(20));
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("card-title");
        titleRow.getChildren().addAll(iconLabel, nameLabel);

        Label portLabel = new Label(portInfo);
        portLabel.getStyleClass().add("card-port");

        Label statusLabel = new Label("● Stopped");
        statusLabel.getStyleClass().add("status-stopped");

        Button toggleBtn = new Button("▶ Start");
        toggleBtn.getStyleClass().add("btn-start");
        toggleBtn.setMaxWidth(Double.MAX_VALUE);

        if (isSmtp) {
            smtpStatusLabel = statusLabel;
            smtpToggleBtn = toggleBtn;
            toggleBtn.setOnAction(e -> toggleSmtpServer());
        } else {
            pop3StatusLabel = statusLabel;
            pop3ToggleBtn = toggleBtn;
            toggleBtn.setOnAction(e -> togglePop3Server());
        }

        card.getChildren().addAll(titleRow, portLabel, statusLabel, toggleBtn);
        return card;
    }

    private VBox buildLogPanel() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("log-panel");

        HBox titleRow = new HBox();
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("📋 Server Log");
        title.getStyleClass().add("panel-title");
        HBox.setHgrow(title, Priority.ALWAYS);
        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("btn-clear");
        clearBtn.setOnAction(e -> logArea.clear());
        titleRow.getChildren().addAll(title, clearBtn);

        logArea = new TextArea();
        logArea.getStyleClass().add("log-area");
        logArea.setEditable(false);
        logArea.setWrapText(true);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        panel.getChildren().addAll(titleRow, logArea);
        return panel;
    }

    private HBox buildClientLauncher() {
        HBox bar = new HBox(15);
        bar.getStyleClass().add("client-bar");
        bar.setPadding(new Insets(15, 30, 15, 30));
        bar.setAlignment(Pos.CENTER);

        Label label = new Label("Launch Client:");
        label.getStyleClass().add("client-bar-label");

        Button smtpClientBtn = new Button("📤 Open SMTP Client");
        smtpClientBtn.getStyleClass().add("btn-client-smtp");
        smtpClientBtn.setOnAction(e -> openSmtpClientUI());

        Button pop3ClientBtn = new Button("📥 Open POP3 Client");
        pop3ClientBtn.getStyleClass().add("btn-client-pop3");
        pop3ClientBtn.setOnAction(e -> openPop3ClientUI());

        bar.getChildren().addAll(label, smtpClientBtn, pop3ClientBtn);
        return bar;
    }

    // ==================== Server Control ====================

    private void toggleSmtpServer() {
        if (smtpServer != null && smtpServer.isRunning()) {
            smtpServer.stop();
            smtpThread.interrupt();
            updateServerStatus(smtpStatusLabel, smtpToggleBtn, false, "SMTP");
        } else {
            smtpServer = new SmtpServer(SMTP_PORT, MAILBOX_PATH);
            smtpThread = new Thread(() -> {
                try {
                    smtpServer.start();
                } catch (IOException e) {
                    Platform.runLater(() -> appendLog("❌ SMTP Error: " + e.getMessage()));
                }
            });
            smtpThread.setDaemon(true);
            smtpThread.start();
            updateServerStatus(smtpStatusLabel, smtpToggleBtn, true, "SMTP");
            appendLog("▶ SMTP Server started on port " + SMTP_PORT);
        }
    }

    private void togglePop3Server() {
        if (pop3Server != null && pop3Server.isRunning()) {
            pop3Server.stop();
            pop3Thread.interrupt();
            updateServerStatus(pop3StatusLabel, pop3ToggleBtn, false, "POP3");
        } else {
            pop3Server = new Pop3Server(POP3_PORT, MAILBOX_PATH);
            pop3Thread = new Thread(() -> {
                try {
                    pop3Server.start();
                } catch (IOException e) {
                    Platform.runLater(() -> appendLog("❌ POP3 Error: " + e.getMessage()));
                }
            });
            pop3Thread.setDaemon(true);
            pop3Thread.start();
            updateServerStatus(pop3StatusLabel, pop3ToggleBtn, true, "POP3");
            appendLog("▶ POP3 Server started on port " + POP3_PORT);
        }
    }

    private void updateServerStatus(Label statusLabel, Button toggleBtn, boolean running, String name) {
        Platform.runLater(() -> {
            if (running) {
                statusLabel.setText("● Running");
                statusLabel.getStyleClass().removeAll("status-stopped");
                statusLabel.getStyleClass().add("status-running");
                toggleBtn.setText("■ Stop");
                toggleBtn.getStyleClass().removeAll("btn-start");
                toggleBtn.getStyleClass().add("btn-stop");
            } else {
                statusLabel.setText("● Stopped");
                statusLabel.getStyleClass().removeAll("status-running");
                statusLabel.getStyleClass().add("status-stopped");
                toggleBtn.setText("▶ Start");
                toggleBtn.getStyleClass().removeAll("btn-stop");
                toggleBtn.getStyleClass().add("btn-start");
                appendLog("■ " + name + " Server stopped");
            }
        });
    }

    private void openSmtpClientUI() {
        appendLog("📤 Opening SMTP Client...");
        SmtpClientWindow smtpClientWindow = new SmtpClientWindow();
        smtpClientWindow.show();
    }

    private void openPop3ClientUI() {
        appendLog("📥 Opening POP3 Client...");
        Pop3ClientWindow pop3ClientWindow = new Pop3ClientWindow();
        pop3ClientWindow.show();
    }

    private void appendLog(String message) {
        Platform.runLater(() -> {
            logArea.appendText("[" + java.time.LocalTime.now().toString().substring(0, 8) + "] " + message + "\n");
        });
    }

    @Override
    public void stop() {
        if (smtpServer != null) smtpServer.stop();
        if (pop3Server != null) pop3Server.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
