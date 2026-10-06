package com.example.odyssey;

final class TripPlanState {

    static final int TOTAL_STEPS = 5;

    private int currentStep = 1;
    private String destination;

    void selectDestination(String destination) {
        this.destination = destination;
        if (currentStep == 1) {
            currentStep = 2;
        }
    }

    void goNext() {
        if (canGoNext()) {
            currentStep++;
        }
    }

    void goBack() {
        if (canGoBack()) {
            currentStep--;
        }
    }

    boolean canGoNext() {
        return destination != null && currentStep < TOTAL_STEPS;
    }

    boolean canGoBack() {
        return currentStep > 1;
    }

    int getCurrentStep() {
        return currentStep;
    }

    String getDestination() {
        return destination;
    }
}
