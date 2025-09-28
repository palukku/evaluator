package dev.phlp.stud.evaluator.controller.ui;

import java.text.DecimalFormat;
import java.util.function.IntConsumer;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;

/**
 * Encapsulates the behaviour of the navigation controls in the header bar.
 */
public class RepositoryNavigationBar {

    private final Spinner<Integer> startIndexSpinner;
    private final Spinner<Integer> endIndexSpinner;
    private final Button previousPlaceholderButton;
    private final Button nextPlaceholderButton;
    private final Label currentPlaceholderLabel;
    private final DecimalFormat pointFormat;
    private final IntConsumer placeholderAdjuster;
    private final Runnable placeholderDisplayUpdater;

    public RepositoryNavigationBar(Spinner<Integer> startIndexSpinner,
                                   Spinner<Integer> endIndexSpinner,
                                   Button previousPlaceholderButton,
                                   Button nextPlaceholderButton,
                                   Label currentPlaceholderLabel,
                                   DecimalFormat pointFormat,
                                   IntConsumer placeholderAdjuster,
                                   Runnable placeholderDisplayUpdater) {
        this.startIndexSpinner = startIndexSpinner;
        this.endIndexSpinner = endIndexSpinner;
        this.previousPlaceholderButton = previousPlaceholderButton;
        this.nextPlaceholderButton = nextPlaceholderButton;
        this.currentPlaceholderLabel = currentPlaceholderLabel;
        this.pointFormat = pointFormat;
        this.placeholderAdjuster = placeholderAdjuster;
        this.placeholderDisplayUpdater = placeholderDisplayUpdater;
    }

    public void initialize() {
        startIndexSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, 1));
        endIndexSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, 1));
        startIndexSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            Integer coerced = coerceValue(startIndexSpinner, oldVal, newVal, 1);
            if (getEndValue() < coerced) {
                endIndexSpinner.getValueFactory().setValue(coerced);
            }
            placeholderDisplayUpdater.run();
        });
        endIndexSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            Integer coerced = coerceValue(endIndexSpinner, oldVal, newVal, getStartValue());
            if (coerced < getStartValue()) {
                startIndexSpinner.getValueFactory().setValue(coerced);
            }
            placeholderDisplayUpdater.run();
        });

        previousPlaceholderButton.setOnAction(event -> placeholderAdjuster.accept(-1));
        nextPlaceholderButton.setOnAction(event -> placeholderAdjuster.accept(1));
    }

    public int getStartValue() {
        return extractValue(startIndexSpinner, 1);
    }

    public int getEndValue() {
        return extractValue(endIndexSpinner, getStartValue());
    }

    public void showStandaloneRange(int currentPlaceholderValue) {
        int startValue = getStartValue();
        int endValue = getEndValue();
        currentPlaceholderLabel.setText("Index: " + currentPlaceholderValue + " (" + startValue + "-" + endValue + ")");
        previousPlaceholderButton.setDisable(true);
        nextPlaceholderButton.setDisable(true);
    }

    public void showContext(int placeholderValue, int currentIndex, int totalContexts,
                            double achievedPoints, double maxPoints) {
        currentPlaceholderLabel.setText("Repo " + formatPlaceholder(placeholderValue)
                + " - Punkte: " + pointFormat.format(achievedPoints)
                + " / " + pointFormat.format(maxPoints));
        previousPlaceholderButton.setDisable(currentIndex <= 0);
        nextPlaceholderButton.setDisable(currentIndex >= totalContexts - 1);
    }

    private Integer coerceValue(Spinner<Integer> spinner, Integer oldValue, Integer newValue, int fallback) {
        if (newValue == null) {
            Integer resolved =
                    oldValue != null ?
                    oldValue :
                    fallback;
            spinner.getValueFactory().setValue(resolved);
            return resolved;
        }
        return newValue;
    }

    private int extractValue(Spinner<Integer> spinner, int fallback) {
        spinner.increment(0);
        Integer value = spinner.getValue();
        return value != null ?
               value :
               fallback;
    }

    private String formatPlaceholder(int value) {
        return String.format(java.util.Locale.ROOT, "%03d", value);
    }
}
