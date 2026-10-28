package com.example.odyssey;

import static org.junit.Assert.*;
import org.junit.Test;

public class GenerationDemoTest {
    @Test public void simulatedStagesAreBoundedAndDoNotRequireRealTimeWaiting() {
        assertEquals(0, GenerationDemo.stage(0));
        assertEquals(0, GenerationDemo.stage(11999));
        assertEquals(1, GenerationDemo.stage(12000));
        assertEquals(2, GenerationDemo.stage(40000));
        assertEquals(3, GenerationDemo.stage(72000));
        assertEquals(4, GenerationDemo.stage(108000));
        assertEquals(4, GenerationDemo.stage(140000));
    }
}
