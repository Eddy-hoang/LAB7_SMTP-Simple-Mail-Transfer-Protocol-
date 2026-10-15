package com.javamail.smtp.ui;

import com.javamail.shared.model.AttachmentPart;
import com.javamail.shared.model.MimeMessage;
import com.javamail.smtp.client.SmtpClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Giao diện soạn thảo và gửi email qua SMTP.
 * Hỗ trợ: To, CC, BCC, Subject, Body (plain/HTML), Attachments.
 */
public class SmtpClientWindow {

    private Stage stage;

    // Config
    private String smtpHost = "localhost";
    private int    smtpPort = 2525;
    private boolean useTLS  = false;
    private String username = "";
    private String password = "";

    // Form fields
    private TextField fromField;
    private TextField toField;
    private TextField ccField;
    private TextField bccField;
    private TextField subjectField;
    private TextArea  bodyArea;
    private ToggleButton htmlToggle;
    private Label statusLabel;
    private VBox attachmentList;

    private final List<AttachmentPart> attachments = new ArrayList<>();

    public void show() {
        stage = new Stage();
        stage.setTitle("📤 SMTP Client — Compose Email");

        VBox root = buildUI();
        Scene scene = new Scene(root, 720, 750);
        scene.getStylesheets().add(
                getClass().getResource("/css/smtp_client.css").toExternalForm());

        stage.setScene(scene);
        stage.setMinWidth(680);
        stage.setMinHeight(650);
        stage.show();
    }

    private VBox buildUI() {
        VBox root = new VBox(0);
        root.getStyleClass().add("compose-root");

        // ===== Header =====
        root.getChildren().add(buildHeader());

        // ===== Form =====
        VBox form = new VBox(10);
        form.setPadding(new Insets(20));
        form.getStyleClass().add("compose-form");
        VBox.setVgrow(form, Priority.ALWAYS);

        // Settings row
        form.getChildren().add(buildSettingsRow());
        form.getChildren().add(new Separator());

        // From
        form.getChildren().add(buildField("From:", fromField = new TextField("user@localhost"), false));
        // To
        form.getChildren().add(buildField("To:", toField = new TextField(), false));
        toField.setPromptText("recipient@example.com, another@example.com");
        // CC
        form.getChildren().add(buildField("CC:", ccField = new TextField(), true));
        ccField.setPromptText("(optional)");
        // BCC
        form.getChildren().add(buildField("BCC:", bccField = new TextField(), true));
        bccField.setPromptText("(optional)");
        // Subject
        form.getChildren().add(buildField("Subject:", subjectField = new TextField(), false));
        subjectField.setPromptText("Email subject");

        // Body label + HTML toggle
        HBox bodyHeader = new HBox(10);
        bodyHeader.setAlignment(Pos.CENTER_LEFT);
        Label bodyLabel = new Label("Body:");
        bodyLabel.getStyleClass().add("field-label");
        bodyLabel.setMinWidth(65);
        htmlToggle = new ToggleButton("HTML");
        htmlToggle.getStyleClass().add("toggle-html");
        bodyHeader.getChildren().addAll(bodyLabel, htmlToggle);
        form.getChildren().add(bodyHeader);

        // Body text area
        bodyArea = new TextArea();
        bodyArea.getStyleClass().add("body-area");
        bodyArea.setPromptText("Write your email here...");
        bodyArea.setPrefRowCount(10);
        VBox.setVgrow(bodyArea, Priority.ALWAYS);
        form.getChildren().add(bodyArea);

        // Attachments
        form.getChildren().add(buildAttachmentSection());

        root.getChildren().add(form);

        // ===== Action Bar =====
        root.getChildren().add(buildActionBar());

        return root;
    }

    private HBox buildHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("compose-header");
        header.setPadding(new Insets(16, 24, 16, 24));
        Label title = new Label("✉  Compose New Email");
        title.getStyleClass().add("compose-title");
        header.getChildren().add(title);
        return header;
    }

    private VBox buildSettingsRow() {
        VBox settingsBox = new VBox(8);
        settingsBox.getStyleClass().add("settings-row");
        settingsBox.setPadding(new Insets(10, 12, 10, 12));

        // Dòng 1: Host, Port, TLS, Nút Gmail Preset
        HBox row1 = new HBox(12);
        row1.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("⚙");
        icon.getStyleClass().add("settings-icon");

        Label hostLabel = new Label("SMTP Host:");
        hostLabel.getStyleClass().add("settings-label");
        TextField hostField = new TextField(smtpHost);
        hostField.setPrefWidth(140);
        hostField.getStyleClass().add("settings-field");
        hostField.textProperty().addListener((o, ov, nv) -> smtpHost = nv.trim());

        Label portLabel = new Label("Port:");
        portLabel.getStyleClass().add("settings-label");
        TextField portField = new TextField(String.valueOf(smtpPort));
        portField.setPrefWidth(55);
        portField.getStyleClass().add("settings-field");
        portField.textProperty().addListener((o, ov, nv) -> {
            try { smtpPort = Integer.parseInt(nv.trim()); } catch (NumberFormatException ignored) {}
        });

        CheckBox tlsCheck = new CheckBox("TLS");
        tlsCheck.getStyleClass().add("tls-check");
        tlsCheck.setSelected(useTLS);
        tlsCheck.setOnAction(e -> useTLS = tlsCheck.isSelected());

        Button gmailPresetBtn = new Button("⚡ Gmail Preset");
        gmailPresetBtn.getStyleClass().add("btn-attach");
        gmailPresetBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 8 3 8;");

        row1.getChildren().addAll(icon, hostLabel, hostField, portLabel, portField, tlsCheck, gmailPresetBtn);

        // Dòng 2: Xác thực (Username và App Password)
        HBox row2 = new HBox(10);
        row2.setAlignment(Pos.CENTER_LEFT);

        Label lockIcon = new Label("🔒");
        lockIcon.getStyleClass().add("settings-icon");

        Label userLabel = new Label("User:");
        userLabel.getStyleClass().add("settings-label");
        TextField userField = new TextField(username);
        userField.setPromptText("your-email@gmail.com");
        userField.setPrefWidth(170);
        userField.getStyleClass().add("settings-field");
        userField.textProperty().addListener((o, ov, nv) -> {
            username = nv.trim();
            // Tự động gợi ý điền trường From nếu người dùng chưa sửa
            if (fromField != null && (fromField.getText().equals("user@localhost") || fromField.getText().isEmpty())) {
                fromField.setText(username);
            }
        });

        Label passLabel = new Label("App Pass:");
        passLabel.getStyleClass().add("settings-label");
        PasswordField passField = new PasswordField();
        passField.setPromptText("16 ký tự App Password");
        passField.setPrefWidth(170);
        passField.getStyleClass().add("settings-field");
        passField.textProperty().addListener((o, ov, nv) -> password = nv.trim());

        gmailPresetBtn.setOnAction(e -> {
            hostField.setText("smtp.gmail.com");
            portField.setText("587");
            tlsCheck.setSelected(true);
            useTLS = true;
            smtpHost = "smtp.gmail.com";
            smtpPort = 587;
        });

        row2.getChildren().addAll(lockIcon, userLabel, userField, passLabel, passField);

        settingsBox.getChildren().addAll(row1, row2);
        return settingsBox;
    }

    private HBox buildField(String labelText, TextField field, boolean optional) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        label.setMinWidth(65);

        field.getStyleClass().add(optional ? "field-input-optional" : "field-input");
        HBox.setHgrow(field, Priority.ALWAYS);

        row.getChildren().addAll(label, field);
        return row;
    }

    private VBox buildAttachmentSection() {
        VBox section = new VBox(8);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label("📎 Attachments:");
        label.getStyleClass().add("field-label");

        Button addBtn = new Button("+ Add File");
        addBtn.getStyleClass().add("btn-attach");
        addBtn.setOnAction(e -> pickAttachment());

        header.getChildren().addAll(label, addBtn);
        section.getChildren().add(header);

        // Danh sách file đính kèm
        attachmentList = new VBox(4);
        attachmentList.getStyleClass().add("attachment-list");
        section.getChildren().add(attachmentList);

        return section;
    }

    private HBox buildActionBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("action-bar");
        bar.setPadding(new Insets(14, 20, 14, 20));
        bar.setAlignment(Pos.CENTER_RIGHT);

        statusLabel = new Label("");
        statusLabel.getStyleClass().add("status-label");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        Button clearBtn = new Button("🗑 Clear");
        clearBtn.getStyleClass().add("btn-secondary");
        clearBtn.setOnAction(e -> clearForm());

        Button sendBtn = new Button("📤 Send Email");
        sendBtn.getStyleClass().add("btn-send");
        sendBtn.setOnAction(e -> sendEmail());

        bar.getChildren().addAll(statusLabel, clearBtn, sendBtn);
        return bar;
    }

    // ==================== Actions ====================

    private void pickAttachment() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn file đính kèm");
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files == null) return;

        for (File file : files) {
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                String contentType = Files.probeContentType(file.toPath());
                if (contentType == null) contentType = "application/octet-stream";

                AttachmentPart att = new AttachmentPart(file.getName(), contentType, data);
                attachments.add(att);
                addAttachmentChip(att);
            } catch (IOException e) {
                showStatus("❌ Không thể đọc file: " + file.getName(), true);
            }
        }
    }

    private void addAttachmentChip(AttachmentPart att) {
        HBox chip = new HBox(8);
        chip.getStyleClass().add("attachment-chip");
        chip.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label("📄 " + att.getFilename() + " (" + att.getReadableSize() + ")");
        nameLabel.getStyleClass().add("attachment-name");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Button removeBtn = new Button("✕");
        removeBtn.getStyleClass().add("btn-remove-attach");
        removeBtn.setOnAction(e -> {
            attachments.remove(att);
            attachmentList.getChildren().remove(chip);
        });

        chip.getChildren().addAll(nameLabel, removeBtn);
        attachmentList.getChildren().add(chip);
    }

    private void sendEmail() {
        // Validate
        if (toField.getText().trim().isEmpty()) {
            showStatus("❌ Vui lòng nhập địa chỉ người nhận (To).", true);
            return;
        }
        if (fromField.getText().trim().isEmpty()) {
            showStatus("❌ Vui lòng nhập địa chỉ người gửi (From).", true);
            return;
        }

        showStatus("⏳ Đang gửi email...", false);

        // Xây dựng MimeMessage
        MimeMessage message = new MimeMessage();
        message.setFrom(fromField.getText().trim());

        for (String r : toField.getText().split(","))
            if (!r.trim().isEmpty()) message.addTo(r.trim());
        for (String r : ccField.getText().split(","))
            if (!r.trim().isEmpty()) message.addCc(r.trim());
        for (String r : bccField.getText().split(","))
            if (!r.trim().isEmpty()) message.addBcc(r.trim());

        message.setSubject(subjectField.getText().trim());

        if (htmlToggle.isSelected()) {
            message.setBodyHtml(bodyArea.getText());
        } else {
            message.setBodyText(bodyArea.getText());
        }

        for (AttachmentPart att : attachments) {
            message.addAttachment(att);
        }

        // Gửi trong background thread để không block UI
        Thread sendThread = new Thread(() -> {
            try {
                SmtpClient client = new SmtpClient(smtpHost, smtpPort, useTLS,
                        username.isEmpty() ? null : username,
                        password.isEmpty() ? null : password);
                client.sendEmail(message);
                Platform.runLater(() -> showStatus("✅ Email đã gửi thành công!", false));
            } catch (IOException ex) {
                Platform.runLater(() ->
                        showStatus("❌ Lỗi gửi email: " + ex.getMessage(), true));
            }
        });
        sendThread.setDaemon(true);
        sendThread.start();
    }

    private void clearForm() {
        toField.clear();
        ccField.clear();
        bccField.clear();
        subjectField.clear();
        bodyArea.clear();
        attachments.clear();
        attachmentList.getChildren().clear();
        showStatus("", false);
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-error", "status-success");
        statusLabel.getStyleClass().add(isError ? "status-error" : "status-success");
    }
}
