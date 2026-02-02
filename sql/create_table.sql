-- 用户表
CREATE TABLE IF NOT EXISTS user (
                                    id BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
                                    userAccount VARCHAR(256) NOT NULL COMMENT '账号',
                                    userPassword VARCHAR(512) NOT NULL COMMENT '密码',
                                    userName VARCHAR(256) NULL COMMENT '用户昵称',
                                    userAvatar VARCHAR(1024) NULL COMMENT '用户头像',
                                    userProfile VARCHAR(512) NULL COMMENT '用户简介',
                                    userRole VARCHAR(256) DEFAULT 'user' NOT NULL COMMENT '用户角色：user/admin',
                                    editTime DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '编辑时间',
                                    createTime DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
                                    updateTime DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    isDelete TINYINT DEFAULT 0 NOT NULL COMMENT '是否删除',
                                    UNIQUE KEY uk_userAccount (userAccount),
                                    INDEX idx_userName (userName)
) COMMENT '用户' COLLATE = utf8mb4_unicode_ci;



-- 应用表
CREATE TABLE app (

                     id bigint auto_increment comment 'id' primary key,

                     appName varchar(256) null comment '应用名称',

                     cover varchar(512) null comment '应用封面',

                     initPrompt text null comment '应用初始化的 prompt',

                     codeGenType varchar(64) null comment '代码生成类型（枚举）',

                     deployKey varchar(64) null comment '部署标识',

                     deployedTime datetime null comment '部署时间',

                     priority int default 0 not null comment '优先级',

                     userId bigint not null comment '创建用户id',

                     editTime datetime default CURRENT_TIMESTAMP not null comment '编辑时间',

                     createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',

                     updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新 时间',

                     isDelete tinyint default 0 not null comment '是否删除',

                     UNIQUE KEY uk_deployKey (deployKey),  -- 确保部署标识唯一

                     INDEX idx_appName (appName),          -- 提升基于应用名称的查询性能

                     INDEX idx_userId (userId)             -- 提升基于用户 ID 的查询性能

) comment '应用' collate = utf8mb4_unicode_ci;


# 对话历史表
CREATE TABLE chat_history (
                              id bigint AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
                              message text NOT NULL COMMENT '消息',
                              messageType varchar(32) NOT NULL COMMENT 'user/ai',
                              appId bigint NOT NULL COMMENT '应用id',
                              userId bigint NOT NULL COMMENT '创建用户id',
                              createTime datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
                              updateTime datetime DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              isDelete tinyint DEFAULT 0 NOT NULL COMMENT '是否删除',
                              INDEX idx_appId (appId),
                              INDEX idx_createTime (createTime),
                              INDEX idx_appId_createTime (appId, createTime)
) COMMENT '对话历史' COLLATE = utf8mb4_unicode_ci;