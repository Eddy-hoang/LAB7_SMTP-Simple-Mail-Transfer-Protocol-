module com.javamail {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.logging;

    opens com.javamail.app to javafx.graphics, javafx.fxml;
    opens com.javamail.smtp.ui to javafx.graphics, javafx.fxml;
    opens com.javamail.pop3.ui to javafx.graphics, javafx.fxml;
    opens com.javamail.smtp.client to javafx.fxml;
    opens com.javamail.pop3.client to javafx.fxml;

    exports com.javamail.app;
    exports com.javamail.smtp.server;
    exports com.javamail.smtp.client;
    exports com.javamail.smtp.ui;
    exports com.javamail.pop3.server;
    exports com.javamail.pop3.client;
    exports com.javamail.pop3.ui;
    exports com.javamail.shared.model;
    exports com.javamail.shared.mime;
}
