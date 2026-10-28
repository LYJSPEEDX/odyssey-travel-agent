package com.example.odyssey;

import static org.junit.Assert.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class InterestGroupsTest {
    @Test public void eachExistingInterestBelongsToExactlyOneGroup() {
        Map<String, String[]> groups = InterestGroups.all();
        assertEquals(12, groups.size());
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, String[]> group : groups.entrySet()) {
            assertTrue(ids.add(group.getKey()));
            for (String child : group.getValue()) assertTrue("Duplicate " + child, ids.add(child));
        }
        assertEquals(PreferenceTags.ALL.keySet(), ids);
    }
}
