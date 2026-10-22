package com.example.odyssey;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.WeakHashMap;
import java.util.concurrent.*;

/** 仅缓存位图、不持有 Activity；下载、解码、排队均有上限，失败保留调用者先设置的本地图。 */
final class SafeImageLoader {
    interface Transport { Bitmap fetch(String url) throws Exception; }
    static final SafeImageLoader SHARED = new SafeImageLoader(SafeImageLoader::download);
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private final Transport transport;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 20, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(12), runnable -> { Thread t = new Thread(runnable, "trip-image"); t.setDaemon(true); return t; }, new ThreadPoolExecutor.AbortPolicy());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private final WeakHashMap<ImageView, Binding> bindings = new WeakHashMap<>();
    // 离屏后取消工作；请求只握住弱引用，不延长 ImageView 及其 Activity 的生命期。
    private static final class Binding implements View.OnAttachStateChangeListener {
        boolean active = true;
        Future<?> future;
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) { active = false; if (future != null) future.cancel(true); view.removeOnAttachStateChangeListener(this); }
    }
    SafeImageLoader(Transport transport) { this.transport = transport; }
    void cancel(ImageView view) {
        Binding old = bindings.remove(view);
        if (old != null) { old.active = false; if (old.future != null) old.future.cancel(true); view.removeOnAttachStateChangeListener(old); }
    }
    void load(ImageView view, String raw, String alt) {
        load(view, raw, alt, () -> { });
    }
    void load(ImageView view, String raw, String alt, Runnable success) {
        cancel(view);
        String url = ImageUrlPolicy.accept(raw); if (url == null) return;
        Binding binding = new Binding(); bindings.put(view, binding); view.addOnAttachStateChangeListener(binding);
        WeakReference<ImageView> target = new WeakReference<>(view);
        try { binding.future = workers.submit(() -> {
            try {
                Bitmap bitmap = cache.get(url);
                if (bitmap == null) { bitmap = transport.fetch(url); if (bitmap != null) cache.put(url, bitmap); }
                Bitmap result = bitmap;
                if (!Thread.currentThread().isInterrupted() && result != null) main.post(() -> {
                    ImageView image = target.get();
                    // 同一控件重绑会替换令牌，即使旧下载无法立刻中断也不能覆盖新城市。
                    if (image != null && binding.active && bindings.get(image) == binding && !binding.future.isCancelled()) {
                        image.setImageBitmap(result); if (!alt.isEmpty()) image.setContentDescription(alt); success.run();
                    }
                });
            } catch (Exception ignored) { /* 离线、拒绝或坏图片：继续显示现有本地后备图。 */ }
        }); } catch (RejectedExecutionException ignored) { cancel(view); }
    }
    private static Bitmap download(String initial) throws Exception {
        String url = ImageUrlPolicy.accept(initial); long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        for (int redirects = 0; url != null && redirects <= 3; redirects++) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) return null;
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            int remaining = (int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(Math.min(4000, remaining)); connection.setReadTimeout(Math.min(2000, remaining));
            connection.setRequestProperty("Accept", "image/*");
            try {
                int code = connection.getResponseCode();
                if (code >= 300 && code <= 399) { url = ImageUrlPolicy.redirect(url, connection.getHeaderField("Location")); continue; }
                if (code != 200 || connection.getContentLengthLong() > MAX_BYTES) return null;
                String type = connection.getContentType(); if (type == null || !type.startsWith("image/")) return null;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                // 压缩下载最多 4MB；另有整体读取期限，慢速流也不能一直占住两个工作线程。
                try (InputStream input = connection.getInputStream()) {
                    byte[] chunk = new byte[8192]; int count;
                    while ((count = input.read(chunk)) != -1) {
                        if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline || bytes.size() + count > MAX_BYTES) return null;
                        bytes.write(chunk, 0, count);
                    }
                }
                byte[] data = bytes.toByteArray(); BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth > 30000 || options.outHeight > 30000) return null;
                options.inSampleSize = 1;
                // 压缩体积不能约束解码内存；缩采样限制最长边，缓存另按位图字节限制 8MB。
                while (options.outWidth / options.inSampleSize > 1200 || options.outHeight / options.inSampleSize > 1200) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false; return BitmapFactory.decodeByteArray(data, 0, data.length, options);
            } finally { connection.disconnect(); }
        }
        return null;
    }
}
