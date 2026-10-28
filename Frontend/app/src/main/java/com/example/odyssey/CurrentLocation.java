package com.example.odyssey;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.os.CancellationSignal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 用户主动点击时获取一次近似位置；只返回地名，不保存坐标。 */
final class CurrentLocation {
    interface Listener { void status(String text); void found(String name, String countryCode); }
    private final AppCompatActivity activity;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService geocoding = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> permission;
    private CancellationSignal pending;
    private int generation;
    private int permissionTicket = -1;

    CurrentLocation(AppCompatActivity activity, Listener listener, boolean waitingPermission) {
        this.activity = activity; this.listener = listener;
        if (waitingPermission) permissionTicket = generation;
        permission = activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (permissionTicket != generation) return;
            permissionTicket = -1;
            if (granted) locate(); else listener.status("Location permission was not granted. Please choose a country and region.");
        });
    }
    void start() {
        cancel();
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate();
        else { permissionTicket = generation; permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION); }
    }
    void cancel() { generation++; if (pending != null) pending.cancel(); pending = null; }
    boolean isWaitingForPermission() { return permissionTicket == generation; }
    void close() { cancel(); geocoding.shutdownNow(); }

    private void locate() {
        cancel(); int ticket = generation;
        LocationManager manager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null || !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || !Geocoder.isPresent()) {
            listener.status("Location is unavailable. Please choose a country and region."); return;
        }
        listener.status("Finding your approximate location…");
        pending = new CancellationSignal();
        handler.postDelayed(() -> {
            if (ticket == generation) { cancel(); listener.status("Location timed out. Please choose a country and region."); }
        }, 15000);
        try {
            LocationManagerCompat.getCurrentLocation(manager, LocationManager.NETWORK_PROVIDER, pending,
                    ContextCompat.getMainExecutor(activity), location -> {
                if (ticket != generation || activity.isDestroyed()) return;
                if (location == null) { cancel(); listener.status("Location is unavailable. Please choose a country and region."); return; }
                geocoding.execute(() -> {
                    String result = ""; String countryCode = "";
                    try {
                        // 反向地理编码可能访问系统定位服务，放在后台线程，失败时继续手动选择。
                        List<Address> addresses = new Geocoder(activity.getApplicationContext(), Locale.ENGLISH)
                                .getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                        if (addresses != null && !addresses.isEmpty()) {
                            Address address = addresses.get(0);
                            countryCode = address.getCountryCode() == null ? "" : address.getCountryCode();
                            List<String> parts = new ArrayList<>();
                            for (String part : new String[]{address.getLocality(), address.getAdminArea(), address.getCountryName()})
                                if (part != null && !part.trim().isEmpty() && !parts.contains(part.trim())) parts.add(part.trim());
                            result = String.join(", ", parts);
                        }
                    } catch (Exception ignored) { /* 系统不保证提供反向地理编码。 */ }
                    String resolved = result; String resolvedCountry = countryCode;
                    handler.post(() -> {
                        if (ticket != generation || activity.isDestroyed()) return;
                        cancel();
                        if (resolved.isEmpty()) listener.status("Could not find a place name. Please choose a country and region.");
                        else listener.found(resolved, resolvedCountry);
                    });
                });
            });
        } catch (SecurityException | IllegalArgumentException error) {
            cancel(); listener.status("Location is unavailable. Please choose a country and region.");
        }
    }
}
