package com.example.chattest.model;

import com.example.chattest.network.ChatClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientModel {
    // ---- Singleton Pattern ----
    private static ClientModel instance;
    public static synchronized ClientModel getInstance() {
        if (instance == null) { instance = new ClientModel(); }
        return instance;
    }
    // ---------------------------

    private ChatClient chatClient;
    private String username;

    // --- NÂNG CẤP NHÓM ---
    private final ObservableList<String> onlineUsers = FXCollections.observableArrayList();
    private final ObservableList<String> groupList = FXCollections.observableArrayList();

    private final Map<String, ObservableList<Message>> conversations = new ConcurrentHashMap<>();

    private final StringProperty activeConversationId = new SimpleStringProperty();
    private final StringProperty activeConversationType = new SimpleStringProperty("NONE");

    private final ObservableList<Message> activeMessages = FXCollections.observableArrayList();

    // --- NÂNG CẤP XÁC THỰC ---
    private final StringProperty authStatus = new SimpleStringProperty();
    private boolean isConnecting = false;
    // ----------------------

    private ClientModel() {}

    /**
     * Thử đăng nhập
     */
    public void attemptLogin(String username, String password, String host, int port) {
        if (isConnecting) return;
        isConnecting = true;
        this.username = username; // Lưu tạm
        try {
            this.chatClient = new ChatClient(host, port, this::onMessageReceived);
            new Thread(chatClient::startListening).start();
            chatClient.sendMessage("LOGIN:" + username + ":" + password);
        } catch (IOException e) {
            Platform.runLater(() -> setAuthStatus("Loi: Khong the ket noi den may chu."));
            isConnecting = false;
        }
    }

    /**
     * Thử đăng ký
     */
    public void attemptRegister(String username, String password, String host, int port) {
        if (isConnecting) return;
        isConnecting = true;
        try {
            ChatClient registerClient = new ChatClient(host, port, this::onMessageReceived);
            new Thread(registerClient::startListening).start();
            registerClient.sendMessage("REGISTER:" + username + ":" + password);
        } catch (IOException e) {
            Platform.runLater(() -> setAuthStatus("Loi: Khong the ket noi den may chu."));
            isConnecting = false;
        }
    }

    /**
     * Callback khi có tin nhắn từ Server
     */
    private void onMessageReceived(String rawMessage) {
        if (rawMessage.startsWith("IN_MSG:")) {
            // Tin 1-1
            String[] parts = rawMessage.substring(7).split(":", 2);
            if (parts.length < 2) return;
            String sender = parts[0];
            String content = parts[1];
            Message msg = new Message(sender, content, false, false);
            String conversationId = "user_" + sender;
            ObservableList<Message> history = getHistoryFor(conversationId);

            Platform.runLater(() -> {
                history.add(msg);
                if (conversationId.equals(activeConversationId.get())) {
                    activeMessages.add(msg);
                }
            });

        } else if (rawMessage.startsWith("IN_GROUP_MSG:")) {
            // Tin nhóm
            String[] parts = rawMessage.substring(13).split(":", 3);
            if (parts.length < 3) return;
            String groupName = parts[0];
            String sender = parts[1];
            String content = parts[2];

            if (sender.equals(this.username)) return; // Lọc tin nhắn của chính mình

            Message msg = new Message(sender, content, false, false);
            String conversationId = "group_" + groupName;
            ObservableList<Message> history = getHistoryFor(conversationId);

            Platform.runLater(() -> {
                history.add(msg);
                if (conversationId.equals(activeConversationId.get())) {
                    activeMessages.add(msg);
                }
            });

        } else if (rawMessage.startsWith("SYSTEM:")) {
            String content = rawMessage.substring(7);

            Platform.runLater(() -> {
                // --- Logic xác thực ---
                if (content.startsWith("LOGIN_SUCCESS:")) {
                    this.username = content.substring(14);
                    setAuthStatus("LOGIN_SUCCESS");
                    isConnecting = false;
                } else if (content.startsWith("REGISTER_SUCCESS:")) {
                    setAuthStatus(content);
                    isConnecting = false;
                } else if (content.startsWith("Loi -")) {
                    setAuthStatus(content);
                    isConnecting = false;

                    // --- Logic danh bạ/nhóm ---
                } else if (content.startsWith("USER_LIST:")) {
                    String userListData = content.substring(10);
                    onlineUsers.clear();
                    if (!userListData.isEmpty()) {
                        onlineUsers.addAll(userListData.split(","));
                    }
                } else if (content.startsWith("USER_JOIN:")) {
                    onlineUsers.add(content.substring(10));
                } else if (content.startsWith("USER_LEAVE:")) {
                    onlineUsers.remove(content.substring(11));
                } else if (content.startsWith("GROUP_LIST:")) {
                    // "SYSTEM:GROUP_LIST:group1,group2"
                    String groupListData = content.substring(11); // Lấy phần sau "GROUP_LIST:"

                    // --- THÊM DÒNG DEBUG NÀY ---
                    System.out.println("[DEBUG] Nhan duoc GROUP_LIST: " + groupListData);
                    // --- KẾT THÚC THÊM ---

                    groupList.clear(); // Xóa list cũ
                    if (!groupListData.isEmpty()) {
                        groupList.addAll(groupListData.split(",")); // Thêm list mới
                    }

                    // --- THÊM DÒNG DEBUG NÀY ---
                    System.out.println("[DEBUG] Danh sach nhom trong Model da cap nhat: " + groupList);
                    // --- KẾT THÚC THÊM ---

                } else {
                    // Tin nhắn hệ thống trong chat
                    activeMessages.add(new Message("Hệ thống", content, false, true));
                }
            });
        }
    }

    /**
     * Gửi tin nhắn đến cuộc trò chuyện đang hoạt động
     */
    public void sendMessage(String content) {
        String activeId = activeConversationId.get();
        String activeType = activeConversationType.get();

        if (content == null || content.trim().isEmpty() || activeId == null || activeType.equals("NONE") || chatClient == null) {
            return;
        }

        String formattedMessage;
        String conversationKey = activeId;
        String nameOnly = activeId.substring(activeId.indexOf("_") + 1);

        if (activeType.equals("USER")) {
            formattedMessage = "MSG:" + nameOnly + ":" + content;
        } else if (activeType.equals("GROUP")) {
            formattedMessage = "GROUP_MSG:" + nameOnly + ":" + content;
        } else {
            return;
        }

        chatClient.sendMessage(formattedMessage);

        Message sentMessage = new Message(this.username, content, true, false);
        ObservableList<Message> history = getHistoryFor(conversationKey);

        Platform.runLater(() -> {
            history.add(sentMessage);
            activeMessages.add(sentMessage);
        });
    }

    /**
     * Yêu cầu tạo nhóm mới VỚI danh sách thành viên
     */
    public void requestGroupCreate(String groupName, ObservableList<String> members) {
        if (groupName == null || groupName.trim().isEmpty() || chatClient == null) {
            return;
        }
        String memberList = String.join(",", members);
        chatClient.sendMessage("GROUP_CREATE:" + groupName.trim() + ":" + memberList);
    }

    /**
     * Chuyển cuộc trò chuyện
     */
    public void setActiveConversation(String name, String type) {
        if (name == null || type.equals("NONE")) {
            activeConversationId.set(null);
            activeConversationType.set("NONE");
            activeMessages.clear();
            return;
        }

        String conversationKey = type.toLowerCase() + "_" + name;

        if (conversationKey.equals(activeConversationId.get())) {
            return;
        }

        activeConversationId.set(conversationKey);
        activeConversationType.set(type);

        ObservableList<Message> history = getHistoryFor(conversationKey);
        activeMessages.setAll(history);
    }

    private ObservableList<Message> getHistoryFor(String conversationKey) {
        return conversations.computeIfAbsent(conversationKey, k -> FXCollections.observableArrayList());
    }

    /**
     * Hàm nội bộ để cập nhật trạng thái xác thực một cách an toàn
     */
    private void setAuthStatus(String status) {
        if (Platform.isFxApplicationThread()) {
            authStatus.set(status);
        } else {
            Platform.runLater(() -> authStatus.set(status));
        }
    }

    // --- Getters và Logout ---

    public StringProperty getAuthStatusProperty() { return authStatus; }
    public ObservableList<Message> getActiveMessages() { return activeMessages; }
    public ObservableList<String> getOnlineUsers() { return onlineUsers; }
    public ObservableList<String> getGroupList() { return groupList; }

    public void requestUserList() {
        if (chatClient != null) { chatClient.sendMessage("SYSTEM:GET_USERS"); }
    }

    public void logout() {
        if (chatClient != null) { chatClient.close(); }
        conversations.clear();
        activeMessages.clear();
        onlineUsers.clear();
        groupList.clear();
        username = null;
        authStatus.set(null);
        activeConversationId.set(null);
        activeConversationType.set("NONE");
    }
    public String getUsername() { return username; }
}