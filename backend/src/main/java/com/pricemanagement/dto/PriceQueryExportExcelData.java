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

    @ExcelProperty("当日售价")
    private BigDecimal currentPrice;

    @ExcelProperty("昨日售价")
    private BigDecimal yesterdayPrice;

    @ExcelProperty("较昨日")
    private BigDecimal changeAmount;

    @ExcelProperty("较昨日比例")
    private String changePercent;

    @ExcelProperty("预算价")
    private BigDecimal budgetPrice;

    @ExcelProperty("月均价")
    private BigDecimal monthlyAveragePrice;

    @ExcelProperty("最近有效价")
    private BigDecimal latestPrice;

    @ExcelProperty("当日是否报价")
    private String hasPrice;
}
