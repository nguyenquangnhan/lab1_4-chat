package com.example.chattest.model;

import com.example.chattest.network.ChatClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser; // Cần giữ lại FileChooser cho bước sau

import java.io.*; // Import đầy đủ java.io.*
import java.nio.file.Files; // Thêm import Files
import java.nio.file.Path;  // Thêm import Path
import java.nio.file.Paths; // Thêm import Paths
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays; // Thêm import Arrays

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

    private final ObservableList<String> onlineUsers = FXCollections.observableArrayList();
    private final ObservableList<String> groupList = FXCollections.observableArrayList();
    private final Map<String, ObservableList<Message>> conversations = new ConcurrentHashMap<>();
    private final StringProperty activeConversationId = new SimpleStringProperty();
    private final StringProperty activeConversationType = new SimpleStringProperty("NONE");
    private final ObservableList<Message> activeMessages = FXCollections.observableArrayList();
    private final StringProperty authStatus = new SimpleStringProperty();
    private boolean isConnecting = false;

    private static final int FILE_CHUNK_SIZE = 8192;
    private Map<String, File> pendingFiles = new ConcurrentHashMap<>();
    private Map<String, FileOfferInfo> pendingFileOffers = new ConcurrentHashMap<>();
    private Map<String, FileOutputStream> receivingFiles = new ConcurrentHashMap<>();
    private static final String DOWNLOAD_DIR = System.getProperty("user.home") + File.separator + "ChatAppDownloads";

    private ClientModel() {
        try {
            Files.createDirectories(Paths.get(DOWNLOAD_DIR));
        } catch (IOException e) {
            System.err.println("Khong the tao thu muc download: " + DOWNLOAD_DIR);
            e.printStackTrace();
        }
    }

    // (attemptLogin, attemptRegister giữ nguyên)
    public void attemptLogin(String username, String password, String host, int port) {
        if (isConnecting) return;
        isConnecting = true;
        this.username = username;
        try {
            this.chatClient = new ChatClient(host, port, this::onMessageReceived);
            new Thread(chatClient::startListening).start();
            chatClient.sendMessage("LOGIN:" + username + ":" + password);
        } catch (IOException e) {
            Platform.runLater(() -> setAuthStatus("Loi: Khong the ket noi den may chu."));
            isConnecting = false;
        }
    }
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
        // --- Logic cũ (IN_MSG, IN_GROUP_MSG, SYSTEM) ---
        if (rawMessage.startsWith("IN_MSG:")) {
            String[] parts = rawMessage.substring(7).split(":", 2);
            if (parts.length < 2) return; String sender = parts[0]; String content = parts[1];
            Message msg = new Message(sender, content, false, false);
            String conversationId = "user_" + sender;
            // Dòng 118 gây lỗi nằm ở đây khi gọi getHistoryFor
            ObservableList<Message> history = getHistoryFor(conversationId);
            Platform.runLater(() -> { history.add(msg); if (conversationId.equals(activeConversationId.get())) { activeMessages.add(msg); } });

        } else if (rawMessage.startsWith("IN_GROUP_MSG:")) {
            String[] parts = rawMessage.substring(13).split(":", 3);
            if (parts.length < 3) return; String groupName = parts[0]; String sender = parts[1]; String content = parts[2];
            if (sender.equals(this.username)) return;
            Message msg = new Message(sender, content, false, false);
            String conversationId = "group_" + groupName;
            ObservableList<Message> history = getHistoryFor(conversationId);
            Platform.runLater(() -> { history.add(msg); if (conversationId.equals(activeConversationId.get())) { activeMessages.add(msg); } });

        } else if (rawMessage.startsWith("SYSTEM:")) {
            String content = rawMessage.substring(7);
            Platform.runLater(() -> {
                if (content.startsWith("LOGIN_SUCCESS:")) { this.username = content.substring(14); setAuthStatus("LOGIN_SUCCESS"); isConnecting = false; }
                else if (content.startsWith("REGISTER_SUCCESS:")) { setAuthStatus(content); isConnecting = false; }
                else if (content.startsWith("Loi -") && (content.contains("xac thuc") || content.contains("mat khau") || content.contains("ton tai") || content.contains("noi khac"))) { setAuthStatus(content); isConnecting = false; }
                else if (content.startsWith("USER_LIST:")) { String userListData = content.substring(10); onlineUsers.clear(); if (!userListData.isEmpty()) { onlineUsers.addAll(userListData.split(",")); } }
                else if (content.startsWith("USER_JOIN:")) { onlineUsers.add(content.substring(10)); }
                else if (content.startsWith("USER_LEAVE:")) { onlineUsers.remove(content.substring(11)); }
                else if (content.startsWith("GROUP_LIST:")) { String groupListData = content.substring(11); System.out.println("[DEBUG] Nhan duoc GROUP_LIST: " + groupListData); groupList.clear(); if (!groupListData.isEmpty()) { groupList.addAll(groupListData.split(",")); } System.out.println("[DEBUG] Danh sach nhom trong Model da cap nhat: " + groupList); }
                else if (content.startsWith("FILE_ACCEPTED:")) { String[] parts = content.split(":", 3); if (parts.length == 3) { startSendingFileChunks(parts[1], parts[2]); } }
                else if (content.startsWith("FILE_REJECTED:")) { String[] parts = content.split(":", 3); if (parts.length == 3) { String rejectionMsg = parts[1] + " đã từ chối nhận file " + parts[2] + "."; activeMessages.add(new Message("Hệ thống", rejectionMsg, false, true)); pendingFiles.remove(parts[1] + ":" + parts[2]); } }
                else { activeMessages.add(new Message("Hệ thống", content, false, true)); }
            });
        }
        // --- Logic nhận file ---
        else if (rawMessage.startsWith("IN_FILE_OFFER:")) { handleFileOffer(rawMessage); }
        else if (rawMessage.startsWith("IN_FILE_CHUNK:")) { handleFileChunk(rawMessage); }
        else if (rawMessage.startsWith("IN_FILE_END:")) { handleFileEnd(rawMessage); }
    }

    // --- Các hàm xử lý nhận file (giữ nguyên) ---
    private void handleFileOffer(String message) { /*...*/ }
    public void acceptFile(String sender, String filename) { /*...*/ }
    public void rejectFile(String sender, String filename) { /*...*/ }
    private void handleFileChunk(String message) { /*...*/ }
    private void handleFileEnd(String message) { /*...*/ }
    private void closeReceivingStream(String fileKey, FileOutputStream fos, boolean hasError) { /*...*/ }
    private static class FileOfferInfo { /*...*/ }

    // --- Các hàm xử lý gửi file (giữ nguyên) ---
    public void initiateFileSend(String recipient, File file) { /*...*/ }
    private void startSendingFileChunks(String recipient, String filename) { /*...*/ }

    // --- CÁC HÀM CÒN LẠI (ĐÃ THÊM LẠI ĐẦY ĐỦ) ---
    public void sendMessage(String content) {
        String activeId = activeConversationId.get(); String activeType = activeConversationType.get();
        if (content == null || content.trim().isEmpty() || activeId == null || activeType.equals("NONE") || chatClient == null) return;
        String formattedMessage; String conversationKey = activeId; String nameOnly = activeId.substring(activeId.indexOf("_") + 1);
        if (activeType.equals("USER")) { formattedMessage = "MSG:" + nameOnly + ":" + content; }
        else if (activeType.equals("GROUP")) { formattedMessage = "GROUP_MSG:" + nameOnly + ":" + content; }
        else { return; }
        chatClient.sendMessage(formattedMessage);
        Message sentMessage = new Message(this.username, content, true, false);
        ObservableList<Message> history = getHistoryFor(conversationKey);
        Platform.runLater(() -> { history.add(sentMessage); activeMessages.add(sentMessage); });
    }

    public void requestGroupCreate(String groupName, ObservableList<String> members) {
        if (groupName == null || groupName.trim().isEmpty() || chatClient == null) return;
        String memberList = String.join(",", members);
        chatClient.sendMessage("GROUP_CREATE:" + groupName.trim() + ":" + memberList);
    }

    /**
     * Chuyển cuộc trò chuyện (ĐÃ THÊM LẠI)
     */
    public void setActiveConversation(String name, String type) {
        if (name == null || type.equals("NONE")) {
            activeConversationId.set(null);
            activeConversationType.set("NONE");
            activeMessages.clear();
            return;
        }
        String conversationKey = type.toLowerCase() + "_" + name;
        if (conversationKey.equals(activeConversationId.get())) return;
        activeConversationId.set(conversationKey);
        activeConversationType.set(type);
        ObservableList<Message> history = getHistoryFor(conversationKey);
        activeMessages.setAll(history);
    }

    /**
     * Lấy lịch sử chat (ĐÃ THÊM LẠI)
     */
    private ObservableList<Message> getHistoryFor(String conversationKey) {
        return conversations.computeIfAbsent(conversationKey, k -> FXCollections.observableArrayList());
    }

    /**
     * Cập nhật trạng thái xác thực (ĐÃ THÊM LẠI)
     */
    private void setAuthStatus(String status) {
        if (Platform.isFxApplicationThread()) { authStatus.set(status); }
        else { Platform.runLater(() -> authStatus.set(status)); }
    }

    // --- Getters (ĐÃ THÊM LẠI) ---
    public StringProperty getAuthStatusProperty() { return authStatus; }
    public ObservableList<Message> getActiveMessages() { return activeMessages; }
    public ObservableList<String> getOnlineUsers() { return onlineUsers; }
    public ObservableList<String> getGroupList() { return groupList; }
    public void requestUserList() { if (chatClient != null) { chatClient.sendMessage("SYSTEM:GET_USERS"); } }
    public String getUsername() { return username; }

    /** Lấy tên cuộc trò chuyện hiện tại (ĐÃ THÊM LẠI VÀ SỬA LỖI) */
    public String getActiveConversationNameOnly() {
        String activeId = activeConversationId.get();
        if (activeId == null) {
            return null; // <-- ĐÂY LÀ PHẦN SỬA LỖI
        }
        return activeId.substring(activeId.indexOf("_") + 1);
    }

    /** Lấy loại cuộc trò chuyện hiện tại (ĐÃ THÊM LẠI) */
    public String getActiveConversationType() {
        return activeConversationType.get();
    }

    // --- Logout (Giữ nguyên) ---
    public void logout() { if (chatClient != null) { chatClient.close(); } conversations.clear(); activeMessages.clear(); onlineUsers.clear(); groupList.clear(); username = null; authStatus.set(null); activeConversationId.set(null); activeConversationType.set("NONE"); pendingFiles.clear(); receivingFiles.forEach((key, stream) -> closeReceivingStream(key, stream, true)); pendingFileOffers.clear(); }
}