package com.example.chattest.controller;

import com.example.chattest.MainApp;
import com.example.chattest.model.ClientModel;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button registerButton;
    @FXML private Button backButton;
    @FXML private Label statusLabel;

    private final ClientModel model = ClientModel.getInstance();
    private final String HOST = "localhost";
    private final int PORT = 12345;

    @FXML
    public void initialize() {
        // Lắng nghe tín hiệu ĐĂNG KÝ từ ClientModel
        model.getAuthStatusProperty().addListener((obs, oldStatus, newStatus) -> {
            if (newStatus == null || newStatus.isEmpty()) {
                return;
            }

            if (newStatus.startsWith("REGISTER_SUCCESS")) {
                // Đăng ký thành công
                showStatus("Đăng ký thành công! Vui lòng quay lại đăng nhập.", false); // false = không phải lỗi
                disableButtons(true); // Vô hiệu hóa nút sau khi thành công

            } else if (newStatus.startsWith("Loi -")) {
                // Bất kỳ lỗi nào từ server (user tồn tại, v.v.)
                showStatus(newStatus, true); // true = lỗi
            }
        });

        // Xóa trạng thái lỗi khi người dùng gõ phím
        usernameField.textProperty().addListener((obs, o, n) -> hideStatus());
        passwordField.textProperty().addListener((obs, o, n) -> hideStatus());
        confirmPasswordField.textProperty().addListener((obs, o, n) -> hideStatus());
    }

    @FXML
    void onRegisterClick(ActionEvent event) {
        String user = usernameField.getText();
        String pass = passwordField.getText();
        String confirmPass = confirmPasswordField.getText();

        if (user.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
            showStatus("Loi - Vui long nhap day du thong tin", true);
            return;
        }
        if (!pass.equals(confirmPass)) {
            showStatus("Loi - Mat khau xac nhan khong khop", true);
            return;
        }

        disableButtons(true);
        showStatus("Dang gui yeu cau...", false);

        // Yêu cầu Model thử đăng ký
        model.attemptRegister(user, pass, HOST, PORT);
    }

    @FXML
    void onBackToLoginClick(ActionEvent event) {
        // Mở lại cửa sổ Đăng nhập
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("view/login-view.fxml"));
            Parent root = loader.load();
            Stage loginStage = new Stage();
            loginStage.setTitle("Đăng nhập");
            // Kích thước của form login
            loginStage.setScene(new Scene(root, 400, 350));
            loginStage.show();

            // Đóng cửa sổ Đăng ký hiện tại
            Stage currentStage = (Stage) usernameField.getScene().getWindow();
            currentStage.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Các hàm tiện ích cho UI ---

    private void showStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
            statusLabel.setManaged(true);
            disableButtons(false); // Bật lại nút sau khi có kết quả
        });
    }

    private void hideStatus() {
        statusLabel.setManaged(false);
    }

    private void disableButtons(boolean disable) {
        registerButton.setDisable(disable);
        backButton.setDisable(disable);
    }
}