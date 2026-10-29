package com.example.odyssey;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retains one request and its result across rotation; never silently retries an AI call. */
public final class TripGenerationModel extends AndroidViewModel {
    static final class Result {
        final SavedTrip trip;
        final String error;
        final boolean saved;
        Result(SavedTrip trip, String error, boolean saved) { this.trip = trip; this.error = error; this.saved = saved; }
    }
    private final MutableLiveData<Result> result = new MutableLiveData<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    java.util.function.Supplier<TripPreviewClient> clientFactory = TripPreviewClient::new;
    private TripPreviewClient client;
    private volatile int ticket;
    private boolean running;
    public TripGenerationModel(Application app) { super(app); }
    LiveData<Result> result() { return result; }
    boolean running() { return running; }
    void start(JSONObject request) {
        if (running) return;
        int current = ++ticket;
        running = true; result.setValue(null);
        TripPreviewClient connection = clientFactory.get(); client = connection;
        worker.execute(() -> {
            Result outcome;
            try {
                SavedTrip trip = SavedTrip.fromContract(getApplication(), connection.preview(request));
                if (current != ticket) return;
                outcome = new Result(trip, "", new TripStore((android.content.Context) getApplication()).save(trip));
            } catch (Exception error) {
                outcome = new Result(null, error instanceof java.net.SocketTimeoutException
                        ? "The server took too long. Retry or edit your choices."
                        : error instanceof java.net.ConnectException
                        ? "Cannot reach the backend. Check the server and connection."
                        : "Generation could not finish. " + (error.getMessage() == null ? "Please retry." : error.getMessage()), false);
            }
            Result completed = outcome;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (current != ticket) return;
                running = false; client = null; result.setValue(completed);
            });
        });
    }
    void retrySave() {
        Result previous = result.getValue();
        if (running || previous == null || previous.trip == null) return;
        int current = ticket;
        running = true;
        worker.execute(() -> {
            boolean saved = new TripStore((android.content.Context) getApplication()).save(previous.trip);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (current != ticket) return;
                running = false; result.setValue(new Result(previous.trip, "", saved));
            });
        });
    }
    void cancel() {
        ++ticket; running = false;
        if (client != null) client.cancel();
        client = null; result.setValue(null);
    }
    @Override protected void onCleared() { cancel(); worker.shutdownNow(); }
}
