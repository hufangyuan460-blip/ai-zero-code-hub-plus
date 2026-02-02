package com.swu.aiZeroCodeHub.generator;

import cn.hutool.core.lang.Dict;
import cn.hutool.setting.yaml.YamlUtil;
import com.mybatisflex.codegen.Generator;
import com.mybatisflex.codegen.config.GlobalConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;

public class MyBatisCodeGenerator {
    //需要生成的表名
    private static final String[] TABLE_NAMES = {"chat_history"};

    public static void main(String[] args) {
        //获取数据源信息
        Dict dict= YamlUtil.loadByPath("application.yaml");
        Map<String,Object> dataSourceConfig=dict.getByPath("spring.datasource");
        String url=dataSourceConfig.get("url").toString();
        String userName = dataSourceConfig.get("username").toString();
        String password =  "Dyhsqq2011956596";     //dataSourceConfig.get("password").toString();
        //配置数据源
        HikariDataSource dataSource=new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(userName);
        dataSource.setPassword(password);

        //创建配置内容
       GlobalConfig globalConfig=createGlobalConfig();

        Generator generator=new Generator(dataSource,globalConfig);
        generator.generate();

    }

    public static GlobalConfig createGlobalConfig(){
        GlobalConfig globalConfig=new GlobalConfig();
        globalConfig.getPackageConfig()
                //设置根包
                .setBasePackage("com.swu.aiZeroCodeHub.generatorResult");
        //
        globalConfig.getStrategyConfig()
                .setGenerateTable(TABLE_NAMES)
                //逻辑删除的默认字段名称
                .setLogicDeleteColumn("isDelete");
        //设置生成entity并且启用lombok
        globalConfig.enableEntity()
                .setWithLombok(true)
                .setJdkVersion(21);
        //mapper
        globalConfig.enableMapper();
        globalConfig.enableMapperXml();

        //service
        globalConfig.enableService();
        globalConfig.enableServiceImpl();

        //controller
        globalConfig.enableController();

        //设置生成时间和字符串为空
        globalConfig.getJavadocConfig()
                .setAuthor("hxyz61")
                .setSince("");
        return globalConfig;



    }
}
