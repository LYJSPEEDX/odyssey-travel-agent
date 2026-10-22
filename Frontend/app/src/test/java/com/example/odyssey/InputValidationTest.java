package com.example.odyssey;

import org.junit.Test;
import static org.junit.Assert.*;

public class InputValidationTest {
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
    @Test public void catalogIsCompleteUniqueAndImmutable() {
        assertEquals(60, PreferenceTags.ALL.size());
        assertEquals(60, new java.util.HashSet<>(PreferenceTags.ALL.keySet()).size());
        assertEquals("Diving", PreferenceTags.ALL.get("DIVING"));
        assertEquals("Snorkeling", PreferenceTags.ALL.get("SNORKELING"));
        assertEquals("Surfing", PreferenceTags.ALL.get("SURFING"));
        assertThrows(UnsupportedOperationException.class, () -> PreferenceTags.ALL.clear());
    }
}
