package com.example.chattest.model;

public class Message {
    private final String username;
    private final String content;
    private final boolean isSentByMe;
    private final boolean isSystemMessage;

    // --- THÊM CÁC TRƯỜNG CHO FILE OFFER ---
    private boolean isFileOffer = false;
    private long fileSize = 0;
    // --- KẾT THÚC THÊM ---

    // Constructor giữ nguyên
    public Message(String username, String content, boolean isSentByMe, boolean isSystemMessage) {
        this.username = username;
        this.content = content;
        this.isSentByMe = isSentByMe;
        this.isSystemMessage = isSystemMessage;
    }

    // Getters giữ nguyên
    public String getUsername() { return username; }
    public String getContent() { return content; }
    public boolean isSentByMe() { return isSentByMe; }
    public boolean isSystemMessage() { return isSystemMessage; }

    // --- THÊM CÁC GETTER/SETTER CHO FILE OFFER ---
    public boolean isFileOffer() {
        return isFileOffer;
    }

    public void setFileOffer(boolean fileOffer) {
        isFileOffer = fileOffer;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    // --- KẾT THÚC THÊM ---
}