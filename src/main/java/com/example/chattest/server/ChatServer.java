package com.example.chattest.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ChatServer {

    private static final int PORT = 12345;
    private static Map<String, ClientHandler> userHandlers = new ConcurrentHashMap<>();
    private static Map<String, Set<ClientHandler>> onlineGroupMembers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("May chu chat (DATABASE MySQL - Hien thi nhom dung) dang chay tren cong " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientThread = new ClientHandler(clientSocket);
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Loi may chu: " + e.getMessage());
        }
    }

    // (broadcastSystemMessage giữ nguyên)
    private static void broadcastSystemMessage(String message, ClientHandler excludeHandler) {
        for (ClientHandler handler : userHandlers.values()) {
            if (handler != excludeHandler) {
                handler.writer.println(message);
            }
        }
    }

    // --- THAY ĐỔI: Hàm này không còn cần thiết nữa ---
    // private static void broadcastGroupList() { ... }
    // --- KẾT THÚC THAY ĐỔI ---

    // Luồng xử lý cho từng client
    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter writer;
        private BufferedReader reader;
        private String username;

        public ClientHandler(Socket socket) { this.socket = socket; }

        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(socket.getOutputStream(), true);

                boolean isAuthenticated = false;
                long startTime = System.currentTimeMillis();
                boolean hasAttemptedAuth = false;

                // (Vòng lặp xác thực giữ nguyên)
                while (!isAuthenticated && (System.currentTimeMillis() - startTime) < 10000) {
                    if (reader.ready()) {
                        String authMessage = reader.readLine();
                        if (authMessage == null) break;
                        hasAttemptedAuth = true;
                        if (authMessage.startsWith("REGISTER:")) { handleRegister(authMessage); break; }
                        else if (authMessage.startsWith("LOGIN:")) {
                            isAuthenticated = handleLogin(authMessage);
                            if (!isAuthenticated) { break; }
                        } else { writer.println("SYSTEM:Loi - Giao thuc xac thuc khong hop le."); break; }
                    }
                    Thread.sleep(50);
                }
                if (!isAuthenticated) {
                    if (!hasAttemptedAuth) { writer.println("SYSTEM:Loi - Chua xac thuc. Dong ket noi."); }
                    return;
                }

                // BƯỚC 2: VÀO PHÒNG CHAT
                System.out.println("Nguoi dung da vao chat: " + this.username);
                String message;
                while ((message = reader.readLine()) != null) {
                    System.out.println(this.username + " gui: " + message);
                    if (message.startsWith("MSG:")) { handlePrivateMessage(message); }
                    else if (message.equals("SYSTEM:GET_USERS")) { handleGetUserList(); }
                    else if (message.startsWith("GROUP_CREATE:")) { handleGroupCreate(message); } // Gọi hàm mới
                    else if (message.startsWith("GROUP_MSG:")) { handleGroupMessage(message); }
                    else { writer.println("SYSTEM:Loi - Giao thuc khong hop le."); }
                }
            } catch (IOException e) {
                System.out.println("Nguoi dung " + (username != null ? username : "") + " da ngat ket noi.");
            } catch (InterruptedException e) { /* Lỗi từ Thread.sleep */ }
            finally {
                // (Khối finally giữ nguyên)
                if (this.username != null) {
                    userHandlers.remove(this.username);
                    for (Set<ClientHandler> members : onlineGroupMembers.values()) { members.remove(this); }
                    broadcastSystemMessage("SYSTEM:USER_LEAVE:" + this.username, this);
                    System.out.println("Da xoa " + this.username + " khoi danh sach online.");
                }
                try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        }

        // (handleRegister giữ nguyên)
        private void handleRegister(String message) {
            String[] parts = message.split(":", 3);
            if (parts.length < 3) { writer.println("SYSTEM:Loi - Dinh dang REGISTER khong hop le."); return; }
            String user = parts[1]; String pass = parts[2];
            if (user.isEmpty() || pass.isEmpty()) { writer.println("SYSTEM:Loi - Ten hoac mat khau khong duoc de trong."); return; }
            boolean success = DatabaseManager.registerUser(user, pass);
            if (success) { writer.println("SYSTEM:REGISTER_SUCCESS:Dang ky thanh cong. Hay dang nhap."); }
            else { writer.println("SYSTEM:Loi - Ten nguoi dung da ton tai hoac loi DB."); }
        }

        /**
         * Xử lý "LOGIN:user:pass" (Sử dụng DatabaseManager)
         */
        private boolean handleLogin(String message) {
            String[] parts = message.split(":", 3);
            if (parts.length < 3) { writer.println("SYSTEM:Loi - Dinh dang LOGIN khong hop le."); return false; }
            String user = parts[1]; String pass = parts[2];

            if (!DatabaseManager.loginUser(user, pass)) {
                writer.println("SYSTEM:Loi - Sai ten dang nhap hoac mat khau.");
                return false;
            }
            if (userHandlers.containsKey(user)) {
                writer.println("SYSTEM:Loi - Tai khoan nay da dang nhap o noi khac.");
                return false;
            }

            this.username = user;
            userHandlers.put(this.username, this);

            // Tải user vào các nhóm online (giữ nguyên logic này)
            Set<String> userGroupsFromDB = DatabaseManager.getGroupsForUser(this.username);
            for (String groupName : userGroupsFromDB) {
                Set<ClientHandler> onlineMembers = onlineGroupMembers.computeIfAbsent(groupName,
                        k -> Collections.synchronizedSet(new HashSet<>()));
                onlineMembers.add(this);
            }

            // Gửi danh sách user online
            handleGetUserList();

            // --- THAY ĐỔI: Gửi danh sách nhóm CỦA RIÊNG USER NÀY ---
            sendPersonalGroupList();
            // --- KẾT THÚC THAY ĐỔI ---

            writer.println("SYSTEM:LOGIN_SUCCESS:" + this.username);
            broadcastSystemMessage("SYSTEM:USER_JOIN:" + this.username, this);
            return true;
        }

        // (handlePrivateMessage và handleGetUserList giữ nguyên)
        private void handlePrivateMessage(String message) {
            try {
                String[] parts = message.substring(4).split(":", 2);
                if (parts.length < 2) { writer.println("SYSTEM:Loi - Dinh dang MSG khong hop le."); return; }
                String recipientUsername = parts[0]; String content = parts[1];
                ClientHandler recipientHandler = userHandlers.get(recipientUsername);
                if (recipientHandler != null) {
                    String formattedMessage = "IN_MSG:" + this.username + ":" + content;
                    recipientHandler.writer.println(formattedMessage);
                } else {
                    writer.println("SYSTEM:Loi - Nguoi dung '" + recipientUsername + "' khong online.");
                }
            } catch (Exception e) { writer.println("SYSTEM:Loi - Khong the xu ly tin nhan."); e.printStackTrace(); }
        }

        private void handleGetUserList() {
            Set<String> allUsers = userHandlers.keySet();
            String userList = allUsers.stream().filter(u -> !u.equals(this.username)).collect(Collectors.joining(","));
            writer.println("SYSTEM:USER_LIST:" + (userList.isEmpty() ? "" : userList));
        }

        /**
         * Gửi danh sách nhóm MÀ USER NÀY LÀ THÀNH VIÊN (từ DB)
         */
        private void sendPersonalGroupList() {
            // Gọi DatabaseManager để lấy nhóm của user này
            Set<String> myGroups = DatabaseManager.getGroupsForUser(this.username);
            String groupList = String.join(",", myGroups);
            writer.println("SYSTEM:GROUP_LIST:" + (groupList.isEmpty() ? "" : groupList));
        }

        /**
         * Xử lý lệnh "GROUP_CREATE:<name>:<member1,member2...>"
         */
        private void handleGroupCreate(String message) {
            String[] parts = message.substring(13).split(":", 2);
            String groupName = parts[0];
            List<String> memberNames = new ArrayList<>(); // Dùng List để giữ thứ tự

            if (groupName.isEmpty() || groupName.contains(",")) {
                writer.println("SYSTEM:Loi - Ten nhom khong hop le."); return;
            }

            if (parts.length > 1 && !parts[1].isEmpty()) {
                memberNames = Arrays.asList(parts[1].split(","));
            }

            // 1. Tạo nhóm trong Database
            boolean success = DatabaseManager.createGroup(groupName, this.username, memberNames);

            if (success) {
                writer.println("SYSTEM:GROUP_CREATE_SUCCESS:" + groupName);

                // --- THAY ĐỔI: Không broadcast list nữa ---
                // broadcastGroupList(); // <-- XÓA DÒNG NÀY

                // 3. Cập nhật Map online VÀ gửi list cá nhân cho thành viên online
                Set<ClientHandler> onlineMembersOfNewGroup = onlineGroupMembers.computeIfAbsent(groupName,
                        k -> Collections.synchronizedSet(new HashSet<>()));

                // Thêm người tạo (chắc chắn online)
                onlineMembersOfNewGroup.add(this);
                this.sendPersonalGroupList(); // Gửi list cập nhật cho người tạo

                // Thêm và thông báo cho các thành viên được mời (nếu họ online)
                for (String memberName : memberNames) {
                    ClientHandler memberHandler = userHandlers.get(memberName);
                    if (memberHandler != null && memberHandler != this) { // Nếu online và không phải người tạo
                        onlineMembersOfNewGroup.add(memberHandler);
                        // Gửi list nhóm CÁ NHÂN cập nhật cho thành viên này
                        memberHandler.sendPersonalGroupList();
                    }
                }
                // --- KẾT THÚC THAY ĐỔI ---

            } else {
                writer.println("SYSTEM:Loi - Khong the tao nhom (ten da ton tai hoac loi DB).");
            }
        }

        // (handleGroupMessage giữ nguyên)
        private void handleGroupMessage(String message) {
            try {
                String[] parts = message.substring(10).split(":", 2);
                if (parts.length < 2) { writer.println("SYSTEM:Loi - Dinh dang GROUP_MSG khong hop le."); return; }
                String groupName = parts[0]; String content = parts[1];
                Set<ClientHandler> onlineMembers = onlineGroupMembers.get(groupName);

                if (onlineMembers != null && !onlineMembers.isEmpty()) {
                    // Kiểm tra xem người gửi có phải là thành viên không (an toàn hơn)
                    if (onlineMembers.contains(this)) {
                        String formattedMessage = "IN_GROUP_MSG:" + groupName + ":" + this.username + ":" + content;
                        for (ClientHandler member : onlineMembers) {
                            member.writer.println(formattedMessage);
                        }
                    } else {
                        writer.println("SYSTEM:Loi - Ban khong phai thanh vien cua nhom '" + groupName + "'.");
                    }
                } else {
                    writer.println("SYSTEM:Loi - Nhom khong ton tai hoac khong co ai online.");
                }
            } catch (Exception e) {
                writer.println("SYSTEM:Loi - Khong the gui tin nhan nhom."); e.printStackTrace();
            }
        }
    }
}