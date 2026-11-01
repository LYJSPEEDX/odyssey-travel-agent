package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;

/** QA 截图工具。 */
final class EditorialLayoutTest {
    static void saveScreen(Context context, String filename) {
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Device screenshot available", image);
        File folder = new File(context.getExternalFilesDir(null), "editorial-previews");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        try (FileOutputStream file = new FileOutputStream(new File(folder, filename))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, file));
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally { image.recycle(); }
    }
    private EditorialLayoutTest() { }
}
