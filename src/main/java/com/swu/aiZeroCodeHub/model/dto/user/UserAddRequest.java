package com.swu.aiZeroCodeHub.model.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserAddRequest implements Serializable {

    private String userAccount;

    private String userName;

    private String userPassword;

    private String userAvatar;

    private String userProfile;

    private String userRole;

    private static final long serialVersionUID = 1L;
}
