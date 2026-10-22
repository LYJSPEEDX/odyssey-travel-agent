package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.Test;
import org.junit.runner.RunWith;

/** 不依赖网络：首页/行程库必须有真实离线图片，并保留原来的数据与点击入口。 */
@RunWith(AndroidJUnit4.class)
public class EditorialTripsTest {
    @org.junit.Rule public final ProfileTestFixture profile = new ProfileTestFixture();
    @Test public void homeExamplesHavePhotoCoversAndRealMetadata() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // 内部入口跳过欢迎层，测试聚焦新版首页，不用等待固定动画时间。
        Intent intent = new Intent(context, HomeActivity.class).putExtra(HomeActivity.EXTRA_OPEN_MY, true);
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                ((BottomNavigationView) activity.findViewById(R.id.bottomNavigation)).setSelectedItemId(R.id.nav_home);
                View card = activity.findViewById(R.id.homeContent).findViewWithTag("example-kyoto");
                assertNotNull(card);
                assertTrue("Trip card needs a photo cover", containsPhoto(card));
                assertTrue("Keep destination readable", containsText(card, "Kyoto"));
                assertTrue("Do not invent sample travel dates", containsText(card, "3 days"));
                assertTrue("The whole card remains an accessible action", card.isClickable());
            });
        }
    }
    @Test public void destinationSelectionHasPhotosAndUpdatesLiveBoard() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            PlannerFlowTest.choose(scenario, "Sydney", "1/7");
            scenario.onActivity(activity -> assertTrue(containsPhoto(activity.findViewById(R.id.stepContent))));
            PlannerFlowTest.choose(scenario, "Kyoto", "2/7");
            scenario.onActivity(activity -> {
                assertTrue(containsText(activity.findViewById(R.id.tripBoard), "Sydney → Kyoto"));
                assertTrue("Live Trip needs the selected destination thumbnail",
                        containsPhoto(activity.findViewById(R.id.tripBoard)));
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertTrue(containsText(activity.findViewById(R.id.tripBoard), "Sydney → Kyoto")));
        }
    }
    @Test public void structuredItineraryHasCoverWithoutLosingJournal() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, TripDetailActivity.class)
                .putExtra(TripDetailActivity.EXTRA_SAMPLE, true).putExtra(TripDetailActivity.EXTRA_ID, "contract_canberra");
        try (ActivityScenario<TripDetailActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                View cover = activity.findViewById(R.id.tripDetailContent).findViewWithTag("journal-cover");
                assertNotNull("Journal needs an editorial cover", cover);
                assertTrue(containsPhoto(cover));
                assertNotNull(activity.findViewById(R.id.tripDetailContent).findViewWithTag("contract-day-0"));
            });
        }
    }
    @Test public void unknownCitiesDoNotBorrowAnotherCityPhoto() {
        assertEquals(R.drawable.photo_kyoto, TripPhotos.resource(" Kyoto "));
        assertEquals(R.drawable.bg_photo_placeholder, TripPhotos.resource("Paris, Texas"));
        assertEquals(R.drawable.bg_photo_placeholder, TripPhotos.resource("../../photo_kyoto"));
        assertEquals(R.drawable.bg_photo_placeholder, TripPhotos.resource(null));
    }
    @Test public void sampleBookmarkSavesWithoutOverwritingAnExistingSnapshot() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        TripStore store = new TripStore(context);
        SavedTrip previous = store.get("bali");
        Intent intent = new Intent(context, HomeActivity.class).putExtra(HomeActivity.EXTRA_OPEN_MY, true);
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                ((BottomNavigationView) activity.findViewById(R.id.bottomNavigation)).setSelectedItemId(R.id.nav_home);
                View card = activity.findViewById(R.id.homeContent).findViewWithTag("example-bali");
                View bookmark = card.findViewById(R.id.editorialSave);
                assertEquals("Show a working save action", View.VISIBLE, bookmark.getVisibility());
                bookmark.performClick();
            });
            long deadline = android.os.SystemClock.uptimeMillis() + 5000;
            while (store.get("bali") == null && android.os.SystemClock.uptimeMillis() < deadline)
                android.os.SystemClock.sleep(50);
            SavedTrip saved = store.get("bali");
            assertNotNull(saved);
            if (previous != null) assertEquals(previous.toJson().toString(), saved.toJson().toString());
            else assertEquals(ExampleTrips.get(context, "bali").itinerary, saved.itinerary);
        } finally {
            // 只删除本测试新增的固定示例；用户原先已保存同 ID 时不动它。
            if (previous == null) context.getSharedPreferences(TripStore.FILE, Context.MODE_PRIVATE)
                    .edit().remove("trip_bali").commit();
        }
    }
    @Test public void structuredSampleIsSavedWithItsContractIdAndIsIdempotent() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SavedTrip source = ExampleTrips.get(context, "contract_canberra");
        SavedTrip parsed = SavedTrip.fromContract(context, source.contractJson);
        TripStore store = new TripStore(context);
        SavedTrip previous = store.get(parsed.id);
        try {
            assertTrue(ExampleTrips.save(context, "contract_canberra"));
            SavedTrip first = store.get(parsed.id);
            assertNotNull("Saved record uses the contract ID and survives parsing", first);
            assertNotNull(first.contractJson);
            assertTrue(ExampleTrips.save(context, "contract_canberra"));
            assertEquals(first.toJson().toString(), store.get(parsed.id).toJson().toString());
            if (previous != null) assertEquals(previous.toJson().toString(), first.toJson().toString());
        } finally {
            if (previous == null) context.getSharedPreferences(TripStore.FILE, Context.MODE_PRIVATE)
                    .edit().remove("trip_" + parsed.id).commit();
        }
    }
    private static void clickText(View root, String label) {
        View target = findText(root, label);
        assertNotNull("Missing option " + label, target);
        while (!target.isClickable() && target.getParent() instanceof View) target = (View) target.getParent();
        assertTrue(target.performClick());
    }
    private static View findText(View root, String label) {
        if (root instanceof TextView && label.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View result = findText(((ViewGroup) root).getChildAt(i), label);
            if (result != null) return result;
        }
        return null;
    }
    private static boolean containsPhoto(View view) {
        if (view instanceof ImageView && ((ImageView) view).getDrawable() != null) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (containsPhoto(((ViewGroup) view).getChildAt(i))) return true;
        return false;
    }
    private static boolean containsText(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (containsText(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }
}
