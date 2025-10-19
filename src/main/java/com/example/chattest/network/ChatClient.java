package com.example.chattest.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;

public class ChatClient {
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    // Callback để báo cho Model khi có tin nhắn
    private final Consumer<String> onMessageReceived;

    public ChatClient(String host, int port, Consumer<String> onMessageReceived) throws IOException {
        this.socket = new Socket(host, port);
        this.writer = new PrintWriter(socket.getOutputStream(), true);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.onMessageReceived = onMessageReceived;
    }

    // Đổi tên từ startListening thành phương thức run
    // để dễ dùng với Thread
    public void startListening() {
        try {
            String serverMessage;
            while (socket.isConnected() && (serverMessage = reader.readLine()) != null) {
                onMessageReceived.accept(serverMessage);
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                System.out.println("Mất kết nối với máy chủ.");
            }
        }
    }

    public void sendMessage(String message) {
        writer.println(message);
    }

    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}