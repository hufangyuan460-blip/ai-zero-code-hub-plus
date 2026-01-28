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