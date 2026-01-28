package com.swu.aiZeroCodeHub.model.dto.user;

import com.swu.aiZeroCodeHub.common.dto.PageRequest;
import lombok.Data;

import java.io.Serializable;

@Data
public class UserQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private String userAccount;


    private String userName;


    //简介
    private String userProfile;


    private String userRole;

    private static final long serialVersionUID = 1L;

}
