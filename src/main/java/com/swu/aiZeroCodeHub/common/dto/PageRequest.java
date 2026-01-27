package com.swu.aiZeroCodeHub.common.dto;

import lombok.Data;

@Data
public class PageRequest {
    //当前页号
    private int pageNumber=1;

    private int pageSize=10;
    //排序字段
    private String sortField;
    //排序顺序，默认降序
    private String sortOrder="descend";

}
