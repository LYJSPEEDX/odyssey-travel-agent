package com.example.odyssey;
import android.widget.ImageView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class ResponseImagesTest {
    @Test public void homeContractSamplesCarryResponseCovers() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int covers = 0;
        for (SavedTrip trip : ExampleTrips.all(context)) if (trip.id.startsWith("contract_")) {
            assertNotNull("Home must receive media before opening details", trip.contractJson);
            assertNotNull(ResponseImage.cover(new JSONObject(trip.contractJson), true, System.currentTimeMillis()));
            covers++;
        }
        assertEquals(2, covers);
    }
    @Test public void completedButQueuedResultCannotUpdateDetachedView() throws Exception {
        SafeImageLoader loader = new SafeImageLoader(url -> android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888));
        class LifecycleImage extends ImageView {
            android.view.View.OnAttachStateChangeListener listener;
            LifecycleImage() { super(InstrumentationRegistry.getInstrumentation().getTargetContext()); }
            @Override public void addOnAttachStateChangeListener(android.view.View.OnAttachStateChangeListener value) { super.addOnAttachStateChangeListener(value); listener = value; }
        }
        ImageView[] target = new ImageView[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            LifecycleImage image = new LifecycleImage(); TripPhotos.show(image, "Kyoto");
            target[0] = image;
            loader.load(image, "https://images.unsplash.com/detached", "Wrong");
            try {
                // 暂停 UI 消费回调，直接等待已提交 worker 完成；此时 cancel 已无法阻止已排队的 UI 更新。
                java.lang.reflect.Field field = SafeImageLoader.class.getDeclaredField("bindings"); field.setAccessible(true);
                Object binding = ((java.util.Map<?, ?>) field.get(loader)).get(image);
                java.lang.reflect.Field future = binding.getClass().getDeclaredField("future"); future.setAccessible(true);
                ((java.util.concurrent.Future<?>) future.get(binding)).get(3, java.util.concurrent.TimeUnit.SECONDS);
                image.listener.onViewDetachedFromWindow(image);
            } catch (Exception error) { throw new AssertionError(error); }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertTrue(target[0].getContentDescription().toString().contains("Kyoto")));
    }
    @Test public void mediaSelectionHonorsStatusExpiryTypesAndSize() throws Exception {
        JSONObject root = new JSONObject("{summary:{coverImageId:'cover'},media:[{id:'cover',status:'READY',url:'https://images.unsplash.com/full',thumbnailUrl:'https://images.unsplash.com/thumb',altText:'Garden',attributions:[{text:'Photo source',url:'javascript:bad'}],expiresAt:null}]}");
        assertEquals("https://images.unsplash.com/thumb", ResponseImage.cover(root, true, 0).url);
        assertEquals("https://images.unsplash.com/full", ResponseImage.cover(root, false, 0).url);
        assertEquals("Photo source", ResponseImage.cover(root, false, 0).credit);
        JSONObject media = root.getJSONArray("media").getJSONObject(0);
        for (String expiry : new String[]{"2099-01-01T00:00:00Z", "2099-01-01T00:00:00.123Z", "2099-01-01T11:00:00+11:00"}) {
            media.put("expiresAt", expiry); assertNotNull(expiry, ResponseImage.cover(root, false, System.currentTimeMillis()));
        }
        for (String expiry : new String[]{"invalid", "2099-02-30T00:00:00Z", "2099-01-01T00:00:00Zjunk", "2099-01-01"}) {
            media.put("expiresAt", expiry); assertNull(expiry, ResponseImage.cover(root, false, System.currentTimeMillis()));
        }
        media.put("expiresAt", "2020-01-01T00:00:00Z"); assertNull(ResponseImage.cover(root, false, System.currentTimeMillis()));
        media.put("expiresAt", JSONObject.NULL).put("status", "PENDING"); assertNull(ResponseImage.cover(root, false, 0));
        media.put("status", "READY").put("url", 42); assertNull(ResponseImage.cover(root, false, 0));
        root.getJSONObject("summary").put("coverImageId", JSONObject.NULL); assertNull(ResponseImage.cover(root, true, 0));
    }
    @Test public void mockResponsesCarryCatalogImages() throws Exception {
        PlanningSession session = new PlanningSession("Sydney", "");
        session.current = 1;
        for (PlanningSession.Option option : session.step().question.options) assertNotNull(ImageUrlPolicy.accept(option.imageUrl));
        PlanningSession parsed = PlanningJson.parse(PlanningJson.snapshot(session).toString());
        assertEquals(session.step().question.options.get(0).imageUrl, parsed.step().question.options.get(0).imageUrl);
        JSONObject raw = PlanningJson.snapshot(session);
        raw.getJSONArray("steps").getJSONObject(1).getJSONObject("question").getJSONArray("options").getJSONObject(0).put("imageUrl", 42);
        assertEquals("", PlanningJson.parse(raw.toString()).steps[1].question.options.get(0).imageUrl);
        TripPlanState plan = new TripPlanState(); plan.selectDeparture("Sydney"); plan.selectDestination("Kyoto");
        plan.useFlexibleDates(); plan.setCompanion("Couple"); plan.setBudget("Comfort");
        JSONObject contract = MockItinerary.fromPlan(InstrumentationRegistry.getInstrumentation().getTargetContext(), plan, "image_test");
        assertEquals(MockPhotoCatalog.url("Kyoto", false), ResponseImage.cover(contract, false, System.currentTimeMillis()).url);
        for (String sample : new String[]{"contract_canberra", "contract_new_york"}) {
            JSONObject example = new JSONObject(ItineraryAssets.example(InstrumentationRegistry.getInstrumentation().getTargetContext(), sample));
            assertNotNull(ResponseImage.cover(example, true, System.currentTimeMillis()));
            ItineraryDocument.parse(InstrumentationRegistry.getInstrumentation().getTargetContext(), example.toString());
        }
    }
    @Test public void legacyAssetKeyRemainsLocalFallback() throws Exception {
        JSONObject root = new JSONObject("{summary:{coverImageId:'c'},media:[{id:'c',status:'READY',assetKey:'city_kyoto'}]}");
        assertEquals("Kyoto", TripPhotos.fallbackCity(root, "Unknown"));
        root.getJSONArray("media").getJSONObject(0).put("assetKey", "../../city_kyoto");
        assertEquals("Unknown", TripPhotos.fallbackCity(root, "Unknown"));
    }
    @Test public void fallbackRebindingCancelsOldCompletion() throws Exception {
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        SafeImageLoader loader = new SafeImageLoader(url -> { started.countDown();
            try { release.await(); } catch (InterruptedException ignored) { /* 模拟无法取消的传输完成，验证绑定令牌。 */ }
            finished.countDown(); return android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888); });
        ImageView[] image = new ImageView[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            image[0] = new ImageView(InstrumentationRegistry.getInstrumentation().getTargetContext());
            TripPhotos.show(image[0], "Unknown"); loader.load(image[0], "https://images.unsplash.com/one", "Remote");
        });
        assertTrue(started.await(3, java.util.concurrent.TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { loader.cancel(image[0]); TripPhotos.show(image[0], "Kyoto"); });
        release.countDown(); assertTrue(finished.await(3, java.util.concurrent.TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertTrue(image[0].getContentDescription().toString().contains("Kyoto")));
    }
    @Test public void successfulFetchReplacesFallbackAndFailureRetainsIt() throws Exception {
        java.util.concurrent.CountDownLatch success = new java.util.concurrent.CountDownLatch(1);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888);
        SafeImageLoader good = new SafeImageLoader(url -> bitmap);
        ImageView[] image = new ImageView[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            image[0] = new ImageView(InstrumentationRegistry.getInstrumentation().getTargetContext()); TripPhotos.show(image[0], "Kyoto");
            good.load(image[0], "https://images.unsplash.com/success", "Returned alt", success::countDown);
        });
        assertTrue(success.await(3, java.util.concurrent.TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            assertEquals("Returned alt", image[0].getContentDescription());
            assertSame(bitmap, ((android.graphics.drawable.BitmapDrawable) image[0].getDrawable()).getBitmap());
        });
        java.util.concurrent.CountDownLatch failure = new java.util.concurrent.CountDownLatch(1);
        SafeImageLoader bad = new SafeImageLoader(url -> { failure.countDown(); throw new java.io.IOException("offline"); });
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { TripPhotos.show(image[0], "Kyoto"); bad.load(image[0], "https://images.unsplash.com/failure", "Wrong"); });
        assertTrue(failure.await(3, java.util.concurrent.TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertTrue(image[0].getContentDescription().toString().contains("Kyoto")));
    }
}
