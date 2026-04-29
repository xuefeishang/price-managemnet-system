package com.pricemanagement.dto;

import com.pricemanagement.entity.Price;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 价格维护数据DTO：包含当前价格、昨日价格和月均价
 */
@Data
public class PriceWithStatsDTO {

    private Price price;
    private Price yesterdayPrice;
    private BigDecimal monthlyAveragePrice;
    /**
     * 继承的价格：当当天没有维护价格时，取最近一次的价格作为默认值
     * 用于计算昨日价格、月均价和价格变化，但不显示在输入框中
     */
    private BigDecimal inheritedPrice;
    /**
     * 继承的预算价格：当当天没有维护预算价格时，取最近一次的预算价格
     * 用于预算价格的继承显示
     */
    private BigDecimal inheritedBudgetPrice;

    public PriceWithStatsDTO(Price price, Price yesterdayPrice, BigDecimal monthlyAveragePrice) {
        this.price = price;
        this.yesterdayPrice = yesterdayPrice;
        this.monthlyAveragePrice = monthlyAveragePrice;
    }

    public PriceWithStatsDTO(Price price, Price yesterdayPrice, BigDecimal monthlyAveragePrice, BigDecimal inheritedPrice) {
        this.price = price;
        this.yesterdayPrice = yesterdayPrice;
        this.monthlyAveragePrice = monthlyAveragePrice;
        this.inheritedPrice = inheritedPrice;
    }

    public PriceWithStatsDTO(Price price, Price yesterdayPrice, BigDecimal monthlyAveragePrice, BigDecimal inheritedPrice, BigDecimal inheritedBudgetPrice) {
        this.price = price;
        this.yesterdayPrice = yesterdayPrice;
        this.monthlyAveragePrice = monthlyAveragePrice;
        this.inheritedPrice = inheritedPrice;
        this.inheritedBudgetPrice = inheritedBudgetPrice;
    }
}
