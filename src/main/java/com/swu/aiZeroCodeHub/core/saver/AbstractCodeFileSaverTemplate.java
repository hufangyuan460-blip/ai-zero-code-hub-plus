package com.swu.aiZeroCodeHub.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.swu.aiZeroCodeHub.constant.AppConstant;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.model.vo.ai.CodeResult;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 抽象保存模板（模板方法）：建目录 → 写文件集合 → 返回目录。
 */
public abstract class AbstractCodeFileSaverTemplate<T extends CodeResult> implements CodeFileSaverTemplate {

    /**
     * 文件保存根目录常量
     */
    protected static final String FILE_SAVE_ROOT_DIR= AppConstant.CODE_OUTPUT_ROOT_DIR;

    protected abstract Class<T> getCodeResultClass();

    protected abstract Map<String, String> buildFiles(T codeResult);

    @Override
    public final File save(CodeResult codeResult, Long appId) {
        if (codeResult == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "代码结果为空");
        }
        T typedResult;
        try {
            typedResult = getCodeResultClass().cast(codeResult);
        } catch (ClassCastException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "代码结果类型不匹配");
        }

        String baseDirPath = buildUniqueDir(getType().getValue(), appId);
        Map<String, String> files = buildFiles(typedResult);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            writeToFile(baseDirPath, entry.getKey(), entry.getValue());
        }
        return new File(baseDirPath);
    }

    private String buildUniqueDir(String bizType, Long appId) {
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "appId 错误");
        }
        String uniqueDirName = StrUtil.format("{}_{}", bizType, appId);
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    private void writeToFile(String dirPath, String filename, String content) {
        String filePath = dirPath + File.separator + filename;
        FileUtil.writeString(content == null ? "" : content, filePath, StandardCharsets.UTF_8);
    }
}
