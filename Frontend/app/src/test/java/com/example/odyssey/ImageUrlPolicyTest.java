package com.example.odyssey;
import org.junit.Test;
import static org.junit.Assert.*;
public class ImageUrlPolicyTest {
    @Test public void acceptsOnlyApprovedHttpsOrigins() {
        assertNotNull(ImageUrlPolicy.accept(" https://images.unsplash.com/photo-a?w=640 "));
        assertNotNull(ImageUrlPolicy.accept("https://images.pexels.com/photos/1.jpg"));
        for (Object value : new Object[]{null, 42, "http://images.unsplash.com/a", "file:///a", "https://user@images.unsplash.com/a", "https://images.unsplash.com.evil.test/a", "https://127.0.0.1/a", "https://images.unsplash.com:444/a", "https://localhost/a"}) assertNull(String.valueOf(value), ImageUrlPolicy.accept(value));
    }
    @Test public void redirectsCannotEscapePolicy() {
        assertNull(ImageUrlPolicy.redirect("https://images.unsplash.com/a", "https://192.168.1.1/a"));
        assertNull(ImageUrlPolicy.redirect("https://images.unsplash.com/a", "http://images.unsplash.com/b"));
        assertEquals("https://images.unsplash.com/b", ImageUrlPolicy.redirect("https://images.unsplash.com/a", "/b"));
    }
}
