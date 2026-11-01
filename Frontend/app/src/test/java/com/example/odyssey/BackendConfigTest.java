package com.example.odyssey;

import static org.junit.Assert.*;
import org.junit.Test;

public class BackendConfigTest {
    @Test public void supportsEmulatorAndHttpsBasePaths() {
        assertEquals("http://10.0.2.2:8000", BackendConfig.normalize(" http://10.0.2.2:8000/ ", true));
        assertEquals("https://example.com/api", BackendConfig.normalize("https://example.com/api/", false));
    }
    @Test public void releaseRejectsCleartextAndNonHttpSchemes() {
        rejects("http://localhost:8000", false);
        rejects("file:///tmp/api", true);
        rejects("ftp://example.com", true);
    }
    @Test public void rejectsCredentialsQueryFragmentsAndInvalidPorts() {
        for (String url : new String[]{"https://user:secret@example.com", "https://example.com?token=x",
                "https://example.com#token", "https://example.com:0", "https://example.com:99999", "", "example.com"})
            rejects(url, true);
    }
    private void rejects(String url, boolean http) {
        try { BackendConfig.normalize(url, http); fail("Accepted " + url); }
        catch (IllegalArgumentException expected) { }
    }
}
