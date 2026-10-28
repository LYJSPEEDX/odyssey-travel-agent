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
    // AtomicFile 的读取会清理 .new；所有实例共用锁，避免读取打断尚未完成的提交。
    private static final Object IO_LOCK = new Object();
    private final AtomicFile file;
    private final JSONObject schema;
    ProfileStore(Context context) throws JSONException {
        this(new File(context.getFilesDir(), "profile.json"), ProfileContracts.schema(context));
    }
    ProfileStore(File path, JSONObject schema) { file = new AtomicFile(path); this.schema = schema; }
    JSONObject load() throws IOException, JSONException {
        synchronized (IO_LOCK) { return read(); }
    }
    private JSONObject read() throws IOException, JSONException {
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return null;
        try (java.io.InputStream input = file.openRead();
             java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > ProfileContracts.MAX_JSON_CHARS) throw new IOException("Profile too large");
                bytes.write(buffer, 0, count);
            }
            String raw = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            ItineraryJson.preflight(raw, ProfileContracts.MAX_JSON_CHARS);
            JSONObject profile = new JSONObject(raw);
            if (Integer.valueOf(2).equals(profile.opt("schema_version")) || Integer.valueOf(3).equals(profile.opt("schema_version"))) {
                try { return validated(profile); }
                catch (IllegalArgumentException exception) { throw new JSONException(exception.getMessage()); }
            }
            ProfileContracts.check(profile, schema, "profile");
            return profile;
        }
    }
    void save(int age, String location, String preferences) throws IOException, JSONException {
        location = InputValidation.text(location); preferences = InputValidation.text(preferences);
        JSONObject profile = new JSONObject().put("schema_version", 1).put("age", age)
                .put("residential_location", location).put("travel_preferences", preferences);
        ProfileContracts.check(profile, schema, "profile");
        write(profile);
    }
    void save(JSONObject profile) throws IOException, JSONException { write(validated(profile)); }
    static boolean isComplete(JSONObject profile) {
        if (profile == null) return false;
        try { JSONObject checked = validated(profile); return checked.optInt("schema_version") == 2 || checked.optBoolean("onboarding_complete"); }
        catch (JSONException | IllegalArgumentException exception) { return false; }
    }
    /** v3 支持选填生日和完成标记；继续读取旧 v2，旧版服务契约保持原样。 */
    private static JSONObject validated(JSONObject source) throws JSONException {
        if (source == null) throw new IllegalArgumentException("Missing profile");
        boolean optional = Integer.valueOf(3).equals(source.opt("schema_version"));
        if (!optional && !Integer.valueOf(2).equals(source.opt("schema_version")))
            throw new IllegalArgumentException("Unsupported profile version");
        JSONObject profile = new JSONObject().put("schema_version", optional ? 3 : 2);
        for (String key : new String[]{"name", "gender", "nationality", "residential_location"})
            profile.put(key, optional ? InputValidation.typedText(source.opt(key), 200, key)
                    : InputValidation.requiredText(source.opt(key), 200, key));
        String gender = profile.getString("gender");
        if (!(optional && gender.isEmpty()) && !java.util.Arrays.asList("Woman", "Man", "Non-binary", "Self-described", "Prefer not to say", "Prefer not to tell").contains(gender))
            throw new IllegalArgumentException("Select a gender option");
        if (optional) {
            // 完成状态独立于是否披露个人资料：全空也能完成引导，不会反复弹出注册页。
            if (!(source.opt("onboarding_complete") instanceof Boolean))
                throw new IllegalArgumentException("Invalid completion state");
            profile.put("onboarding_complete", source.getBoolean("onboarding_complete"));
            profile.put("birth_date", InputValidation.birthDate(source.opt("birth_date")));
            // 旧年龄仅兼容保存，不凭年龄猜出生日期；用户主动不透露时不保留此值。
            if (source.has("legacy_age")) {
                Object legacy = source.get("legacy_age");
                if (!(legacy instanceof Integer)) throw new IllegalArgumentException("Invalid legacy age");
                profile.put("legacy_age", InputValidation.age(legacy.toString()));
            }
        } else {
            Object age = source.opt("age");
            if (!(age instanceof Integer)) throw new IllegalArgumentException("Age must be an integer");
            profile.put("age", InputValidation.age(age.toString()));
        }
        profile.put("travel_preferences", InputValidation.optionalText(source.opt("travel_preferences"), 2000, "Preferences"));
        org.json.JSONArray tags = source.optJSONArray("preference_tags");
        if (tags == null || tags.length() > PreferenceTags.ALL.size()) throw new IllegalArgumentException("Invalid preference tags");
        org.json.JSONArray normalized = new org.json.JSONArray();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < tags.length(); i++) {
            String id = InputValidation.requiredText(tags.get(i), 50, "Preference tag");
            if (!PreferenceTags.ALL.containsKey(id) || !seen.add(id)) throw new IllegalArgumentException("Invalid preference tag");
            normalized.put(id);
        }
        return profile.put("preference_tags", normalized);
    }
    private void write(JSONObject profile) throws IOException, JSONException {
        byte[] encoded = profile.toString(2).getBytes(StandardCharsets.UTF_8);
        synchronized (IO_LOCK) { writeAtomic(encoded); }
    }
    private void writeAtomic(byte[] encoded) throws IOException {
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(encoded);
            file.finishWrite(stream);
        } catch (IOException exception) {
            if (stream != null) file.failWrite(stream);
            throw exception;
        }
    }
}
