package com.example.odyssey;

import org.junit.Test;
import static org.junit.Assert.*;

public class InputValidationTest {
    @org.junit.Test public void typedTextRejectsMissingAndWrongTypesButAllowsEmptyStrings() {
        org.junit.Assert.assertEquals("", InputValidation.typedText("", 10, "Value"));
        org.junit.Assert.assertEquals("Sydney", InputValidation.typedText("\u00a0Sydney\u00a0", 10, "Value"));
        for (Object invalid : new Object[]{null, 3, true, "12345678901"}) {
            try { InputValidation.typedText(invalid, 10, "Value"); org.junit.Assert.fail(); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void normalizesUnicodeEdgesWithoutDestroyingParagraphs() {
        assertEquals("Sydney\nArt", InputValidation.text("\u00a0 Sydney\nArt\u2003"));
        assertEquals("", InputValidation.text(null));
    }
    @Test public void rejectsTypesBlankLengthAndInvalidAge() {
        assertThrows(IllegalArgumentException.class, () -> InputValidation.text(5));
        assertThrows(IllegalArgumentException.class, () -> InputValidation.requiredText("\u00a0", 3, "Name"));
        assertThrows(IllegalArgumentException.class, () -> InputValidation.optionalText("abcd", 3, "Name"));
        assertEquals("abc", InputValidation.requiredText(" abc ", 3, "Name"));
        assertEquals(30, InputValidation.age(" 30 "));
        for (String value : new String[]{"0", "121", "abc", "", "1.2"})
            assertThrows(IllegalArgumentException.class, () -> InputValidation.age(value));
    }
    @Test public void optionalBirthdayRejectsImpossibleAndFutureDates() {
        assertEquals("", InputValidation.birthDate(""));
        assertEquals("2000-02-29", InputValidation.birthDate("2000-02-29"));
        for (String date : new String[]{"2001-02-29", "2000-04-31", "3000-01-01", "2000-2-1", "not a date"})
            assertThrows(date, IllegalArgumentException.class, () -> InputValidation.birthDate(date));
    }
    @Test public void catalogIsCompleteUniqueAndImmutable() {
        assertEquals(96, PreferenceTags.ALL.size());
        assertEquals(96, new java.util.HashSet<>(PreferenceTags.ALL.keySet()).size());
        assertEquals("Diving", PreferenceTags.ALL.get("DIVING"));
        assertEquals("Snorkeling", PreferenceTags.ALL.get("SNORKELING"));
        assertEquals("Surfing", PreferenceTags.ALL.get("SURFING"));
        assertThrows(UnsupportedOperationException.class, () -> PreferenceTags.ALL.clear());
    }
}
