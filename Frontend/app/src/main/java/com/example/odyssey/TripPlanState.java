package com.example.odyssey;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Five-step form state. No Android or network dependency. */
final class TripPlanState implements Serializable {
    private static final long serialVersionUID = 1L;
    static final int TOTAL_STEPS = 5;
    private int currentStep = 1;
    private String departure = "";
    private String destination = "";
    private int startDate;
    private int endDate;
    private boolean flexibleDates;
    private String companion = "";
    private String budget = "";
    private final LinkedHashSet<String> constraints = new LinkedHashSet<>();
    private final String[] notes = {"", "", "", "", "", ""};
    private final String[] drafts = {"", "", "", "", "", ""};
    private int generationStatus; // 0: form, 1: mock loading, 2: mock result

    void selectDeparture(String value) { departure = value.trim(); }

    void selectDestination(String value) {
        String city = value.trim();
        if (!destination.equalsIgnoreCase(city)) {
            budget = "";
            notes[4] = "";
            drafts[4] = "";
        }
        destination = city;
    }

    void selectDate(int date) {
        flexibleDates = false;
        if (startDate == 0 || endDate != 0 || date < startDate) {
            startDate = date;
            endDate = 0;
        } else {
            endDate = date;
        }
    }

    void useFlexibleDates() {
        flexibleDates = true;
        startDate = 0;
        endDate = 0;
    }

    void setCompanion(String value) { companion = value.trim(); }
    void setBudget(String value) { budget = value.trim(); }
    void toggleConstraint(String value) {
        if (!constraints.remove(value)) constraints.add(value);
    }
    void clearConstraints() { constraints.clear(); notes[5] = ""; drafts[5] = ""; }
    void setNote(int step, String value) { notes[step] = value.trim(); }
    void setDraft(int step, String value) { drafts[step] = value; }

    boolean isStepComplete(int step) {
        switch (step) {
            case 1: return !departure.isEmpty() && !destination.isEmpty()
                    && !departure.equalsIgnoreCase(destination);
            case 2: return flexibleDates || (startDate != 0 && endDate >= startDate && getDurationDays() <= 60);
            case 3: return !companion.isEmpty();
            case 4: return !budget.isEmpty();
            case 5: return true;
            default: return false;
        }
    }

    void goNext() {
        if (!canGoNext()) return;
        if (currentStep < TOTAL_STEPS) currentStep++;
        else generationStatus = 1;
    }

    void finishGeneration() { if (isGenerating()) generationStatus = 2; }

    void goBack() {
        if (generationStatus != 0) generationStatus = 0;
        else if (currentStep > 1) currentStep--;
    }

    boolean canGoNext() {
        if (generationStatus != 0) return false;
        for (int step = 1; step <= currentStep; step++) {
            if (!isStepComplete(step)) return false;
        }
        return true;
    }

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
        return startDate == 0 || endDate == 0 ? 0
                : TripDates.daysBetweenInclusive(startDate, endDate);
    }
    String getCompanion() { return companion; }
    String getBudget() { return budget; }
    Set<String> getConstraints() { return Collections.unmodifiableSet(constraints); }
    String getNote(int step) { return notes[step]; }
    String getDraft(int step) { return drafts[step]; }
}
