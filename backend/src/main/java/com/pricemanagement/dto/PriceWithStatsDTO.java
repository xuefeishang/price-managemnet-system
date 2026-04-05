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

    public PriceWithStatsDTO(Price price, Price yesterdayPrice, BigDecimal monthlyAveragePrice) {
        this.price = price;
        this.yesterdayPrice = yesterdayPrice;
        this.monthlyAveragePrice = monthlyAveragePrice;
    }
}
