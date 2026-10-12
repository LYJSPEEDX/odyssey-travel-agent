package com.example.odyssey;

import static org.junit.Assert.*;
import org.junit.Test;

public class JournalTimeTest {
    @Test public void datesAreHumanReadableAndInvalidDatesStayUnknown() {
        assertEquals("10 Nov", JournalTime.date("2026-11-10"));
        assertEquals("Date pending", JournalTime.date("2026-02-30"));
        assertEquals("Date pending", JournalTime.date(null));
    }
    @Test public void clockUsesEachEndpointsLocalTimeNotDeviceTimezone() {
        assertEquals("06:00", JournalTime.clock("2026-11-10T06:00:00+11:00"));
        assertEquals("14:00", JournalTime.clock("2026-11-09T14:00:00-05:00"));
        assertEquals("—", JournalTime.clock("invalid"));
        assertEquals("—", JournalTime.clock("2026-11-10T25:00:00Z"));
    }
    @Test public void durationsDoNotPretendMovingTimeIncludesLayovers() {
        assertEquals("45 min", JournalTime.duration(45));
        assertEquals("2h", JournalTime.duration(120));
        assertEquals("2h 35m", JournalTime.duration(155));
    }
}
