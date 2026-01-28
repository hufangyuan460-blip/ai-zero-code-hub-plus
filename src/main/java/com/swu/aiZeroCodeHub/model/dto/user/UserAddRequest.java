package com.swu.aiZeroCodeHub.model.dto.user;

import com.mybatisflex.annotation.Column;
import lombok.Data;

import java.io.Serializable;

@Data
public class UserAddRequest implements Serializable {
    private String userAccount;


    private String userPassword;


    private String userName;

    //头像
    private String userAvatar;

    //简介
    private String userProfile;


    private String userRole;

    private static final long serialVersionUID = 1L;
}
