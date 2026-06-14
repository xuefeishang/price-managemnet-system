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

    @ExcelProperty("最新有效价")
    private BigDecimal latestPrice;

    @ExcelProperty("最新有效价日期")
    private LocalDate latestPriceDate;

    @ExcelProperty("上期有效价")
    private BigDecimal previousPrice;

    @ExcelProperty("上期有效价日期")
    private LocalDate previousPriceDate;

    @ExcelProperty("较上期变动额")
    private BigDecimal previousChangeAmount;

    @ExcelProperty("较上期变动率")
    private String previousChangePercent;

    @ExcelProperty("指标基准年度预算价")
    private BigDecimal budgetPrice;

    @ExcelProperty("较预算变动额")
    private BigDecimal budgetChangeAmount;

    @ExcelProperty("较预算变动率")
    private String budgetChangePercent;

    @ExcelProperty("月均价")
    private BigDecimal monthlyAveragePrice;

    @ExcelProperty("上月均价")
    private BigDecimal previousMonthAveragePrice;

    @ExcelProperty("月环比")
    private String monthOverMonthPercent;

    @ExcelProperty("去年同期均价")
    private BigDecimal lastYearSamePeriodAveragePrice;

    @ExcelProperty("年同比")
    private String yearOverYearPercent;

    @ExcelProperty("是否有近期价格")
    private String hasPrice;
}
