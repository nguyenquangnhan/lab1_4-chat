package com.example.chattest.server;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class DatabaseManager {

    // --- CẤU HÌNH DATABASE ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/chat_test";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "1234"; // Thay mật khẩu của bạn
    // ---------------------------

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    private static int getUserId(String username) {
        // Bảng users không phải từ khóa, không cần backtick
        String sql = "SELECT user_id FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("user_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static boolean registerUser(String username, String password) {
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean loginUser(String username, String password) {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                return BCrypt.checkpw(password, storedHash);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * NÂNG CẤP: Tạo nhóm mới và thêm NHIỀU thành viên (Đã thêm backtick)
     */
    public static boolean createGroup(String groupName, String creatorUsername, java.util.List<String> memberUsernames) {
        int creatorId = getUserId(creatorUsername);
        if (creatorId == -1) return false;

        // --- SỬA Ở ĐÂY: Thêm backtick `` quanh `groups` ---
        String sqlGroup = "INSERT INTO `groups` (group_name, creator_id) VALUES (?, ?)";
        // Bảng group_members không phải từ khóa
        String sqlMember = "INSERT INTO group_members (user_id, group_id) VALUES (?, ?)";

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            // 1. Tạo nhóm
            PreparedStatement pstmtGroup = conn.prepareStatement(sqlGroup, Statement.RETURN_GENERATED_KEYS);
            pstmtGroup.setString(1, groupName);
            pstmtGroup.setInt(2, creatorId);
            pstmtGroup.executeUpdate(); // <-- Dòng 112 gây lỗi nằm ở đây

            // 2. Lấy group_id
            int groupId = -1;
            ResultSet rsKeys = pstmtGroup.getGeneratedKeys();
            if (rsKeys.next()) {
                groupId = rsKeys.getInt(1);
            } else {
                conn.rollback();
                return false;
            }

            // 3. Chuẩn bị Batch Insert thành viên
            PreparedStatement pstmtMember = conn.prepareStatement(sqlMember);
            // 3a. Thêm người tạo
            pstmtMember.setInt(1, creatorId);
            pstmtMember.setInt(2, groupId);
            pstmtMember.addBatch();
            // 3b. Thêm các thành viên được mời
            for (String memberName : memberUsernames) {
                int memberId = getUserId(memberName);
                if (memberId != -1 && memberId != creatorId) {
                    pstmtMember.setInt(1, memberId);
                    pstmtMember.setInt(2, groupId);
                    pstmtMember.addBatch();
                }
            }

            // 4. Thực thi Batch Insert
            pstmtMember.executeBatch();
            // 5. Hoàn tất
            conn.commit();
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            return false;
        } finally {
            try { if (conn != null) conn.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    /**
     * Lấy tất cả tên nhóm (Đã thêm backtick)
     */
    public static Set<String> getAllGroupNames() {
        Set<String> groupNames = new HashSet<>();
        // --- SỬA Ở ĐÂY ---
        String sql = "SELECT group_name FROM `groups`";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                groupNames.add(rs.getString("group_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return groupNames;
    }

    /**
     * Lấy tất cả tên thành viên của một nhóm (Đã thêm backtick)
     */
    public static Set<String> getGroupMembers(String groupName) {
        Set<String> memberNames = new HashSet<>();
        // --- SỬA Ở ĐÂY ---
        String sql = "SELECT u.username FROM users u " +
                "JOIN group_members gm ON u.user_id = gm.user_id " +
                "JOIN `groups` g ON gm.group_id = g.group_id " + // Thêm backtick
                "WHERE g.group_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                memberNames.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return memberNames;
    }

    /**
     * Lấy các nhóm mà một user tham gia (Đã thêm backtick)
     */
    public static Set<String> getGroupsForUser(String username) {
        Set<String> groupNames = new HashSet<>();
        // --- SỬA Ở ĐÂY ---
        String sql = "SELECT g.group_name FROM `groups` g " + // Thêm backtick
                "JOIN group_members gm ON g.group_id = gm.group_id " +
                "JOIN users u ON gm.user_id = u.user_id " +
                "WHERE u.username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString("group_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return groupNames;
    }
}