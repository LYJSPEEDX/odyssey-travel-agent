package com.example.odyssey;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** 固定的八个槽位；浏览位置与答案状态分离，返回不会凭空增加问答轮次。 */
final class PlanningSession implements Serializable {
    private static final long serialVersionUID = 1L;
    static final String[] IDS = {"ORIGIN", "DESTINATION", "DATES", "COMPANIONS", "BUDGET",
            "CONSTRAINTS", "FOLLOW_UP_1", "FOLLOW_UP_2"};
    final String id;
    int revision;
    int current;
    final String residence;
    final String preferences;
    final Step[] steps = new Step[8];
    TripPlanState dateDraft = new TripPlanState();
    String feedback = "";

    static final class Option implements Serializable {
        private static final long serialVersionUID = 1L;
        String id, title, description, value, imageUrl;
        final List<String> keywords = new ArrayList<>();
        Option(String id, String title, String description, String value, String... keywords) {
            this.id = id; this.title = title; this.description = description; this.value = value;
            this.imageUrl = MockPhotoCatalog.url(value, true);
            this.keywords.addAll(Arrays.asList(keywords));
        }
    }
    static final class Question implements Serializable {
        private static final long serialVersionUID = 1L;
        String id, title, description, inputMode = "OPTIONS";
        boolean allowText = true;
        int minSelections = 1, maxSelections = 1;
        final List<Option> options = new ArrayList<>();
    }
    static final class Step implements Serializable {
        private static final long serialVersionUID = 1L;
        String state = "LOCKED", answer = "", draft = "";
        Question question;
        final List<String> keywords = new ArrayList<>();
        final LinkedHashSet<String> selected = new LinkedHashSet<>();
    }
    PlanningSession(String residence, String preferences) {
        this(UUID.randomUUID().toString(), residence, preferences);
    }
    PlanningSession(String id, String residence, String preferences) {
        this.id = InputValidation.requiredText(id, 100, "Session id");
        String city = InputValidation.optionalText(residence, 200, "Residence");
        // 未提供所在地时只展示通用示例城市，不把 Sydney 标记成用户资料。
        this.residence = city;
        this.preferences = InputValidation.optionalText(preferences, 2000, "Preferences");
        for (int i = 0; i < steps.length; i++) steps[i] = new Step();
        rebuildQuestions();
        steps[0].state = "AWAITING_INPUT";
    }
    Step step() { return steps[current]; }
    String progress() { return current == 0 ? "START" : current + "/7"; }
    String answer(int index) { return steps[index].answer; }
    void selectDate(int date) { dateDraft.selectDate(date); }
    void toggle(String id) {
        if (step().question.options.stream().noneMatch(option -> option.id.equals(id)))
            throw new IllegalArgumentException("This option has changed; choose again");
        step().state = "AWAITING_INPUT";
        if (current == 3 && id.equals("solo") || current == 5 && id.equals("none")) {
            step().selected.clear(); step().selected.add(id); return;
        }
        step().selected.remove(current == 3 ? "solo" : "none");
        if (step().question.maxSelections == 1) step().selected.clear();
        if (!step().selected.remove(id)) step().selected.add(id);
    }
    /** REFINE 只刷新建议；确认过的答案也必须再次明确确认，不能让文本提交自动前进。 */
    void refine(String stepId, String text) {
        int index = index(stepId);
        String value = InputValidation.requiredText(text, index < 2 ? 200 : 500, "Message");
        if (index > 2) throw new IllegalArgumentException("This question accepts an answer");
        steps[index].state = "AWAITING_INPUT";
        steps[index].draft = "";
        steps[index].selected.clear();
        if (index < 2) {
            steps[index].question.options.clear();
            steps[index].question.options.add(new Option("custom", value,
                    "Local demo suggestion · confirm to use", value, "Custom", "Explore", "Demo"));
        } else {
            dateDraft.useFlexibleDates();
            steps[index].question.description = "Demo suggestion for: " + value + ". Confirm flexible dates to continue.";
        }
        staleAfter(index);
        revision++;
    }
    void accept(String stepId, String answer, List<String> keywords) {
        int index = index(stepId);
        for (int i = 0; i < index; i++) if (!steps[i].state.equals("CONFIRMED"))
            throw new IllegalArgumentException("Review earlier answers first");
        Step step = steps[index];
        String normalized = InputValidation.requiredText(answer, index < 2 ? 200 : 2000, "Answer");
        if (index == 1 && normalized.equalsIgnoreCase(answer(0))
                || index == 0 && normalized.equalsIgnoreCase(answer(1)))
            throw new IllegalArgumentException("Departure and destination must be different");
        if (!step.answer.equals(normalized)) staleAfter(index);
        step.answer = normalized;
        step.state = "CONFIRMED";
        step.draft = "";
        step.keywords.clear(); step.keywords.addAll(keywords);
        feedback = "Understood: " + String.join(" · ", keywords);
        revision++;
        rebuildQuestions();
        if (index < 7) {
            current = index + 1;
            if (step().state.equals("LOCKED")) step().state = "AWAITING_INPUT";
        }
    }
    private void staleAfter(int index) {
        for (int i = index + 1; i < steps.length; i++)
            if (!steps[i].answer.isEmpty()) steps[i].state = "NEEDS_REVIEW";
    }
    boolean canGenerate() {
        for (Step step : steps) if (!step.state.equals("CONFIRMED")) return false;
        return true;
    }
    void browse(int direction) {
        int next = current + direction;
        if (next >= 0 && next < 8 && !steps[next].state.equals("LOCKED")) current = next;
    }
    static int index(String stepId) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(stepId)) return i;
        throw new IllegalArgumentException("Unknown step");
    }
    /** 问题 ID 包含上下文摘要；旧问题里的选项在上游改动后不能误当成新答案。 */
    void rebuildQuestions() {
        for (int i = 0; i < 8; i++) {
            if (i < 6 && steps[i].question != null) continue;
            Question q = new Question();
            String context = String.join("\u001f", answer(0), answer(1), answer(2), answer(3), answer(4), answer(5), preferences);
            q.id = IDS[i] + "-" + (i < 6 ? "1" : UUID.nameUUIDFromBytes(context.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            q.title = new String[]{"Where are you departing from?", "Where would you like to go?",
                    "When are you travelling?", "Who is coming along?", "Choose your daily budget",
                    "Anything we should plan around?", "What pace feels right?", "What would make this trip yours?"}[i];
            q.description = "Local demo · choose an option or describe your preference.";
            if (i == 0) {
                if (!residence.isEmpty()) q.options.add(new Option("home", residence, "From your profile", residence, "Departure"));
                for (String city : new String[]{"Sydney", "Melbourne", "Brisbane"})
                    if (!city.equalsIgnoreCase(residence)) q.options.add(new Option(city, city, "Departure city", city, "Departure"));
            } else if (i == 1) {
                q.options.add(new Option("tokyo", "Tokyo", "City discovery", "Tokyo", "Food", "Culture", "Neon"));
                q.options.add(new Option("bali", "Bali", "Island escape", "Bali", "Beach", "Nature", "Relax"));
                q.options.add(new Option("kyoto", "Kyoto", "Slow discovery", "Kyoto", "Temples", "Gardens", "Tradition"));
            } else if (i == 2) {
                q.inputMode = "DATE_RANGE";
                q.description = "Select start and return dates, or confirm a flexible demo suggestion.";
                q.options.add(new Option("flexible", "Flexible dates", "A suggested three-day trip", "Flexible dates", "Flexible"));
            } else if (i == 3) {
                q.maxSelections = 3;
                for (String value : new String[]{"Solo", "Partner", "Friends", "Family"})
                    q.options.add(new Option(value.toLowerCase(java.util.Locale.ROOT), value, "Travel with " + value.toLowerCase(java.util.Locale.ROOT), value, value));
            } else if (i == 4) {
                // 预算以目的地为上下文，每次真正切换目的地后重建此问题。
                String city = answer(1).isEmpty() ? "your destination" : answer(1);
                int tier = 0;
                for (TripOptions.Option budget : TripOptions.budgets(city))
                    q.options.add(new Option("budget-" + tier++, budget.title, "For " + city + ": " + budget.description, budget.title, budget.title));
            } else if (i == 5) {
                q.maxSelections = 6;
                int constraintIndex = 0;
                for (TripOptions.Option constraint : TripOptions.CONSTRAINTS)
                    q.options.add(new Option("constraint-" + constraintIndex++, constraint.title, constraint.description, constraint.title, constraint.title));
                q.options.add(new Option("none", "No restrictions", "Plan without extra constraints", "No restrictions", "No restrictions"));
            } else if (i == 6) {
                q.description = "Based on your constraints: " + (answer(5).isEmpty() ? "none yet" : answer(5));
                String prefix = q.id + "-";
                q.options.add(new Option(prefix + "relaxed", "Relaxed", "One main activity, with breaks", "Relaxed pace", "Relaxed"));
                q.options.add(new Option(prefix + "balanced", "Balanced", "A mix of exploration and downtime", "Balanced pace", "Balanced"));
                q.options.add(new Option(prefix + "packed", "Packed", "See more each day", "Packed pace", "Active"));
            } else {
                q.description = "For " + answer(1) + ", inspired by your profile: " + (preferences.isEmpty() ? "open to discovery" : preferences);
                String prefix = q.id + "-";
                q.options.add(new Option(prefix + "local", "Local discoveries", "Food, neighbourhoods and everyday culture", "Local discoveries", "Local"));
                q.options.add(new Option(prefix + "nature", "Nature time", "Parks, scenery and time outdoors", "Nature time", "Nature"));
                q.options.add(new Option(prefix + "highlights", "Classic highlights", "A first visit with the memorable sights", "Classic highlights", "Highlights"));
            }
            steps[i].question = q;
        }
    }
    /** 只在所有槽位确认后转换；旧行程生成、验证、保存去重逻辑仍复用原入口。 */
    TripPlanState toPlan() {
        if (!canGenerate()) throw new IllegalArgumentException("Review all answers first");
        TripPlanState plan = new TripPlanState();
        plan.selectDeparture(answer(0)); plan.selectDestination(answer(1));
        if (dateDraft.isFlexibleDates()) plan.useFlexibleDates();
        else { plan.selectDate(dateDraft.getStartDate()); plan.selectDate(dateDraft.getEndDate()); }
        plan.setCompanion(answer(3)); plan.setBudget(answer(4));
        if (!answer(5).equals("No restrictions"))
            for (String constraint : answer(5).split(" · ")) plan.toggleConstraint(constraint);
        plan.setNote(5, "Pace: " + answer(6) + "; Experience: " + answer(7));
        for (int i = 0; i < 5; i++) plan.goNext();
        return plan;
    }
}
