package com.example.chattest.controller;

import com.example.chattest.MainApp;
import com.example.chattest.model.ClientModel;
import com.example.chattest.model.Message;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;

public class ChatController {

    // --- Cập nhật FXML IDs ---
    @FXML private ListView<String> contactList; // Danh bạ User
    @FXML private ListView<String> groupList;   // Danh bạ Group (MỚI)
    @FXML private Label currentChatUser;
    @FXML private VBox chatBox;
    @FXML private TextField messageInput;
    @FXML private ScrollPane chatScrollPane;
    @FXML private Button reloadButton;
    @FXML private TextField groupNameField; // Ô nhập tên nhóm (MỚI)
    @FXML private Button createGroupButton; // Nút tạo nhóm (MỚI)
    // -------------------------

    private final ClientModel model = ClientModel.getInstance();

    @FXML
    public void initialize() {
        // Tự động cuộn
        chatBox.heightProperty().addListener((obs, oldVal, newVal) -> chatScrollPane.setVvalue(1.0));

        // Lắng nghe danh sách tin nhắn "ĐANG HOẠT ĐỘNG"
        model.getActiveMessages().addListener((ListChangeListener<Message>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (Message msg : c.getAddedSubList()) {
                        addMessageToView(msg);
                    }
                }
            }
        });

        // --- Gắn 2 ListView vào Model ---
        contactList.setItems(model.getOnlineUsers());
        groupList.setItems(model.getGroupList()); // (MỚI)
        // ---------------------------------

        // Tự động chọn user đầu tiên (nếu có)
        model.getOnlineUsers().addListener((ListChangeListener<String>) c -> {
            while(c.next()) {
                if(c.wasAdded() && contactList.getSelectionModel().getSelectedIndex() == -1 && groupList.getSelectionModel().getSelectedIndex() == -1) {
                    Platform.runLater(() -> contactList.getSelectionModel().selectFirst());
                }
            }
        });

        // --- Logic chọn item (NÂNG CẤP) ---
        // Lắng nghe khi click vào DANH BẠ USER
        contactList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // Hủy chọn ở list kia
                groupList.getSelectionModel().clearSelection();

                chatBox.getChildren().clear();
                currentChatUser.setText(newVal + " (Cá nhân)");
                // Báo Model: đây là USER
                model.setActiveConversation(newVal, "USER");
            }
        });

        // Lắng nghe khi click vào DANH BẠ GROUP (MỚI)
        groupList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // Hủy chọn ở list kia
                contactList.getSelectionModel().clearSelection();

                chatBox.getChildren().clear();
                currentChatUser.setText(newVal + " (Nhóm)");
                // Báo Model: đây là GROUP
                model.setActiveConversation(newVal, "GROUP");
            }
        });
        // ---------------------------------
    }

    /**
     * Được gọi bởi LoginController SAU KHI ĐĂNG NHẬP THÀNH CÔNG
     */
    public void initData(String username, Stage stage) {
        stage.setTitle("Chat Messenger - " + username);

        stage.setOnCloseRequest(event -> {
            model.logout();
            Platform.exit();
        });

        System.out.println("Da vao man hinh chat voi ten: " + username);

        // Tự động yêu cầu danh bạ user (danh bạ nhóm server tự gửi khi login)
        model.requestUserList();
    }

    @FXML
    void onSendButtonClick() {
        String content = messageInput.getText();
        // Model đã biết là gửi cho USER hay GROUP
        model.sendMessage(content);
        messageInput.clear();
    }

    @FXML
    void onReloadContactsClick() {
        model.requestUserList();
        System.out.println("Dang yeu cau lam moi danh ba user...");
        // (Server tự gửi danh sách nhóm khi login và khi tạo mới,
        // nhưng ta có thể thêm 1 lệnh reload nhóm nếu muốn)
    }

    // --- HÀM MỚI ---
    // ... bên trong ChatController.java ...

    // HÀM NÀY ĐƯỢC SỬA LẠI HOÀN TOÀN
    @FXML
    void onCreateGroupClick() {
        try {
            // Mở file FXML của Dialog
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("view/create-group-dialog.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Tạo nhóm mới");
            dialogStage.setScene(new Scene(root));

            // (Tùy chọn) Khóa cửa sổ chat chính cho đến khi dialog này được đóng
            // dialogStage.initModality(Modality.APPLICATION_MODAL);

            dialogStage.showAndWait(); // Hiển thị và chờ

        } catch (IOException e) {
            e.printStackTrace();
            // (Thêm Alert báo lỗi nếu không mở được dialog)
        }
    }
    // ----------------

    /**
     * Hàm hiển thị bong bóng chat (Không thay đổi)
     */
    private void addMessageToView(Message message) {
        Label messageLabel = new Label(message.getContent());
        messageLabel.setWrapText(true);
        VBox messageBubble = new VBox();
        messageBubble.getStyleClass().add("message-bubble");
        messageBubble.getChildren().add(messageLabel);
        HBox messageRow = new HBox();
        messageRow.getStyleClass().add("message-container");

        if (message.isSystemMessage()) {
            messageLabel.setStyle("-fx-font-style: italic; -fx-background-color: #fafafa; -fx-text-fill: #555;");
            messageLabel.getStyleClass().remove("message-bubble");
            messageRow.setAlignment(Pos.CENTER);
            messageRow.getChildren().add(messageLabel);
        } else if (message.isSentByMe()) {
            messageBubble.getStyleClass().add("sent");
            messageBubble.setAlignment(Pos.CENTER_LEFT);
            messageRow.setAlignment(Pos.CENTER_RIGHT);
            messageRow.getChildren().add(messageBubble);
        } else {
            Label senderLabel = new Label(message.getUsername());
            senderLabel.getStyleClass().add("sender-label");
            messageBubble.getStyleClass().add("received");
            messageBubble.setAlignment(Pos.CENTER_LEFT);
            VBox messageGroup = new VBox(2);
            messageGroup.setAlignment(Pos.CENTER_LEFT);
            messageGroup.getChildren().addAll(senderLabel, messageBubble);
            messageRow.setAlignment(Pos.CENTER_LEFT);
            messageRow.getChildren().add(messageGroup);
        }
        chatBox.getChildren().add(messageRow);
    }
}