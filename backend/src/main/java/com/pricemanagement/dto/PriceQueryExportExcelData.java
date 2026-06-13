package com.pricemanagement.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@ColumnWidth(20)
public class PriceQueryExportExcelData {

    @ExcelProperty("查询日期")
    private LocalDate effectiveDate;

    @ExcelProperty("产品名称")
    private String productName;

    @ExcelProperty("产地")
    private String originName;

    @ExcelProperty("规格")
    private String specification;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("币种")
    private String currency;

    @ExcelProperty("近期价格")
    private BigDecimal currentPrice;

    @ExcelProperty("预算价格")
    private BigDecimal yesterdayPrice;

    @ExcelProperty("较预算")
    private BigDecimal changeAmount;

    @ExcelProperty("较预算比例")
    private String changePercent;

    @ExcelProperty("预算价")
    private BigDecimal budgetPrice;

    @ExcelProperty("月均价")
    private BigDecimal monthlyAveragePrice;

    @ExcelProperty("最后维护价")
    private BigDecimal latestPrice;

    @ExcelProperty("是否有近期价格")
    private String hasPrice;
}
