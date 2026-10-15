package com.javamail.pop3.ui;

import com.javamail.pop3.client.Pop3Client;
import com.javamail.shared.model.AttachmentPart;
import com.javamail.shared.model.MimeMessage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Giao diện POP3 Client - Inbox xem và quản lý email.
 * Layout: Sidebar (danh sách) | Main Panel (nội dung email)
 */
public class Pop3ClientWindow {

    private Stage stage;
    private Pop3Client pop3Client;

    // Config
    private String pop3Host = "localhost";
    private int    pop3Port = 1100;
    private String username = "user@localhost";
    private String password = "";

    // UI
    private ListView<EmailListItem> emailListView;
    private Label senderLabel;
    private Label subjectLabel;
    private Label dateLabel;
    private Label attachmentCountLabel;
    private WebView bodyWebView;
    private TextArea bodyTextArea;
    private VBox attachmentPanel;
    private Label statusLabel;
    private Button refreshBtn;
    private Button deleteBtn;

    private List<MimeMessage> loadedEmails;

    public void show() {
        stage = new Stage();
        stage.setTitle("📥 POP3 Client — Inbox");

        BorderPane root = buildUI();
        Scene scene = new Scene(root, 1000, 680);
        scene.getStylesheets().add(
                getClass().getResource("/css/pop3_client.css").toExternalForm());

        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(550);
        stage.show();
    }

    private BorderPane buildUI() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("inbox-root");

        // Top: Header + Connection bar
        root.setTop(buildTopBar());

        // Left: Email list
        root.setLeft(buildEmailList());

        // Center: Email viewer
        root.setCenter(buildEmailViewer());

        // Bottom: Status bar
        root.setBottom(buildStatusBar());

        return root;
    }

    // ===== Top Bar =====
    private VBox buildTopBar() {
        VBox top = new VBox(0);

        // Header
        HBox header = new HBox();
        header.getStyleClass().add("inbox-header");
        header.setPadding(new Insets(14, 24, 14, 24));
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("📥  Inbox");
        title.getStyleClass().add("inbox-title");
        header.getChildren().add(title);

        // Connection bar
        HBox connBar = new HBox(10);
        connBar.getStyleClass().add("conn-bar");
        connBar.setPadding(new Insets(8, 16, 8, 16));
        connBar.setAlignment(Pos.CENTER_LEFT);

        Label hostLbl = new Label("Host:");
        hostLbl.getStyleClass().add("conn-label");
        TextField hostField = new TextField(pop3Host);
        hostField.setPrefWidth(120);
        hostField.getStyleClass().add("conn-field");
        hostField.textProperty().addListener((o, ov, nv) -> pop3Host = nv);

        Label portLbl = new Label("Port:");
        portLbl.getStyleClass().add("conn-label");
        TextField portField = new TextField(String.valueOf(pop3Port));
        portField.setPrefWidth(55);
        portField.getStyleClass().add("conn-field");
        portField.textProperty().addListener((o, ov, nv) -> {
            try { pop3Port = Integer.parseInt(nv); } catch (NumberFormatException ignored) {}
        });

        Label userLbl = new Label("User:");
        userLbl.getStyleClass().add("conn-label");
        TextField userField = new TextField(username);
        userField.setPrefWidth(140);
        userField.getStyleClass().add("conn-field");
        userField.textProperty().addListener((o, ov, nv) -> username = nv);

        Label passLbl = new Label("Pass:");
        passLbl.getStyleClass().add("conn-label");
        PasswordField passField = new PasswordField();
        passField.setPrefWidth(100);
        passField.getStyleClass().add("conn-field");
        passField.textProperty().addListener((o, ov, nv) -> password = nv);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        refreshBtn = new Button("🔄 Refresh");
        refreshBtn.getStyleClass().add("btn-refresh");
        refreshBtn.setOnAction(e -> fetchEmails());

        connBar.getChildren().addAll(
                hostLbl, hostField, portLbl, portField,
                userLbl, userField, passLbl, passField,
                spacer, refreshBtn
        );

        top.getChildren().addAll(header, connBar);
        return top;
    }

    // ===== Email List Sidebar =====
    private VBox buildEmailList() {
        VBox sidebar = new VBox(0);
        sidebar.getStyleClass().add("email-sidebar");
        sidebar.setPrefWidth(280);

        Label listTitle = new Label("Messages");
        listTitle.getStyleClass().add("sidebar-title");
        listTitle.setPadding(new Insets(10, 14, 6, 14));

        emailListView = new ListView<>();
        emailListView.getStyleClass().add("email-list");
        VBox.setVgrow(emailListView, Priority.ALWAYS);

        emailListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(EmailListItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setGraphic(buildEmailCell(item));
                    setText(null);
                }
            }
        });

        emailListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) displayEmail(newVal);
                }
        );

        sidebar.getChildren().addAll(listTitle, emailListView);
        return sidebar;
    }

    private VBox buildEmailCell(EmailListItem item) {
        VBox cell = new VBox(3);
        cell.getStyleClass().add("email-cell");
        cell.setPadding(new Insets(8, 12, 8, 12));

        HBox top = new HBox();
        Label fromLbl = new Label(item.from);
        fromLbl.getStyleClass().add("cell-from");
        HBox.setHgrow(fromLbl, Priority.ALWAYS);
        Label sizeLbl = new Label(item.size);
        sizeLbl.getStyleClass().add("cell-size");
        top.getChildren().addAll(fromLbl, sizeLbl);

        Label subjectLbl = new Label(item.subject);
        subjectLbl.getStyleClass().add("cell-subject");

        Label dateLbl = new Label(item.date);
        dateLbl.getStyleClass().add("cell-date");

        cell.getChildren().addAll(top, subjectLbl, dateLbl);
        return cell;
    }

    // ===== Email Viewer =====
    private VBox buildEmailViewer() {
        VBox viewer = new VBox(0);
        viewer.getStyleClass().add("email-viewer");

        // Email header info
        VBox headerBox = new VBox(6);
        headerBox.getStyleClass().add("email-header-box");
        headerBox.setPadding(new Insets(16, 20, 12, 20));

        senderLabel  = new Label("Select an email to read");
        senderLabel.getStyleClass().add("viewer-sender");
        subjectLabel = new Label("");
        subjectLabel.getStyleClass().add("viewer-subject");
        dateLabel    = new Label("");
        dateLabel.getStyleClass().add("viewer-date");

        // Toolbar: delete, save
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        deleteBtn = new Button("🗑 Delete");
        deleteBtn.getStyleClass().add("btn-delete");
        deleteBtn.setDisable(true);
        deleteBtn.setOnAction(e -> deleteSelected());

        Button saveBtn = new Button("💾 Save .eml");
        saveBtn.getStyleClass().add("btn-save");
        saveBtn.setOnAction(e -> saveEmailToFile());

        toolbar.getChildren().addAll(deleteBtn, saveBtn);

        headerBox.getChildren().addAll(senderLabel, subjectLabel, dateLabel, toolbar);
        viewer.getChildren().add(headerBox);

        new Separator();

        // Attachment panel
        attachmentPanel = new VBox(4);
        attachmentPanel.getStyleClass().add("attachment-panel");
        attachmentPanel.setPadding(new Insets(8, 20, 8, 20));
        attachmentCountLabel = new Label();
        attachmentCountLabel.getStyleClass().add("attach-count");
        attachmentPanel.getChildren().add(attachmentCountLabel);
        attachmentPanel.setVisible(false);
        viewer.getChildren().add(attachmentPanel);

        // Body: TabPane (plain text vs html)
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("body-tabs");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Plain text tab
        bodyTextArea = new TextArea();
        bodyTextArea.getStyleClass().add("body-text");
        bodyTextArea.setEditable(false);
        bodyTextArea.setWrapText(true);
        Tab plainTab = new Tab("Plain Text", bodyTextArea);
        plainTab.getStyleClass().add("body-tab");

        // HTML tab
        bodyWebView = new WebView();
        bodyWebView.getStyleClass().add("body-html");
        Tab htmlTab = new Tab("HTML", bodyWebView);
        htmlTab.getStyleClass().add("body-tab");

        tabPane.getTabs().addAll(plainTab, htmlTab);
        viewer.getChildren().add(tabPane);

        return viewer;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(6, 16, 6, 16));
        bar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Ready — Click 'Refresh' to fetch emails");
        statusLabel.getStyleClass().add("status-text");
        bar.getChildren().add(statusLabel);
        return bar;
    }

    // ==================== Logic ====================

    private void fetchEmails() {
        refreshBtn.setDisable(true);
        setStatus("⏳ Đang kết nối tới " + pop3Host + ":" + pop3Port + "...");
        emailListView.getItems().clear();

        Thread t = new Thread(() -> {
            Pop3Client client = new Pop3Client(pop3Host, pop3Port, username, password);
            try {
                client.connect();
                int[] stat = client.getStat();
                List<Pop3Client.EmailInfo> infos = client.listEmails();

                for (Pop3Client.EmailInfo info : infos) {
                    MimeMessage msg = client.retrieveEmail(info.number);
                    // Đính kèm số thứ tự để xóa sau này
                    msg.setMessageId(String.valueOf(info.number));

                    EmailListItem item = new EmailListItem(
                            info.number,
                            msg.getFrom() != null ? msg.getFrom() : "(no sender)",
                            msg.getSubject() != null ? msg.getSubject() : "(no subject)",
                            msg.getDate() != null
                                    ? msg.getDate().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                                    : "",
                            info.getReadableSize(),
                            msg
                    );

                    Platform.runLater(() -> emailListView.getItems().add(item));
                }

                client.disconnect();
                Platform.runLater(() -> {
                    setStatus("✅ " + stat[0] + " email(s) | Total: " + formatSize(stat[1]));
                    refreshBtn.setDisable(false);
                });

            } catch (IOException e) {
                Platform.runLater(() -> {
                    setStatus("❌ Lỗi kết nối: " + e.getMessage());
                    refreshBtn.setDisable(false);
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void displayEmail(EmailListItem item) {
        MimeMessage msg = item.message;
        deleteBtn.setDisable(false);

        senderLabel.setText("From: " + (msg.getFrom() != null ? msg.getFrom() : "(unknown)"));
        subjectLabel.setText(msg.getSubject() != null ? msg.getSubject() : "(no subject)");
        dateLabel.setText(msg.getDate() != null
                ? msg.getDate().format(DateTimeFormatter.ofPattern("EEEE, MMMM dd yyyy — HH:mm"))
                : "");

        // Body
        bodyTextArea.setText(msg.getBodyText() != null ? msg.getBodyText() : "");
        if (msg.getBodyHtml() != null && !msg.getBodyHtml().isEmpty()) {
            bodyWebView.getEngine().loadContent(msg.getBodyHtml());
        } else {
            bodyWebView.getEngine().loadContent("<pre style='font-family:sans-serif;padding:16px'>"
                    + escapeHtml(msg.getBodyText() != null ? msg.getBodyText() : "") + "</pre>");
        }

        // Attachments
        attachmentPanel.getChildren().clear();
        if (msg.hasAttachments()) {
            attachmentPanel.setVisible(true);
            attachmentCountLabel.setText("📎 " + msg.getAttachments().size() + " attachment(s)");
            attachmentPanel.getChildren().add(attachmentCountLabel);

            for (AttachmentPart att : msg.getAttachments()) {
                HBox chip = new HBox(8);
                chip.setAlignment(Pos.CENTER_LEFT);
                chip.getStyleClass().add("attachment-chip");
                Label nameLabel = new Label("📄 " + att.getFilename() + " (" + att.getReadableSize() + ")");
                nameLabel.getStyleClass().add("attachment-name");
                Button dlBtn = new Button("⬇ Download");
                dlBtn.getStyleClass().add("btn-download");
                dlBtn.setOnAction(e -> downloadAttachment(att));
                chip.getChildren().addAll(nameLabel, dlBtn);
                attachmentPanel.getChildren().add(chip);
            }
        } else {
            attachmentPanel.setVisible(false);
        }
    }

    private void deleteSelected() {
        EmailListItem selected = emailListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Xóa email này khỏi server?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Xác nhận xóa");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                Thread t = new Thread(() -> {
                    Pop3Client client = new Pop3Client(pop3Host, pop3Port, username, password);
                    try {
                        client.connect();
                        client.deleteEmail(selected.number);
                        client.disconnect();
                        Platform.runLater(() -> {
                            emailListView.getItems().remove(selected);
                            clearViewer();
                            setStatus("🗑 Email #" + selected.number + " đã xóa.");
                        });
                    } catch (IOException e) {
                        Platform.runLater(() -> setStatus("❌ Lỗi xóa: " + e.getMessage()));
                    }
                });
                t.setDaemon(true);
                t.start();
            }
        });
    }

    private void downloadAttachment(AttachmentPart att) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Chọn thư mục lưu file");
        File dir = chooser.showDialog(stage);
        if (dir == null || att.getData() == null) return;

        File out = new File(dir, att.getFilename());
        try {
            Files.write(out.toPath(), att.getData());
            setStatus("💾 Đã lưu: " + out.getAbsolutePath());
        } catch (IOException e) {
            setStatus("❌ Lỗi lưu file: " + e.getMessage());
        }
    }

    private void saveEmailToFile() {
        EmailListItem selected = emailListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Chọn thư mục lưu .eml");
        File dir = chooser.showDialog(stage);
        if (dir == null) return;

        File out = new File(dir, "email_" + selected.number + ".eml");
        try {
            String raw = selected.message.getRawContent();
            if (raw == null) raw = "(no content)";
            Files.writeString(out.toPath(), raw);
            setStatus("💾 Đã lưu: " + out.getAbsolutePath());
        } catch (IOException e) {
            setStatus("❌ Lỗi lưu file: " + e.getMessage());
        }
    }

    private void clearViewer() {
        senderLabel.setText("Select an email to read");
        subjectLabel.setText("");
        dateLabel.setText("");
        bodyTextArea.clear();
        bodyWebView.getEngine().loadContent("");
        attachmentPanel.setVisible(false);
        deleteBtn.setDisable(true);
    }

    private void setStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ==================== Inner Classes ====================

    public static class EmailListItem {
        public final int number;
        public final String from;
        public final String subject;
        public final String date;
        public final String size;
        public final MimeMessage message;

        public EmailListItem(int number, String from, String subject,
                             String date, String size, MimeMessage message) {
            this.number  = number;
            this.from    = from;
            this.subject = subject;
            this.date    = date;
            this.size    = size;
            this.message = message;
        }
    }
}
