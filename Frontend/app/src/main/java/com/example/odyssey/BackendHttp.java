package com.example.odyssey;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** One blocking call per worker task. Never retries mutations or follows redirects. */
final class BackendHttp {
    static final int MAX_RESPONSE = 2_000_000;
    interface Transport { JSONObject call(String method, String path, JSONObject body, boolean generation) throws Exception; }
    static final class ApiError extends IOException {
        final int status;
        final String code;
        ApiError(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    }
    private final String endpoint, owner;
    BackendHttp(String endpoint, String owner) { this.endpoint = endpoint; this.owner = owner; }
    JSONObject call(String method, String path, JSONObject body, boolean generation) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15_000);
            // Research can take ten minutes, followed by scheduling. No fabricated progress timer.
            connection.setReadTimeout(generation ? 1_200_000 : 330_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Session-Id", owner);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream stream = connection.getOutputStream()) { stream.write(bytes); }
            }
            int status = connection.getResponseCode();
            String returnedOwner = connection.getHeaderField("X-Session-Id");
            if (returnedOwner == null || !owner.equals(returnedOwner))
                throw new IOException("Server session identity was not preserved. Check the API URL and proxy.");
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String raw = read(stream);
            if (status < 200 || status >= 300) {
                JSONObject value;
                try { value = new JSONObject(raw); } catch (Exception ignored) { value = new JSONObject(); }
                JSONObject detail = value.optJSONObject("detail");
                String code = detail == null ? "HTTP_" + status : detail.optString("code", "HTTP_" + status);
                String message = detail == null ? "Request failed (HTTP " + status + "). Check the server and your inputs."
                        : detail.optString("message", "Request failed (HTTP " + status + ").");
                throw new ApiError(status, code, message);
            }
            if (raw.isEmpty()) return new JSONObject();
            return new JSONObject(raw);
        } finally { connection.disconnect(); }
    }
    private static String read(InputStream input) throws IOException {
        if (input == null) return "";
        try (InputStream stream = input; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192]; int read;
            while ((read = stream.read(bytes)) != -1) {
                if (buffer.size() + read > MAX_RESPONSE) throw new IOException("Server response exceeds the supported size.");
                buffer.write(bytes, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }
}
