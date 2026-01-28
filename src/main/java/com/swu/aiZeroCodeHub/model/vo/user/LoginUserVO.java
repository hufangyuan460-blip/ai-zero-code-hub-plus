package com.swu.aiZeroCodeHub.model.vo.user;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class LoginUserVO implements Serializable {
    private Long id;
    private String userAccount;
    private String userPassword;
    //头像
    private String userAvatar;
    //昵称
    private String userProfile;
    //角色
    private String userRole;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private static final long serialVersionUID = 1L;
}
