package com.example.odyssey;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import org.junit.Test;

public class CurrencyPreferenceTest {
    @Test public void countryMapsToCurrencyButUnknownLocationIsNotGuessed() {
        assertEquals("AUD", CurrencyPreference.fromCountry("AU"));
        assertEquals("JPY", CurrencyPreference.fromCountry("JP"));
        assertEquals("USD", CurrencyPreference.fromCountry("US"));
        assertEquals("AU", CurrencyPreference.countryFromLocation("New South Wales, Australia"));
        assertEquals("", CurrencyPreference.countryFromLocation("Sydney"));
        assertEquals("", CurrencyPreference.fromCountry(""));
        assertEquals("USD", CurrencyPreference.preferred(null));
    }
    @Test public void decimalConversionUsesTargetCurrencyPrecision() {
        assertEquals("150.00", ExchangeMath.convert(new BigDecimal("100"), new BigDecimal("1.5"), "AUD", false).toPlainString());
        assertEquals("100.00", ExchangeMath.convert(new BigDecimal("150"), new BigDecimal("1.5"), "USD", true).toPlainString());
        assertEquals("152", ExchangeMath.convert(new BigDecimal("1"), new BigDecimal("151.5"), "JPY", false).toPlainString());
        assertThrows(IllegalArgumentException.class, () -> ExchangeMath.convert(BigDecimal.ONE, BigDecimal.ZERO, "AUD", false));
    }
}
