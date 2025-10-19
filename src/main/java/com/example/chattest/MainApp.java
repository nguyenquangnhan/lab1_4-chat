package com.example.chattest;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Khởi động bằng màn hình ĐĂNG NHẬP từ thư mục /view/
        FXMLLoader fXMLloader = new FXMLLoader(MainApp.class.getResource("view/login-view.fxml"));
        Scene scene = new Scene(fXMLloader.load(), 400, 300);

        stage.setTitle("Đăng nhập");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}