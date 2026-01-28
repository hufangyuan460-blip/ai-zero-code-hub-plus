package com.swu.aiZeroCodeHub.model.vo.user;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long id;

    private String userAccount;

    private String userName;

    //头像
    private String userAvatar;

    //简介
    private String userProfile;

    private String userRole;

    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;


}
