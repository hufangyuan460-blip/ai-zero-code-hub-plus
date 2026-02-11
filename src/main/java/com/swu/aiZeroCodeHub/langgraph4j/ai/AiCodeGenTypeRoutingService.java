package com.swu.aiZeroCodeHub.langgraph4j.ai;

import cn.hutool.core.util.StrUtil;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import org.springframework.stereotype.Service;

@Service
public class AiCodeGenTypeRoutingService {

    public CodeGenTypeEnum routeCodeGenType(String prompt) {
        String text = StrUtil.blankToDefault(prompt, "");
        String lower = text.toLowerCase();
        if (lower.contains("vue") || lower.contains("vite") || text.contains("工程") || text.contains("项目")) {
            return CodeGenTypeEnum.VUE_PROJECT;
        }
        if (text.contains("多文件") || text.contains("multi") || text.contains("组件")) {
            return CodeGenTypeEnum.MULTI_FILE;
        }
        return CodeGenTypeEnum.HTML;
    }
}
