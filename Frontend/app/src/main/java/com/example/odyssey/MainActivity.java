package com.example.odyssey;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {

    // 只声明变量，不在这里查找 View
    private TextView stepText;
    private TextView tripBoardDestination;
    private View tripBoard;
    private View[] progressSteps;
    private NestedScrollView mainScroll;
    private MaterialButton backButton;
    private MaterialButton nextButton;
    private OnBackPressedCallback stepBackCallback;

    private final TripPlanState tripPlanState = new TripPlanState();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        // 先加载 activity_main.xml
        setContentView(R.layout.activity_main);

        // XML 加载以后，才能找到里面的 TextView
        stepText = findViewById(R.id.stepText);
        tripBoard = findViewById(R.id.tripBoard);
        tripBoardDestination = findViewById(R.id.tripBoardDestination);
        mainScroll = findViewById(R.id.main);
        backButton = findViewById(R.id.backButton);
        nextButton = findViewById(R.id.nextButton);
        progressSteps = new View[]{
                findViewById(R.id.progressStep1),
                findViewById(R.id.progressStep2),
                findViewById(R.id.progressStep3),
                findViewById(R.id.progressStep4),
                findViewById(R.id.progressStep5)
        };

        MaterialCardView kyotoCard = findViewById(R.id.kyotoCard);
        MaterialCardView tokyoCard = findViewById(R.id.tokyoCard);

        stepBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                navigateBack();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, stepBackCallback);

        kyotoCard.setOnClickListener(
                view -> selectDestination(R.string.destination_kyoto)
        );
        tokyoCard.setOnClickListener(
                view -> selectDestination(R.string.destination_tokyo)
        );
        backButton.setOnClickListener(view -> navigateBack());
        nextButton.setOnClickListener(view -> navigateNext());

        // 设置步骤文字、进度和按钮状态
        updateNavigationUi();

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (view, insets) -> {
                    Insets systemBars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                }
        );
    }

    private void updateNavigationUi() {
        int currentStep = tripPlanState.getCurrentStep();

        stepText.setText(
                getString(
                        R.string.step_count,
                        currentStep,
                        TripPlanState.TOTAL_STEPS
                )
        );

        int activeColor = ContextCompat.getColor(this, R.color.lime);
        int inactiveColor = ContextCompat.getColor(this, R.color.outline);
        for (int index = 0; index < progressSteps.length; index++) {
            progressSteps[index].setBackgroundColor(
                    index < currentStep ? activeColor : inactiveColor
            );
        }

        boolean showTripBoard = tripPlanState.getDestination() != null
                && currentStep > 1;
        tripBoard.setVisibility(showTripBoard ? View.VISIBLE : View.GONE);

        backButton.setEnabled(tripPlanState.canGoBack());
        nextButton.setEnabled(tripPlanState.canGoNext());
        stepBackCallback.setEnabled(tripPlanState.canGoBack());
    }

    private void selectDestination(int destinationResId) {
        String destination = getString(destinationResId);
        tripPlanState.selectDestination(destination);

        tripBoardDestination.setText(destination);
        updateNavigationUi();

        tripBoard.post(
                () -> mainScroll.smoothScrollTo(0, tripBoard.getTop())
        );
    }

    private void navigateBack() {
        tripPlanState.goBack();
        updateNavigationUi();
    }

    private void navigateNext() {
        tripPlanState.goNext();
        updateNavigationUi();
    }
}
