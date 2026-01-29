package com.swu.aiZeroCodeHub.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.HtmlCodeResult;
import com.swu.aiZeroCodeHub.model.vo.ai.MultiFileCodeResult;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class CodeFileSaver {
    private static final String FILE_SAV_ROOT_DIR=System.getProperty("user.dir")+"/tmp/code_output";

    /**
     * 保存单文件结果到本地
     * @param htmlCodeResult
     * @return
     */
    public static File saveHtmlCodeResult(HtmlCodeResult htmlCodeResult) {
        String baseDirPath=buildUniqueDir(CodeGenTypeEnum.HTML.getValue());
        writeToFile(baseDirPath,"index.html", htmlCodeResult.getHtmlCode());
        return new File(baseDirPath);
    }


    /**
     * 保存多文件代码结果到本地
     * @param multiFileCodeResult
     * @return
     */
    public static File saveMultiFileCodeResult(MultiFileCodeResult multiFileCodeResult) {
         String baseDirPath=buildUniqueDir(CodeGenTypeEnum.MULTI_FILE.getValue());
         writeToFile(baseDirPath,"index.html", multiFileCodeResult.getHtmlCode());
         writeToFile(baseDirPath,"style.css", multiFileCodeResult.getHtmlCode());
         writeToFile(baseDirPath,"script.js", multiFileCodeResult.getHtmlCode());
         return new File(baseDirPath);
    }

    /**
     * 构建唯一目录路径：/tmp/code_output/bizType_雪花ID
     * @param bizType
     * @return
     */
    private static String buildUniqueDir(String bizType) {
        String uniqueDirName = StrUtil.format("{}_{}", bizType, IdUtil.getSnowflakeNextIdStr());
        String dirPath=FILE_SAV_ROOT_DIR+File.separator+uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 写入单个文件
     * @param dirPath
     * @param filename
     * @param content
     */
    private static void writeToFile(String dirPath,String filename,String content){
         String filePath=dirPath+File.separator+filename;
         FileUtil.writeString(content,filePath, StandardCharsets.UTF_8);
    }
}
