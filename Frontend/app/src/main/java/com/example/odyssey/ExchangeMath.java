package com.example.odyssey;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/** 使用十进制运算并按目标货币精度舍入；不获取、不推测汇率。 */
final class ExchangeMath {
    static BigDecimal convert(BigDecimal amount, BigDecimal quotePerBase, String target, boolean inverse) {
        if (amount == null || amount.signum() < 0 || quotePerBase == null || quotePerBase.signum() <= 0)
            throw new IllegalArgumentException("Invalid amount or exchange rate");
        Currency currency = Currency.getInstance(target);
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) throw new IllegalArgumentException("Currency has no supported precision");
        return inverse ? amount.divide(quotePerBase, digits, RoundingMode.HALF_UP)
                : amount.multiply(quotePerBase).setScale(digits, RoundingMode.HALF_UP);
    }
    private ExchangeMath() { }
}
