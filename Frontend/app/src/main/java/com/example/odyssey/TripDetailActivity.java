package com.example.odyssey;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import org.json.JSONException;

/**
 * 行程详情入口：按 Intent 中的 ID 读取示例或本地记录，再交给 ItineraryRenderer 展示。
 * 本类管理“选中哪一天”和“调整草稿”；卡片布局与 JSON 字段映射放在 Renderer 中。
 */
public class TripDetailActivity extends AppCompatActivity {
    // 与调用页 putExtra 的键名一致；static final 常量避免两处手写字符串造成拼写不匹配。
    static final String EXTRA_ID = "trip_id";
    static final String EXTRA_SAMPLE = "sample";
    private int selectedDay; // 数组下标从 0 开始，界面 Day 1 对应下标 0。
    // 解析后的完整数据，既有 root JSON，也有方便按 ID 查找对象的索引。
    private ItineraryDocument document;
    private HomeUi ui;
    // 本详情页的调整文字；不写回 document，也不是五步表单中的输入草稿。
    private String adjustmentDraft = "";
    private final JournalAdjustment adjustment = new JournalAdjustment();
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_trip_detail);
        // Toolbar 是详情页顶栏；点击它的导航箭头关闭详情，不等于日程的“前一天”。
        MaterialToolbar toolbar = findViewById(R.id.tripDetailToolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        // 读取启动本页时携带的来源标记；未传时默认打开用户保存的记录。
        boolean sample = getIntent().getBooleanExtra(EXTRA_SAMPLE, false);
        toolbar.setTitle(sample ? R.string.sample_badge : R.string.my_title);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tripDetailRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        // ui 后续创建的文字/卡片会加入 XML 中这个竖向容器。
        ui = new HomeUi(findViewById(R.id.tripDetailContent));
        // 首次打开选 Day 1（下标 0）；重建时恢复之前的日期下标。
        selectedDay = savedInstanceState == null ? 0 : savedInstanceState.getInt("itinerary_day", 0);
        if (savedInstanceState != null) adjustmentDraft = savedInstanceState.getString("adjustment_draft", "");
        try {
            // ID 只是行程的定位符，不包含 JSON 内容；没有 ID 就不会猜测打开哪条记录。
            String id = getIntent().getStringExtra(EXTRA_ID);
            // 示例直接读打包资源；My Trips 则按 ID 读取已保存的快照，不重新生成。
            SavedTrip trip = id == null ? null : sample ? ExampleTrips.get(this, id) : new TripStore(this).get(id);
            // 示例不存在或本地未找到时显示错误并结束加载，避免访问 trip.route 导致空指针。
            if (trip == null) {
                ui.text(getString(R.string.trip_not_found), 18, true);
                return;
            }
            if (trip.contractJson != null) {
                // 新版结构化行程先校验再绘制；恢复的日期下标需要限制在实际天数内。
                toolbar.setTitle(R.string.journal_toolbar);
                document = ItineraryDocument.parse(this, trip.contractJson);
                // 例：只有 3 天时合法下标是 0～2；Math.min 限上限，Math.max 限下限。
                selectedDay = Math.max(0, Math.min(selectedDay, document.root.getJSONArray("days").length() - 1));
                // 先成功画出结构化详情，再显示只适用于新版详情的底部调整入口。
                renderItinerary();
                findViewById(R.id.journalFooter).setVisibility(android.view.View.VISIBLE);
                updateDraftStatus();
                // 外层回调响应按钮点击；内层 draft -> ... 响应弹层输入变化，是两种不同事件。
                findViewById(R.id.journalAdjust).setOnClickListener(view -> adjustment.show(this, adjustmentDraft, draft -> {
                    // 将新文字保存在 Activity 字段里，并同步底部“草稿已保留”的提示。
                    adjustmentDraft = draft; updateDraftStatus();
                }));
                // 已显示新版 JSON 页面，不再执行下面的旧版纯文本布局。
                return;
            }
            // 兼容旧版纯文本记录，不强制迁移或覆盖用户已有数据。
            TripCoverView.add(ui, ui.content, trip.destination, trip.destination, null);
            ui.text(trip.route, 28, true);
            ui.text(trip.dates, 14, false);
            ui.card(getString(sample ? R.string.sample_badge : R.string.trip_saved),
                    getString(sample ? R.string.sample_description : R.string.saved_trip_notice),
                    R.color.mint, null);
            ui.text(trip.brief, 14, false);
            ui.text(trip.itinerary, 16, false);
        } catch (JSONException | ClassCastException exception) {
            // 解析/存储类型出错时清掉可能只画了一半的内容；不删除或覆盖原记录。
            ui.content.removeAllViews();
            ui.text(getString(R.string.trip_read_error), 16, false);
        }
    }
    /** 日期变化后清空并重建动态内容；只改变展示，不修改 document 中的行程数据。 */
    private void renderItinerary() throws JSONException {
        // 这里只清 View，不清 document；下一次渲染仍从同一份数据读取。
        ui.content.removeAllViews();
        // index -> ... 被点击的日期按钮稍后调用，不是在 new Renderer 时立刻执行。
        new ItineraryRenderer(ui, document, selectedDay, index -> {
            // 先记住新下标，再重画，才会使新一天显示为选中并展示对应事件。
            selectedDay = index;
            try { renderItinerary(); }
            catch (JSONException error) {
                ui.content.removeAllViews();
                ui.text(getString(R.string.trip_read_error), 16, false);
            }
        }).render();
    }
    @Override protected void onSaveInstanceState(Bundle outState) {
        // 旋转/页面重建时恢复草稿；这里不写 TripStore，退出详情后不长期保留草稿。
        outState.putInt("itinerary_day", selectedDay);
        outState.putString("adjustment_draft", adjustmentDraft);
        super.onSaveInstanceState(outState);
    }
    private void updateDraftStatus() {
        // trim().isEmpty() 将全空格也视为无草稿；setText 接收的是两种英文文案资源 ID。
        ((android.widget.TextView) findViewById(R.id.journalDraftStatus)).setText(adjustmentDraft.trim().isEmpty()
                ? R.string.journal_offline : R.string.journal_draft_kept);
    }
    // 页面销毁时关闭持有 Activity 的弹层，避免旧窗口继续挂在已销毁的页面上。
    @Override protected void onDestroy() { adjustment.dismiss(); super.onDestroy(); }
}
