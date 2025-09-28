package dev.phlp.stud.evaluator.controller;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;

import dev.phlp.stud.evaluator.core.events.EventBus;
import dev.phlp.stud.evaluator.events.CheckoutInfoChanged;
import dev.phlp.stud.evaluator.events.CloneProgressUpdated;
import dev.phlp.stud.evaluator.events.CloneProgressVisibilityChanged;
import dev.phlp.stud.evaluator.events.StatusLogEntryAdded;
import dev.phlp.stud.evaluator.events.StatusMessageUpdated;
import dev.phlp.stud.evaluator.events.TotalsUpdated;

/**
 * Handles bottom status bar including totals, clone progress and log display.
 */
public final class StatusBarController {

    private static final int MAX_LOG_ENTRIES = 200;
    private static final DecimalFormat POINT_FORMAT = new DecimalFormat("0.##");

    private final EventBus events;
    private final List<String> logEntries = new ArrayList<>();
    @FXML
    private Label totalPointsLabel;
    @FXML
    private ProgressBar cloneProgressBar;
    @FXML
    private Label cloneProgressLabel;
    @FXML
    private Label checkoutInfoLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Hyperlink logDetailsToggle;
    @FXML
    private TitledPane logDetailsPane;
    @FXML
    private TextArea logDetailsArea;
    private AutoCloseable totalsSubscription;
    private AutoCloseable progressVisibilitySubscription;
    private AutoCloseable progressSubscription;
    private AutoCloseable statusSubscription;
    private AutoCloseable logSubscription;
    private AutoCloseable checkoutSubscription;

    public StatusBarController(EventBus events) {
        this.events = Objects.requireNonNull(events, "EventBus must not be null");
    }

    @FXML
    public void initialize() {
        logDetailsPane.setVisible(false);
        logDetailsPane.setManaged(false);
        logDetailsToggle.setDisable(true);
        logDetailsToggle.setOnAction(event -> toggleLogDetails());
        cloneProgressBar.setVisible(false);
        cloneProgressBar.setManaged(false);
        cloneProgressLabel.setVisible(false);
        cloneProgressLabel.setManaged(false);

        totalsSubscription = events.subscribe(TotalsUpdated.class, this::handleTotalsUpdated);
        progressVisibilitySubscription = events.subscribe(CloneProgressVisibilityChanged.class, this::handleProgressVisibility);
        progressSubscription = events.subscribe(CloneProgressUpdated.class, this::handleProgressUpdate);
        statusSubscription = events.subscribe(StatusMessageUpdated.class, this::handleStatusMessage);
        logSubscription = events.subscribe(StatusLogEntryAdded.class, this::handleLogEntry);
        checkoutSubscription = events.subscribe(CheckoutInfoChanged.class, this::handleCheckoutInfo);
    }

    private void toggleLogDetails() {
        boolean show = !logDetailsPane.isVisible();
        logDetailsPane.setVisible(show);
        logDetailsPane.setManaged(show);
        logDetailsToggle.setText(
                show ?
                "Details verbergen" :
                "Details anzeigen");
        if (show) {
            logDetailsToggle.getStyleClass().remove("log-highlight");
        }
    }

    private void handleTotalsUpdated(TotalsUpdated event) {
        Runnable task = () -> totalPointsLabel.setText(
                POINT_FORMAT.format(event.achievedPoints()) + " / " + POINT_FORMAT.format(event.maxPoints()));
        runOnFxThread(task);
    }

    private void handleProgressVisibility(CloneProgressVisibilityChanged event) {
        Runnable task = () -> {
            cloneProgressBar.setVisible(event.visible());
            cloneProgressBar.setManaged(event.visible());
            cloneProgressLabel.setVisible(event.visible());
            cloneProgressLabel.setManaged(event.visible());
            if (!event.visible()) {
                cloneProgressBar.setProgress(0);
                cloneProgressLabel.setText("");
            }
        };
        runOnFxThread(task);
    }

    private void handleProgressUpdate(CloneProgressUpdated event) {
        Runnable task = () -> {
            int total = Math.max(event.total(), 0);
            if (total <= 0) {
                cloneProgressBar.setProgress(0);
                cloneProgressLabel.setText("");
                return;
            }
            double progress = Math.min(1.0, Math.max(0.0, (double) event.completed() / total));
            cloneProgressBar.setProgress(progress);
            cloneProgressLabel.setText(event.completed() + " / " + total);
        };
        runOnFxThread(task);
    }

    private void handleStatusMessage(StatusMessageUpdated event) {
        Runnable task = () -> statusLabel.setText(event.message());
        runOnFxThread(task);
    }

    private void handleLogEntry(StatusLogEntryAdded event) {
        Runnable task = () -> {
            logEntries.add(buildLogEntry(event));
            if (logEntries.size() > MAX_LOG_ENTRIES) {
                logEntries.remove(0);
            }
            logDetailsArea.setText(String.join(System.lineSeparator() + System.lineSeparator(), logEntries));
            logDetailsArea.positionCaret(logDetailsArea.getText().length());
            logDetailsToggle.setDisable(false);
            if (event.error() && !logDetailsToggle.getStyleClass().contains("log-highlight") && !logDetailsPane.isVisible()) {
                logDetailsToggle.getStyleClass().add("log-highlight");
            }
        };
        runOnFxThread(task);
    }

    private String buildLogEntry(StatusLogEntryAdded event) {
        StringBuilder builder = new StringBuilder(
                event.message() != null ?
                event.message() :
                "");
        if (event.stackTrace() != null && !event.stackTrace().isBlank()) {
            builder.append(System.lineSeparator()).append(event.stackTrace());
        }
        return builder.toString();
    }

    private void handleCheckoutInfo(CheckoutInfoChanged event) {
        Runnable task = () -> checkoutInfoLabel.setText(event.text());
        runOnFxThread(task);
    }

    private void runOnFxThread(Runnable task) {
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    public void shutdown() {
        closeQuietly(totalsSubscription);
        closeQuietly(progressVisibilitySubscription);
        closeQuietly(progressSubscription);
        closeQuietly(statusSubscription);
        closeQuietly(logSubscription);
        closeQuietly(checkoutSubscription);
    }

    private void closeQuietly(AutoCloseable handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (Exception ignored) {
            // ignore
        }
    }
}
