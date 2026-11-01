package com.example.odyssey;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URI;
import java.util.UUID;

/** Endpoint and anonymous ownership are private and excluded from backup. */
final class BackendConfig {
    static final String FILE = "live_backend";
    static final String DEFAULT_URL = "http://10.0.2.2:8000";
    final SharedPreferences preferences;
    BackendConfig(Context context) { preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    /** 优先用户在设置里填的地址，其次打包时配置的地址（gradle 属性 odysseyBackendUrl），调试版默认连本机。 */
    String url() {
        String packaged = BuildConfig.BACKEND_URL.isEmpty() && BuildConfig.DEBUG ? DEFAULT_URL : BuildConfig.BACKEND_URL;
        return preferences.getString("base_url", packaged);
    }
    /** 局域网地址（含模拟器的 10.0.2.2）在 Android 17 上需要 ACCESS_LOCAL_NETWORK 权限。 */
    static boolean isLocalNetwork(String url) {
        String host;
        try { host = new URI(url).getHost(); } catch (java.net.URISyntaxException error) { return false; }
        return host != null && (host.endsWith(".local") || host.matches("^(10\\.|192\\.168\\.|169\\.254\\.|172\\.(1[6-9]|2\\d|3[01])\\.).*"));
    }
    static String normalize(String input, boolean allowHttp) {
        try {
            URI uri = new URI(input.trim());
            if ((!"https".equals(uri.getScheme()) && !(allowHttp && "http".equals(uri.getScheme())))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || uri.getPort() > 65535 || uri.getPort() == 0)
                throw new IllegalArgumentException();
            String value = uri.toASCIIString();
            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
            return value;
        } catch (Exception error) { throw new IllegalArgumentException(allowHttp
                ? "Enter an HTTP or HTTPS server URL without credentials, query or fragment."
                : "Enter an HTTPS server URL without credentials, query or fragment."); }
    }
    boolean saveUrl(String value) { return preferences.edit().putString("base_url", value).commit(); }
    String key(String endpoint) { return "server_" + UUID.nameUUIDFromBytes(endpoint.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    synchronized String owner(String endpoint) throws java.io.IOException {
        String key = key(endpoint) + "_owner";
        String existing = preferences.getString(key, "");
        if (!existing.isEmpty()) return existing;
        String value = UUID.randomUUID().toString();
        if (!preferences.edit().putString(key, value).commit()) throw new java.io.IOException("Cannot save server session identity.");
        return value;
    }
}
