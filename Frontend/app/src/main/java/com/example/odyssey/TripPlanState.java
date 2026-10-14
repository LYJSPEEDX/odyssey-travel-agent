package com.example.odyssey;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 五步表单的数据与规则，不依赖 Android View 或网络，因此可以直接写单元测试。
 * 步骤编号使用 1～5；生成状态独立记录，不把“生成中”当成第六步。
 * Serializable 用于随 Activity 的 Bundle 保存临时状态，不是后端 JSON 协议。
 */
final class TripPlanState implements Serializable {
    // Java 序列化类的版本号，不是行程 JSON 的 schemaVersion。
    private static final long serialVersionUID = 1L;
    // static final 是全类共用常量；TOTAL_STEPS 可以直接通过 TripPlanState.TOTAL_STEPS 读取。
    static final int TOTAL_STEPS = 5;
    // 初始第 1 步；这里只存步骤数字，不存对应的按钮或页面控件。
    private int currentStep = 1;
    // 空字符串表示没有答案，不用 null，可安全调用 isEmpty()。
    private String departure = "";
    private String destination = "";
    // 日期是 YYYYMMDD 整数，不是毫秒时间戳；0 表示尚未选择。
    private int startDate;
    private int endDate;
    private boolean flexibleDates;
    private String companion = "";
    private String budget = "";
    // Set 自动去重，LinkedHashSet 还保留点击顺序；final 不妨碍往集合里增加/删除元素。
    private final LinkedHashSet<String> constraints = new LinkedHashSet<>();
    // 下标 0 留空，让下标与步骤号一致。notes 已提交，drafts 是输入框里尚未发送的文字。
    private final String[] notes = {"", "", "", "", "", ""};
    private final String[] drafts = {"", "", "", "", "", ""};
    private int generationStatus; // 0: form, 1: mock loading, 2: mock result

    // trim 去掉首尾空格；这里只存名字，不查询城市是否真实存在。
    void selectDeparture(String value) { departure = value.trim(); }

    void selectDestination(String value) {
        // 局部变量 city 是清理后的新答案，不直接改传入的 value 字符串。
        String city = value.trim();
        // equalsIgnoreCase 忽略大小写；同一个城市仅改变大小写时不清理预算。
        if (!destination.equalsIgnoreCase(city)) {
            // 预算档位依赖目的地，更换城市后不能沿用上一座城市的预算答案。
            budget = "";
            // 第 4 步的已提交补充说明也与旧目的地有关。
            notes[4] = "";
            // 第 4 步尚未发送的草稿同样不能带进新目的地预算。
            drafts[4] = "";
        }
        // 清理完成后才写入新目的地，比较时仍能拿到原来的城市名。
        destination = city;
    }

    /** 首次点击选出发日；更早的第二次点击重新选起点；区间已完成后再次点击开始新选择。 */
    void selectDate(int date) {
        // 用户点了具体日期，即从灵活日期模式切回固定日期模式。
        flexibleDates = false;
        // 三种情况都重新起选：没有起点 / 已有完整区间 / 本次点的日期比起点早。
        if (startDate == 0 || endDate != 0 || date < startDate) {
            // 例：先点 11 月 12 日，再点 10 日，此时出发日变成 10 日。
            startDate = date;
            // 不把旧起点自动当成返回日；清空终点，让用户继续选择返回日。
            endDate = 0;
        } else {
            // 已有起点且本次不早于起点，才把本次点击作为终点；同一天往返也允许。
            endDate = date;
        }
    }

    void useFlexibleDates() {
        // 只表达用户愿意接受日期建议，不意味着这里已经计算出最佳日期。
        flexibleDates = true;
        // 清空固定区间，避免状态同时表示“固定日期”和“日期未定”。
        startDate = 0;
        endDate = 0;
    }

    void setCompanion(String value) { companion = value.trim(); }
    void setBudget(String value) { budget = value.trim(); }
    void toggleConstraint(String value) {
        // remove 成功返回 true，说明原本选中，已取消；返回 false 才 add，说明原本没选。
        if (!constraints.remove(value)) constraints.add(value);
    }
    // 点击“No restrictions”同时清除预设限制、已提交备注和输入草稿。
    void clearConstraints() { constraints.clear(); notes[5] = ""; drafts[5] = ""; }
    // 备注属于已提交答案，去除多余首尾空格。
    void setNote(int step, String value) { notes[step] = value.trim(); }
    // 草稿属于正在编辑的内容，原样保留空格，避免重绘时改变用户输入。
    void setDraft(int step, String value) { drafts[step] = value; }

    /** 决定能否前进；限制可留空，但日期最多 60 天且包含出发和返回当天。 */
    boolean isStepComplete(int step) {
        switch (step) {
            // 两个城市都非空且名字不同，第一步才完成。
            case 1: return !departure.isEmpty() && !destination.isEmpty()
                    && !departure.equalsIgnoreCase(destination);
            // 选了灵活日期即可通过；否则必须有有效首尾日期，且不超过协议上限。
            case 2: return flexibleDates || (startDate != 0 && endDate >= startDate && getDurationDays() <= 60);
            // 同行人与预算接受预设或自定义文本，只要非空即可。
            case 3: return !companion.isEmpty();
            case 4: return !budget.isEmpty();
            // 限制是可选项；没有限制不是错误，所以第五步总是完成。
            case 5: return true;
            // 不是 1～5 的步骤号不受支持，不能默认当作完成。
            default: return false;
        }
    }

    void goNext() {
        // 状态类自身也检查，不能只依赖 UI 按钮置灰来保证正确性。
        if (!canGoNext()) return;
        // ++ 将当前步数加一，最多加到 5；已经第 5 步则只切换生成状态。
        if (currentStep < TOTAL_STEPS) currentStep++;
        else generationStatus = 1;
    }

    // 延迟任务回来时，仅把“生成中”变成“完成”；不会强行改掉已返回表单的状态。
    void finishGeneration() { if (isGenerating()) generationStatus = 2; }

    void goBack() {
        // 生成中/生成完成 → 回到表单（步数仍是 5），不是直接跳到第 4 步。
        if (generationStatus != 0) generationStatus = 0;
        // 普通表单才减一步，且不允许退到不存在的第 0 步。
        else if (currentStep > 1) currentStep--;
    }

    boolean canGoNext() {
        // 生成区域不能继续前进，只有表单可以进入下一步。
        if (generationStatus != 0) return false;
        // 不只检查当前页：用户返回修改上游答案后，之前的完成条件也可能失效。
        for (int step = 1; step <= currentStep; step++) {
            if (!isStepComplete(step)) return false;
        }
        // 检查范围内所有步骤都通过，才允许前进。
        return true;
    }

    // 以下方法只读取当前状态（getter/判断方法），不改变答案。
    boolean canGoBack() { return currentStep > 1 || generationStatus != 0; }
    boolean isGenerating() { return generationStatus == 1; }
    boolean isGenerated() { return generationStatus == 2; }
    boolean isFlexibleDates() { return flexibleDates; }
    int getCurrentStep() { return currentStep; }
    String getDeparture() { return departure; }
    String getDestination() { return destination; }
    int getStartDate() { return startDate; }
    int getEndDate() { return endDate; }
    int getDurationDays() {
        // 任一日期未选返回 0；否则计算包含首尾的天数，如 10 日到 12 日为 3 天。
        return startDate == 0 || endDate == 0 ? 0
                : TripDates.daysBetweenInclusive(startDate, endDate);
    }
    String getCompanion() { return companion; }
    String getBudget() { return budget; }
    // 给外部只读视图，防止调用方绕过 toggleConstraint 等方法直接修改集合。
    Set<String> getConstraints() { return Collections.unmodifiableSet(constraints); }
    String getNote(int step) { return notes[step]; }
    String getDraft(int step) { return drafts[step]; }
}
