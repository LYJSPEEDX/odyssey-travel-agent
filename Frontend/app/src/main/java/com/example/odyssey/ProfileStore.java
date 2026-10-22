package com.example.odyssey;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** App-private, versioned JSON; writes preserve the previous profile on failure. */
final class ProfileStore {
    private final AtomicFile file;
    private final JSONObject schema;
    ProfileStore(Context context) throws JSONException {
        this(new File(context.getFilesDir(), "profile.json"), ProfileContracts.schema(context));
    }
    ProfileStore(File path, JSONObject schema) { file = new AtomicFile(path); this.schema = schema; }
    JSONObject load() throws IOException, JSONException {
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return null;
        try (java.io.InputStream input = file.openRead();
             java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > ProfileContracts.MAX_JSON_CHARS) throw new IOException("Profile too large");
                bytes.write(buffer, 0, count);
            }
            return ProfileContracts.parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8), schema, "profile");
        }
    }
    void save(int age, String location, String preferences) throws IOException, JSONException {
        location = location.trim(); preferences = preferences.trim();
        JSONObject profile = new JSONObject().put("schema_version", 1).put("age", age)
                .put("residential_location", location).put("travel_preferences", preferences);
        ProfileContracts.check(profile, schema, "profile");
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(profile.toString(2).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream);
        } catch (IOException exception) {
            if (stream != null) file.failWrite(stream);
            throw exception;
        }
    }
}
