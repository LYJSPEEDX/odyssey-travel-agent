package com.example.odyssey;

import static org.junit.Assert.*;
import org.junit.Test;

public class MockInputAssessmentTest {
    @Test public void demoRecognisesExampleCitiesButDoesNotTrustUnknownText() {
        assertTrue(MockInputAssessment.acceptable(0, "Perth"));
        assertFalse(MockInputAssessment.acceptable(0, "asdfghjkl"));
        assertFalse(MockInputAssessment.acceptable(1, "Springfield"));
    }
    @Test public void datesAndFreeTextNeedEnoughMeaningToContinue() {
        assertTrue(MockInputAssessment.acceptable(2, "not fixed"));
        assertFalse(MockInputAssessment.acceptable(2, "xyz"));
        assertFalse(MockInputAssessment.acceptable(3, "????"));
        assertTrue(MockInputAssessment.acceptable(3, "Two friends"));
        assertFalse(MockInputAssessment.acceptable(4, "500"));
    }
}
