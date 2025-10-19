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
import java.net.URL;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private Label statusLabel;

    private final ClientModel model = ClientModel.getInstance();
    private final String HOST = "localhost";
    private final int PORT = 12345;

    @FXML
    public void initialize() {
        // Lắng nghe tín hiệu ĐĂNG NHẬP từ ClientModel
        model.getAuthStatusProperty().addListener((obs, oldStatus, newStatus) -> {
            if (newStatus == null || newStatus.isEmpty()) {
                return;
            }

            if (newStatus.equals("LOGIN_SUCCESS")) {
                // Đăng nhập thành công! Mở cửa sổ chat
                Platform.runLater(this::openChatWindow);

            }
            // --- THAY ĐỔI ---
            // Không lắng nghe "REGISTER_SUCCESS" ở đây nữa
            // --- KẾT THÚC THAY ĐỔI ---

            else if (newStatus.startsWith("Loi -")) {
                // Chỉ hiển thị các lỗi liên quan đến ĐĂNG NHẬP
                showStatus(newStatus, true);
            }
        });

        // Xóa trạng thái lỗi khi người dùng gõ phím
        usernameField.textProperty().addListener((obs, o, n) -> hideStatus());
        passwordField.textProperty().addListener((obs, o, n) -> hideStatus());
    }

    @FXML
    void onLoginClick(ActionEvent event) {
        String user = usernameField.getText();
        String pass = passwordField.getText();
        if (user.isEmpty() || pass.isEmpty()) {
            showStatus("Loi - Vui long nhap ten va mat khau", true);
            return;
        }

        disableButtons(true);
        showStatus("Dang ket noi...", false);

        model.attemptLogin(user, pass, HOST, PORT);
    }

    @FXML
    void onRegisterClick(ActionEvent event) {
        // --- THAY ĐỔI ---
        // Không đăng ký ở đây. Chỉ mở cửa sổ đăng ký.
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("view/register-view.fxml"));
            Parent root = loader.load();
            Stage registerStage = new Stage();
            registerStage.setTitle("Đăng ký tài khoản");
            // Kích thước của form đăng ký (cao hơn)
            registerStage.setScene(new Scene(root, 400, 400));
            registerStage.show();

            // Đóng cửa sổ Đăng nhập hiện tại
            Stage currentStage = (Stage) usernameField.getScene().getWindow();
            currentStage.close();

        } catch (IOException e) {
            e.printStackTrace();
            showStatus("Loi - Khong the mo form dang ky.", true);
        }
        // --- KẾT THÚC THAY ĐỔI ---
    }

    /**
     * Chỉ được gọi KHI Model báo "LOGIN_SUCCESS"
     */
    private void openChatWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("view/chat-view.fxml"));
            Parent root = loader.load();

            ChatController chatController = loader.getController();
            Stage chatStage = new Stage();

            chatController.initData(model.getUsername(), chatStage);

            Scene scene = new Scene(root, 800, 600);

            URL cssUrl = MainApp.class.getResource("view/style.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            chatStage.setScene(scene);
            chatStage.show();

            Stage loginStage = (Stage) usernameField.getScene().getWindow();
            loginStage.close();

        } catch (IOException e) {
            e.printStackTrace();
            showStatus("Loi - Khong the tai giao dien chat.", true);
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
        loginButton.setDisable(disable);
        registerButton.setDisable(disable);
    }
}