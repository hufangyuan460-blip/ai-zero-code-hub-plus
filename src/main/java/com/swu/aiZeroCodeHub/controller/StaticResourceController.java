package com.swu.aiZeroCodeHub.controller;

import com.swu.aiZeroCodeHub.constant.AppConstant;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/static")
public class StaticResourceController {
    private static final String PREVIEW_ROOT_DIR= AppConstant.CODE_OUTPUT_ROOT_DIR;


}
