package com.example.odyssey;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Network boundary for final generation. Credentials belong on the backend. */
class TripPreviewClient {
    private final String baseUrl;
    TripPreviewClient() { this(BuildConfig.API_BASE_URL); }
    TripPreviewClient(String baseUrl) { this.baseUrl = baseUrl; }
    private volatile HttpURLConnection active;
    private volatile boolean cancelled;

    static JSONObject request(TripPlanState state, int adults, String currency, String preferences) throws Exception {
        if (state.isFlexibleDates() || state.getStartDate() == 0 || state.getEndDate() <= state.getStartDate())
            throw new IllegalArgumentException("Choose fixed dates with at least one overnight stay.");
        if (state.getDurationDays() > 60) throw new IllegalArgumentException("Choose no more than 60 days.");
        if (adults < 1 || adults > 20) throw new IllegalArgumentException("Enter 1–20 adults.");
        String code = currency.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{3}")) throw new IllegalArgumentException("Enter a three-letter currency, such as AUD.");
        JSONObject notes = new JSONObject();
        for (int i = 1; i <= 5; i++) if (!state.getNote(i).isEmpty()) notes.put(String.valueOf(i), state.getNote(i));
        if (!preferences.isEmpty()) notes.put("1", preferences);
        return new JSONObject().put("departure", state.getDeparture()).put("destination", state.getDestination())
                .put("startDate", iso(state.getStartDate())).put("endDate", iso(state.getEndDate()))
                .put("companion", state.getCompanion()).put("budget", TripOptions.budgetDescription(state))
                .put("adults", adults).put("currency", code)
                .put("constraints", new JSONArray(state.getConstraints())).put("notes", notes);
    }
    private static String iso(int key) { return String.format(Locale.ROOT, "%04d-%02d-%02d", key / 10000, key / 100 % 100, key % 100); }

    String preview(JSONObject request) throws Exception {
        if (baseUrl.isEmpty()) throw new IllegalStateException("The backend URL is not configured for this build.");
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/trips/preview").openConnection();
        active = connection;
        try {
            if (cancelled) throw new InterruptedException();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(1250000); // Research and scheduling each have a 600s server budget.
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            String raw;
            try (InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int count;
                if (input != null) while ((count = input.read(buffer)) != -1) {
                    if (cancelled) throw new InterruptedException();
                    if (bytes.size() + count > ItineraryAssets.MAX_BYTES * 4) throw new IllegalArgumentException("The server response is too large.");
                    bytes.write(buffer, 0, count);
                }
                raw = bytes.toString("UTF-8");
            }
            if (status != 200) {
                String message = "Trip generation failed (HTTP " + status + ").";
                try {
                    Object detail = new JSONObject(raw).opt("detail");
                    if (detail instanceof JSONObject) message += " " + ((JSONObject) detail).optString("message", "");
                    else if (status == 422) message += " Check your dates, adult count and currency.";
                } catch (Exception ignored) { }
                String id = connection.getHeaderField("X-Research-Id");
                if (id != null) message += " Reference: " + id;
                throw new java.io.IOException(message);
            }
            if (cancelled) throw new InterruptedException();
            return new JSONObject(raw).getJSONObject("itinerary").toString();
        } finally { connection.disconnect(); active = null; }
    }
    void cancel() { cancelled = true; HttpURLConnection connection = active; if (connection != null) connection.disconnect(); }
}
