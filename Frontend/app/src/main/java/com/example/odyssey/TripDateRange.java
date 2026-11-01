package com.example.odyssey;

/** 日历上正在选择的出发/返回日期；只是待发送的草稿，由后端确认后才算答案。 */
final class TripDateRange {
    private int start, end;
    int getStartDate() { return start; }
    int getEndDate() { return end; }
    void clear() { start = 0; end = 0; }
    boolean isComplete() { return start != 0 && end != 0; }
    void select(int date) {
        if (start == 0 || end != 0 || date < start) { start = date; end = 0; }
        // 后端不支持当天往返，再点一次同一天不算返回日期。
        else if (date > start) end = date;
    }
}
