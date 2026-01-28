package com.swu.aiZeroCodeHub.model.dto.user;

import lombok.Data;

@Data
public class UserUpdateRequest {

    private Long id;


    private String userName;

    private String userPassword;

    //头像
    private String userAvatar;

    //简介
    private String userProfile;


    private String userRole;

    private static final long serialVersionUID = 1L;
}
