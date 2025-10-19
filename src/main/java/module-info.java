module com.example.chattest {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires jbcrypt;
    requires java.sql; // Thêm dòng này cho chắc chắn

    // Mở gói controller để FXML có thể truy cập
    opens com.example.chattest.controller to javafx.fxml;

    // Mở gói model để JavaFX binding (như ObservableList) hoạt động
    opens com.example.chattest.model to javafx.base;

    // Mở gói chính
    opens com.example.chattest to javafx.fxml, javafx.graphics;
}