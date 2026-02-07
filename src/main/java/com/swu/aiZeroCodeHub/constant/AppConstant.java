package com.swu.aiZeroCodeHub.constant;

public interface AppConstant {
     //应用生成目录 项目根目录+/tmp/code_output
     String CODE_OUTPUT_ROOT_DIR=System.getProperty("user.dir")+"/tmp/code_output";
     //应用部署目录
     String CODE_DEPLOY_ROOT_DIR=System.getProperty("user.dir")+"/tmp/code_deploy";
     //应用部署域名
     String CODE_DEPLOY_HOST="http://localhost:8123/app";
}
