package com.example.chattest.controller;

import com.example.chattest.model.ClientModel;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode; // <-- Đảm bảo bạn có import này
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class CreateGroupController {

    @FXML private TextField groupNameField;
    @FXML private ListView<String> userListView;
    @FXML private Button createButton;

    private ClientModel model = ClientModel.getInstance();

    @FXML
    public void initialize() { // <-- CHỈ CÓ MỘT HÀM initialize()
        // Lấy danh sách user online từ Model và đưa vào ListView
        userListView.setItems(model.getOnlineUsers());

        // Đặt chế độ chọn nhiều người
        userListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    @FXML
    void onFinalCreateClick(ActionEvent event) {
        String groupName = groupNameField.getText();

        // Lấy danh sách các user được chọn
        ObservableList<String> selectedMembers = userListView.getSelectionModel().getSelectedItems();

        if (groupName == null || groupName.trim().isEmpty()) {
            // (Bạn có thể thêm Alert báo lỗi)
            System.err.println("Ten nhom khong duoc de trong");
            return;
        }

        // Gọi hàm mới trong Model
        model.requestGroupCreate(groupName, selectedMembers);

        // Đóng cửa sổ dialog
        closeDialog();
    }
    @FXML
    void onCancelClick(ActionEvent event) {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) createButton.getScene().getWindow();
        stage.close();
    }
}