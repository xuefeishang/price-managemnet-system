
package com.pricemanagement.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

@Data
@ColumnWidth(20)
public class ProductExcelData {

    @ExcelProperty("产品编码")
    private String code;

    @ExcelProperty("产品名称")
    private String name;

    @ExcelProperty("分类编码")
    private String categoryCode;

    @ExcelProperty("规格参数")
    private String specs;

    @ExcelProperty("原价")
    private BigDecimal originalPrice;

    @ExcelProperty("现价")
    private BigDecimal currentPrice;

    @ExcelProperty("成本价")
    private BigDecimal costPrice;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("价格规格")
    private String priceSpec;

    @ExcelProperty("状态(ACTIVE/INACTIVE)")
    private String status;

    @ExcelProperty("描述")
    private String description;

    @ExcelProperty("备注")
    private String remark;
}

