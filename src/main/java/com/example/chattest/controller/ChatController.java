package com.example.chattest.controller;

import com.example.chattest.MainApp;
import com.example.chattest.model.ClientModel;
import com.example.chattest.model.Message;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent; // <-- THÊM IMPORT
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node; // <-- THÊM IMPORT
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*; // <-- THÊM IMPORT * (ListView, ScrollPane, TextField, Alert, Button, Label)
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser; // Giữ lại import này
import javafx.stage.Stage;

import java.io.File; // Giữ lại import này
import java.io.IOException;

public class ChatController {

    // --- FXML IDs (Giữ nguyên) ---
    @FXML private ListView<String> contactList;
    @FXML private ListView<String> groupList;
    @FXML private Label currentChatUser;
    @FXML private VBox chatBox;
    @FXML private TextField messageInput;
    @FXML private ScrollPane chatScrollPane;
    @FXML private Button reloadButton;
    @FXML private TextField groupNameField;
    @FXML private Button createGroupButton;
    @FXML private Button sendFileButton; // Đã thêm ở bước trước
    // -------------------------

    private final ClientModel model = ClientModel.getInstance();

    // (initialize giữ nguyên)
    @FXML
    public void initialize() {
        chatBox.heightProperty().addListener((obs, oldVal, newVal) -> chatScrollPane.setVvalue(1.0));
        model.getActiveMessages().addListener((ListChangeListener<Message>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (Message msg : c.getAddedSubList()) { addMessageToView(msg); }
                }
            }
        });
        contactList.setItems(model.getOnlineUsers());
        groupList.setItems(model.getGroupList());
        model.getOnlineUsers().addListener((ListChangeListener<String>) c -> {
            while(c.next()) {
                if(c.wasAdded() && contactList.getSelectionModel().getSelectedIndex() == -1 && groupList.getSelectionModel().getSelectedIndex() == -1) {
                    Platform.runLater(() -> contactList.getSelectionModel().selectFirst());
                }
            }
        });
        contactList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                groupList.getSelectionModel().clearSelection(); chatBox.getChildren().clear();
                currentChatUser.setText(newVal + " (Cá nhân)"); model.setActiveConversation(newVal, "USER");
            }
        });
        groupList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                contactList.getSelectionModel().clearSelection(); chatBox.getChildren().clear();
                currentChatUser.setText(newVal + " (Nhóm)"); model.setActiveConversation(newVal, "GROUP");
            }
        });
    }

    // (initData giữ nguyên)
    public void initData(String username, Stage stage) {
        stage.setTitle("Chat Messenger - " + username);
        stage.setOnCloseRequest(event -> { model.logout(); Platform.exit(); });
        System.out.println("Da vao man hinh chat voi ten: " + username);
        model.requestUserList();
    }

    // (onSendButtonClick, onReloadContactsClick, onCreateGroupClick giữ nguyên)
    @FXML void onSendButtonClick() { /*...*/ }
    @FXML void onReloadContactsClick() { /*...*/ }
    @FXML void onCreateGroupClick() { /*...*/ }
    @FXML void onSendFileClick() { /*...*/ } // Đã thêm ở bước trước
    private void showAlert(Alert.AlertType type, String title, String content) { /*...*/ } // Đã thêm ở bước trước


    /**
     * Hàm hiển thị bong bóng chat (NÂNG CẤP ĐỂ HIỂN THỊ FILE OFFER)
     */
    private void addMessageToView(Message message) {
        Node messageNode; // Sử dụng Node chung

        if (message.isFileOffer()) {
            // --- NẾU LÀ FILE OFFER ---
            messageNode = createFileOfferNode(message);
            // ------------------------
        } else {
            // --- NẾU LÀ TIN NHẮN THƯỜNG HOẶC HỆ THỐNG ---
            Label messageLabel = new Label(message.getContent());
            messageLabel.setWrapText(true);
            VBox messageBubble = new VBox();
            messageBubble.getChildren().add(messageLabel);
            messageNode = messageBubble;

            if (message.isSystemMessage()) {
                messageLabel.setStyle("-fx-font-style: italic; -fx-background-color: #fafafa; -fx-text-fill: #555;");
                messageNode = messageLabel; // Bỏ bubble cho hệ thống
            } else if (message.isSentByMe()) {
                messageBubble.getStyleClass().addAll("message-bubble", "sent");
                messageBubble.setAlignment(Pos.CENTER_LEFT);
            } else { // Tin nhắn nhận (thường)
                messageBubble.getStyleClass().addAll("message-bubble", "received");
                messageBubble.setAlignment(Pos.CENTER_LEFT);
                Label senderLabel = new Label(message.getUsername());
                senderLabel.getStyleClass().add("sender-label");
                VBox messageGroup = new VBox(2, senderLabel, messageBubble);
                messageGroup.setAlignment(Pos.CENTER_LEFT);
                messageNode = messageGroup;
            }
            // ------------------------------------------
        }

        // Tạo HBox cho cả hàng và căn lề
        HBox messageRow = new HBox();
        messageRow.getStyleClass().add("message-container");

        if (message.isSystemMessage() || message.isFileOffer()) {
            messageRow.setAlignment(Pos.CENTER);
        } else if (message.isSentByMe()) {
            messageRow.setAlignment(Pos.CENTER_RIGHT);
        } else {
            messageRow.setAlignment(Pos.CENTER_LEFT);
        }

        messageRow.getChildren().add(messageNode);
        chatBox.getChildren().add(messageRow);
    }

    // --- THÊM CÁC HÀM MỚI ĐỂ XỬ LÝ FILE OFFER ---

    /** Tạo HBox hiển thị lời mời nhận file và nút Đồng ý/Từ chối */
    private HBox createFileOfferNode(Message offerMessage) {
        String sender = offerMessage.getUsername();
        String filename = offerMessage.getContent();
        long filesize = offerMessage.getFileSize();
        String filesizeStr = String.format("%.2f KB", (double)filesize / 1024);
        if (filesize > 1024 * 1024) { filesizeStr = String.format("%.2f MB", (double)filesize / (1024 * 1024)); }

        Label offerLabel = new Label(sender + " muốn gửi file: " + filename + " (" + filesizeStr + ")");
        offerLabel.setWrapText(true);

        Button acceptButton = new Button("Đồng ý");
        acceptButton.setStyle("-fx-background-color: #42b72a; -fx-text-fill: white;");
        acceptButton.setUserData(sender + ":" + filename); // Lưu dữ liệu vào nút
        acceptButton.setOnAction(this::onAcceptFileClick); // Đặt sự kiện click

        Button rejectButton = new Button("Từ chối");
        rejectButton.setStyle("-fx-background-color: #f02849; -fx-text-fill: white;");
        rejectButton.setUserData(sender + ":" + filename);
        rejectButton.setOnAction(this::onRejectFileClick);

        HBox buttonBox = new HBox(10, acceptButton, rejectButton);
        buttonBox.setAlignment(Pos.CENTER);

        VBox offerBox = new VBox(5, offerLabel, buttonBox);
        offerBox.setAlignment(Pos.CENTER);
        offerBox.setStyle("-fx-background-color: #e4e6eb; -fx-padding: 10px; -fx-background-radius: 10px; -fx-border-color: #cccccc; -fx-border-radius: 10px;");
        offerBox.setMaxWidth(350);

        return new HBox(offerBox); // Bọc trong HBox để căn giữa
    }

    /** Được gọi khi nhấn nút "Đồng ý" nhận file */
    private void onAcceptFileClick(ActionEvent event) {
        Button clickedButton = (Button) event.getSource();
        String userData = (String) clickedButton.getUserData();
        String[] parts = userData.split(":", 2);
        if (parts.length == 2) {
            String sender = parts[0];
            String filename = parts[1];
            model.acceptFile(sender, filename); // Gọi Model

            // Thay đổi giao diện
            VBox offerBox = (VBox) clickedButton.getParent().getParent();
            offerBox.getChildren().remove(1); // Xóa HBox nút
            ((Label)offerBox.getChildren().get(0)).setText("Đang tải file " + filename + "...");
            // (Bạn có thể thêm ProgressBar nếu muốn hiển thị tiến độ phức tạp hơn)
        }
    }

    /** Được gọi khi nhấn nút "Từ chối" nhận file */
    private void onRejectFileClick(ActionEvent event) {
        Button clickedButton = (Button) event.getSource();
        String userData = (String) clickedButton.getUserData();
        String[] parts = userData.split(":", 2);
        if (parts.length == 2) {
            String sender = parts[0];
            String filename = parts[1];
            model.rejectFile(sender, filename); // Gọi Model

            // Thay đổi giao diện
            VBox offerBox = (VBox) clickedButton.getParent().getParent();
            offerBox.getChildren().remove(1); // Xóa HBox nút
            ((Label)offerBox.getChildren().get(0)).setText("Đã từ chối nhận file " + filename + ".");
            offerBox.setStyle("-fx-background-color: #ffebee; -fx-padding: 10px; -fx-background-radius: 10px; -fx-border-color: #ffcdd2; -fx-border-radius: 10px;");
        }
    }
    // --- KẾT THÚC THÊM ---
}