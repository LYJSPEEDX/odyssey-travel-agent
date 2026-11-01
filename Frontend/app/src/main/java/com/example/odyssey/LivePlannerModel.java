package com.example.odyssey;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.MutableLiveData;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retained across rotation. Durable mutation markers prevent process-death duplicate requests. */
final class LivePlannerModel {
    private static final java.util.Map<String, LivePlannerModel> MODELS = new java.util.HashMap<>();
    static synchronized LivePlannerModel get(Application application) {
        String url = new BackendConfig(application).url();
        LivePlannerModel model = MODELS.get(url);
        if (model == null) { model = new LivePlannerModel(application); MODELS.put(url, model); }
        return model;
    }
    private final Application application;
    Application getApplication() { return application; }
    final MutableLiveData<Integer> updates = new MutableLiveData<>(0);
    final BackendConfig config;
    final String endpoint, key;
    private BackendHttp.Transport transport;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    JSONObject session, creation, pendingTurn;
    JSONArray serverTrips = new JSONArray();
    String error = "", status = "", savedTripId = "", resultJson = "";
    boolean busy, generationUncertain;
    int selected;
    private int event;

    LivePlannerModel(Application application) {
        this.application = application;
        config = new BackendConfig(application);
        endpoint = config.url(); key = config.key(endpoint);
        try {
            String normalized = BackendConfig.normalize(endpoint, BuildConfig.DEBUG);
            BackendHttp http = new BackendHttp(normalized, config.owner(endpoint));
            transport = http::call;
            JSONObject cached = new JSONObject(config.preferences.getString(key + "_state", "{}"));
            session = cached.optJSONObject("session"); creation = cached.optJSONObject("creation");
            pendingTurn = cached.optJSONObject("pendingTurn");
            generationUncertain = cached.optBoolean("generationPending");
            savedTripId = cached.optString("savedTripId");
            if (session != null) { LivePlanningProtocol.validate(session); selected = LivePlanningProtocol.index(session.getString("currentStep")); }
            if (generationUncertain) error = "Generation may have reached the server. Check saved server trips before starting again.";
            else if (pendingTurn != null) error = "A planning reply was interrupted. Restore the session, then check that reply.";
        } catch (Exception exception) { error = friendly(exception); }
    }
    private void publish() { main.post(() -> updates.setValue(++event)); }
    private interface Work { void run() throws Exception; }
    private void run(String label, Work task) {
        if (busy) return;
        if (transport == null) { error = "Configure a valid server URL in Settings first."; publish(); return; }
        busy = true; status = label; error = ""; publish();
        worker.execute(() -> {
            try { task.run(); }
            catch (Exception exception) { error = friendly(exception); }
            finally { busy = false; status = ""; publish(); }
        });
    }
    private void persist() throws Exception {
        JSONObject state = new JSONObject().put("session", session == null ? JSONObject.NULL : session)
                .put("creation", creation == null ? JSONObject.NULL : creation)
                .put("pendingTurn", pendingTurn == null ? JSONObject.NULL : pendingTurn)
                .put("generationPending", generationUncertain).put("savedTripId", savedTripId);
        if (!config.preferences.edit().putString(key + "_state", state.toString()).commit())
            throw new IOException("Cannot save planning state. No further request was sent.");
    }
    String draft() { return config.preferences.getString(key + "_draft_" + selected, ""); }
    void draft(String value) { config.preferences.edit().putString(key + "_draft_" + selected, value).apply(); }
    void select(int step) { if (!busy) { selected = step; publish(); } }
    private String path() throws Exception { return "/v1/planning-sessions/" + session.getString("sessionId"); }
    private void apply(JSONObject response) throws Exception {
        LivePlanningProtocol.validate(response);
        if (session != null && !session.getString("sessionId").equals(response.getString("sessionId")))
            throw new IOException("The server returned a different planning session.");
        if (session != null && response.getInt("revision") < session.getInt("revision"))
            throw new IOException("An older response was received. Refresh the session.");
        session = response; selected = LivePlanningProtocol.index(response.getString("currentStep"));
        persist();
    }
    void health() { run("Checking connection…", () -> {
        JSONObject response = transport.call("GET", "/healthz", null, false);
        if (!"ok".equals(response.optString("status")) || !"ok".equals(response.optString("db")))
            throw new IOException("Server or database is unavailable.");
        error = "Connected. The server and database are ready; model and travel providers are checked when used.";
    }); }
    void start() { run("Opening your planning session…", () -> {
        if (session != null) { restoreInternal(); return; }
        if (creation == null) {
            JSONObject profile = new JSONObject();
            JSONObject local = new ProfileStore(getApplication()).load();
            if (ProfileStore.isComplete(local)) {
                JSONObject full = ProfilePayload.createSession(local).getJSONObject("profile");
                for (String field : new String[]{"residentialLocation", "residenceCountryCode", "displayCurrency", "currencySource", "travelTagIds", "travelPreferences"})
                    if (full.has(field)) profile.put(field, full.get(field));
            }
            creation = new JSONObject().put("schemaVersion", "1.0").put("requestId", UUID.randomUUID().toString()).put("profile", profile);
            persist();
        }
        apply(transport.call("POST", "/v1/planning-sessions", creation, false));
    }); }
    void restore() { if (session == null) start(); else run("Restoring confirmed answers…", this::restoreInternal); }
    private void restoreInternal() throws Exception { apply(transport.call("GET", path(), null, false)); }
    void send(String text) {
        if (busy || session == null || pendingTurn != null) return;
        try { submit(LivePlanningProtocol.turn(session, LivePlanningProtocol.STEPS[selected], text)); }
        catch (Exception exception) { error = friendly(exception); publish(); }
    }
    void confirm(String optionId) {
        if (busy || session == null || pendingTurn != null) return;
        try { submit(LivePlanningProtocol.confirm(session, optionId)); }
        catch (Exception exception) { error = friendly(exception); publish(); }
    }
    private void submit(JSONObject turn) { run("Waiting for the planning assistant…", () -> {
        pendingTurn = turn; persist();
        deliverPending();
    }); }
    void replay() { if (pendingTurn != null) run("Checking the original reply…", this::deliverPending); }
    private void deliverPending() throws Exception {
        try {
            JSONObject response = transport.call("POST", path() + "/turns", pendingTurn, false);
            // Exact replays can be old snapshots; GET the latest authoritative revision afterwards.
            int sentStep = LivePlanningProtocol.index(pendingTurn.getString("stepId"));
            LivePlanningProtocol.validate(response);
            if (!session.getString("sessionId").equals(response.getString("sessionId")))
                throw new IOException("Unexpected planning session.");
            pendingTurn = null;
            config.preferences.edit().remove(key + "_draft_" + sentStep).commit();
            if (response.getInt("revision") >= session.getInt("revision")) apply(response);
            else persist();
            restoreInternal();
        } catch (BackendHttp.ApiError error) {
            // Processing/transport uncertainty retains the exact request. Never allocate a new ID silently.
            if (!"TURN_PROCESSING".equals(error.code)
                    && !(error.status >= 500 && error.code.startsWith("HTTP_"))) {
                pendingTurn = null; persist();
                if (error.status == 409) restoreInternal();
            }
            throw error;
        }
    }
    void abandonTurn() { run("Restoring before a new attempt…", () -> {
        restoreInternal(); pendingTurn = null; persist();
        error = "Restored. Review the current proposal before sending another message. An earlier turn may still be processing.";
    }); }
    void generate() { run("Preparing generation…", () -> {
        if (session == null || pendingTurn != null || generationUncertain)
            throw new IOException("Restore planning or check server trips before generating.");
        if (!session.getJSONObject("generationReadiness").getBoolean("ready"))
            throw new IOException("Confirm all supported travel facts first.");
        JSONObject finalized = transport.call("POST", path() + "/finalize",
                new JSONObject().put("baseRevision", session.getInt("revision")), false);
        if (!"1.0".equals(finalized.getString("schemaVersion"))
                || !session.getString("sessionId").equals(finalized.getString("sessionId"))
                || session.getInt("revision") != finalized.getInt("revision"))
            throw new IOException("Finalization returned a different session or revision. Restore before generating.");
        JSONObject request = finalized.getJSONObject("generationRequest");
        generationUncertain = true; savedTripId = ""; resultJson = ""; persist();
        status = "Researching and building your itinerary… This can take several minutes."; publish();
        try {
            JSONObject itinerary = transport.call("POST", "/trips", request, true);
            resultJson = itinerary.toString();
            saveResult(resultJson);
            generationUncertain = false; persist();
        } catch (BackendHttp.ApiError exception) {
            // Even an HTTP error can follow a commit/proxy failure; use server recovery first.
            error = exception.getMessage() + " Check server trips before generating again.";
            persist();
        }
    }); }
    private void saveResult(String raw) throws Exception {
        SavedTrip snapshot = SavedTrip.fromContract(getApplication(), raw);
        if (!new TripStore(getApplication()).save(snapshot)) throw new IOException("The server returned a trip, but local saving failed. Retry saving or recover it from server trips.");
        savedTripId = snapshot.id;
    }
    void retrySave() { if (!resultJson.isEmpty()) run("Saving itinerary on this device…", () -> {
        saveResult(resultJson); generationUncertain = false; persist();
    }); }
    void listTrips() { run("Checking saved server trips…", () -> {
        JSONObject response = transport.call("GET", "/trips?limit=100&offset=0", null, false);
        serverTrips = response.getJSONArray("items");
        error = serverTrips.length() == 0 ? "No saved trips found yet. A disconnected generation may still be running; check again later." : "Choose a saved server trip to recover it on this device.";
    }); }
    void recover(String id) { run("Recovering saved itinerary…", () -> {
        UUID.fromString(id);
        JSONObject response = transport.call("GET", "/trips/" + id + "/itinerary", null, false);
        if (!id.equals(response.getString("id"))) throw new IOException("Server itinerary ID mismatch.");
        resultJson = response.toString(); saveResult(resultJson); generationUncertain = false; persist();
    }); }
    void allowAnotherGeneration() { if (!busy) { generationUncertain = false; error = "";
        run("Preparing a new attempt…", this::persist); } }
    void newSession() { run("Starting a fresh plan…", () -> {
        if (generationUncertain) throw new IOException("Check saved server trips or explicitly acknowledge a new generation first.");
        session = null; creation = null; pendingTurn = null; savedTripId = ""; resultJson = "";
        serverTrips = new JSONArray(); selected = 0;
        android.content.SharedPreferences.Editor editor = config.preferences.edit();
        for (int i = 0; i < 8; i++) editor.remove(key + "_draft_" + i);
        editor.commit(); persist();
    }); }
    static String friendly(Exception exception) {
        if (exception instanceof BackendHttp.ApiError) {
            BackendHttp.ApiError error = (BackendHttp.ApiError) exception;
            return error.getMessage() + " (" + error.code + ")";
        }
        if (exception instanceof java.net.SocketTimeoutException) return "The server took too long to reply. Restore planning or check saved server trips. No automatic retry was sent.";
        if (exception instanceof java.net.ConnectException || exception instanceof java.net.UnknownHostException)
            return "Cannot reach the server. Check the server URL, Wi-Fi and backend startup.";
        if (exception instanceof org.json.JSONException) return "The server returned an unsupported or incomplete response. Check that app and backend versions match.";
        return exception.getMessage() == null ? "The operation failed. Restore the session before retrying." : exception.getMessage();
    }

}
