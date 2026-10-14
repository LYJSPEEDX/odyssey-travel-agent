package com.example.odyssey;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 原型阶段的本机行程库，使用应用私有 SharedPreferences，不包含云同步。
 * 每个行程一个 trip_ID 键：同 ID 更新快照，不同 ID 互不覆盖；不调用 clear() 清空整库。
 */
final class TripStore {
    static final String FILE = "trip_library";
    private static final String PREFIX = "trip_";
    private final SharedPreferences preferences;

    TripStore(Context context) {
        // MODE_PRIVATE 表示应用私有偏好文件；this(...) 调用下面那个接收 preferences 的构造器。
        this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE));
    }
    // 测试可传自己的偏好文件，不必把真实行程库当作测试数据写入目标。
    TripStore(SharedPreferences preferences) { this.preferences = preferences; }

    // commit 同步返回写入是否成功，因此调用方应在后台线程执行，而不是阻塞 UI。
    boolean save(SavedTrip trip) {
        try {
            // 四步：打开编辑器 → 用 trip_ID 作键 → 快照转 JSON 文本 → commit 同步写入并返回结果。
            return preferences.edit().putString(PREFIX + trip.id, trip.toJson().toString()).commit();
        } catch (JSONException exception) {
            // 快照不能正确序列化时返回失败，交给 MainActivity 显示重试入口。
            return false;
        }
    }

    /** 按创建时间倒序展示；损坏记录向上报错，不静默删除或跳过用户数据。 */
    List<SavedTrip> list() throws JSONException {
        List<SavedTrip> trips = new ArrayList<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            // Map.Entry 是一条键值对；? 表示值类型暂未知，因为偏好中也可能存数字/布尔值。
            if (!entry.getKey().startsWith(PREFIX)) continue;
            // 本行程格式要求值是 JSON 字符串；先检查再做 (String) 类型转换。
            if (!(entry.getValue() instanceof String)) throw new JSONException("Unreadable trip entry");
            trips.add(SavedTrip.fromJson((String) entry.getValue()));
        }
        // right 在前、left 在后形成时间降序；最新创建的行程排在最上面。
        trips.sort((left, right) -> Long.compare(right.createdAt, left.createdAt));
        return trips;
    }

    SavedTrip get(String id) throws JSONException {
        // 没有这个键时得到 null；“没找到”与“有记录但解析失败”是不同情况。
        String value = preferences.getString(PREFIX + id, null);
        return value == null ? null : SavedTrip.fromJson(value);
    }
}
