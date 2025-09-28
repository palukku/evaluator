package dev.phlp.stud.evaluator.controller;

import java.text.DecimalFormat;
import java.util.Objects;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;

import dev.phlp.stud.evaluator.controller.ui.RepositoryNavigationBar;
import dev.phlp.stud.evaluator.core.events.EventBus;
import dev.phlp.stud.evaluator.events.RepositoryActionsAvailabilityChanged;
import dev.phlp.stud.evaluator.events.RepositoryConfigurationLoaded;
import dev.phlp.stud.evaluator.events.RepositoryContextActivated;
import dev.phlp.stud.evaluator.events.RepositoryStandaloneModeActivated;
import dev.phlp.stud.evaluator.service.workflow.EvaluationWorkflow;

public final class RepositoryToolbarController {

    private static final DecimalFormat POINT_FORMAT = new DecimalFormat("0.##");

    private final EvaluationWorkflow workflow;
    private final EventBus events;

    @FXML
    private TextField repositoryTemplateField;
    @FXML
    private Spinner<Integer> startIndexSpinner;
    @FXML
    private Spinner<Integer> endIndexSpinner;
    @FXML
    private Button previousPlaceholderButton;
    @FXML
    private Button nextPlaceholderButton;
    @FXML
    private Label currentPlaceholderLabel;
    @FXML
    private Label tagValueLabel;
    @FXML
    private Label deadlineValueLabel;
    @FXML
    private Button cloneButton;
    @FXML
    private Button exportMarkdownButton;

    private RepositoryNavigationBar repositoryNavigationBar;
    private boolean contextMode;
    private int standalonePlaceholderValue = 1;
    private AutoCloseable configurationSubscription;
    private AutoCloseable standaloneSubscription;
    private AutoCloseable contextSubscription;
    private AutoCloseable availabilitySubscription;

    public RepositoryToolbarController(EvaluationWorkflow workflow, EventBus events) {
        this.workflow = Objects.requireNonNull(workflow, "EvaluationWorkflow must not be null");
        this.events = Objects.requireNonNull(events, "EventBus must not be null");
    }

    @FXML
    public void initialize() {
        repositoryNavigationBar = new RepositoryNavigationBar(
                startIndexSpinner,
                endIndexSpinner,
                previousPlaceholderButton,
                nextPlaceholderButton,
                currentPlaceholderLabel,
                POINT_FORMAT,
                this::handlePlaceholderAdjustment,
                this::updateStandaloneDisplay);
        repositoryNavigationBar.initialize();

        cloneButton.setOnAction(event -> handlePreparationRequest());
        exportMarkdownButton.setOnAction(event -> workflow.exportMarkdown());

        tagValueLabel.setText("-");
        deadlineValueLabel.setText("-");
        contextMode = false;
        updateStandaloneDisplay();

        configurationSubscription = events.subscribe(RepositoryConfigurationLoaded.class, this::handleConfigurationLoaded);
        standaloneSubscription = events.subscribe(RepositoryStandaloneModeActivated.class, this::handleStandaloneMode);
        contextSubscription = events.subscribe(RepositoryContextActivated.class, this::handleContextActivated);
        availabilitySubscription = events.subscribe(RepositoryActionsAvailabilityChanged.class, this::handleAvailabilityChanged);
    }

    public void setRepositoryTemplate(String template) {
        repositoryTemplateField.setText(
                template != null ?
                template :
                "");
    }

    public void setTagValue(String value) {
        tagValueLabel.setText(
                value != null && !value.isBlank() ?
                value :
                "-");
    }

    public void setDeadlineValue(String value) {
        deadlineValueLabel.setText(
                value != null && !value.isBlank() ?
                value :
                "-");
    }

    public void setCloneActionEnabled(boolean enabled) {
        cloneButton.setDisable(!enabled);
    }

    public void setExportActionEnabled(boolean enabled) {
        exportMarkdownButton.setDisable(!enabled);
    }

    public int getStartIndex() {
        return repositoryNavigationBar.getStartValue();
    }

    public int getEndIndex() {
        return repositoryNavigationBar.getEndValue();
    }

    public int getStandalonePlaceholderValue() {
        return standalonePlaceholderValue;
    }

    public void enterStandaloneMode(int placeholderValue) {
        contextMode = false;
        setStandalonePlaceholderValue(placeholderValue, false);
    }

    public void showContext(int placeholderValue, int currentIndex, int totalContexts,
                            double achievedPoints, double maxPoints) {
        contextMode = true;
        repositoryNavigationBar.showContext(placeholderValue, currentIndex, totalContexts, achievedPoints, maxPoints);
    }

    private void handlePreparationRequest() {
        String template = repositoryTemplateField.getText();
        template =
                template != null ?
                template.trim() :
                "";
        repositoryTemplateField.setText(template);
        workflow.prepareRepositories(template, getStartIndex(), getEndIndex());
    }

    private void handlePlaceholderAdjustment(int delta) {
        if (contextMode) {
            workflow.adjustPlaceholder(delta);
            return;
        }
        setStandalonePlaceholderValue(standalonePlaceholderValue + delta, true);
    }

    private void updateStandaloneDisplay() {
        setStandalonePlaceholderValue(standalonePlaceholderValue, true);
    }

    private void setStandalonePlaceholderValue(int value, boolean publishEvent) {
        int startValue = repositoryNavigationBar.getStartValue();
        int endValue = repositoryNavigationBar.getEndValue();
        int clamped = Math.max(startValue, Math.min(endValue, value));
        boolean changed = clamped != standalonePlaceholderValue;
        standalonePlaceholderValue = clamped;
        if (!contextMode) {
            repositoryNavigationBar.showStandaloneRange(standalonePlaceholderValue);
            if (publishEvent && changed) {
                workflow.updateStandalonePlaceholder(standalonePlaceholderValue);
            }
        }
    }

    private void handleConfigurationLoaded(RepositoryConfigurationLoaded event) {
        Runnable task = () -> {
            setRepositoryTemplate(event.repositoryTemplate());
            setTagValue(event.tag());
            String deadlineText =
                    event.deadline() != null ?
                    event.deadline().toString() :
                    "-";
            setDeadlineValue(deadlineText);
        };
        runOnFxThread(task);
    }

    private void handleStandaloneMode(RepositoryStandaloneModeActivated event) {
        Runnable task = () -> enterStandaloneMode(event.placeholderValue());
        runOnFxThread(task);
    }

    private void handleContextActivated(RepositoryContextActivated event) {
        Runnable task = () -> showContext(event.placeholderValue(), event.currentIndex(), event.totalContexts(),
                event.achievedPoints(), event.maxPoints());
        runOnFxThread(task);
    }

    private void handleAvailabilityChanged(RepositoryActionsAvailabilityChanged event) {
        Runnable task = () -> {
            setCloneActionEnabled(event.cloneEnabled());
            setExportActionEnabled(event.exportEnabled());
        };
        runOnFxThread(task);
    }

    private void runOnFxThread(Runnable task) {
        if (javafx.application.Platform.isFxApplicationThread()) {
            task.run();
        } else {
            javafx.application.Platform.runLater(task);
        }
    }

    public void shutdown() {
        closeQuietly(configurationSubscription);
        closeQuietly(standaloneSubscription);
        closeQuietly(contextSubscription);
        closeQuietly(availabilitySubscription);
    }

    private void closeQuietly(AutoCloseable handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (Exception ignored) {
            // ignore shutdown errors
        }
    }
}
