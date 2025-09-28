package dev.phlp.stud.evaluator.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import dev.phlp.stud.evaluator.controller.CommandTerminalController.CommandOutcome;
import dev.phlp.stud.evaluator.model.EvaluationNode;
import dev.phlp.stud.evaluator.model.EvaluationStatus;
import dev.phlp.stud.evaluator.service.command.CommandRunner;
import dev.phlp.stud.evaluator.service.workflow.CommandExecutionContext;
import dev.phlp.stud.evaluator.service.workflow.EvaluationWorkflow;

/**
 * Runs all runnable child tasks of a category sequentially while providing
 * feedback about their execution state and logs.
 */
public final class BatchCommandRunnerController {

    private static final String[] ROW_STYLE_CLASSES = {
            "batch-pending", "batch-running", "batch-success", "batch-failed", "batch-cancelled"
    };
    private final ObservableList<BatchEntry> entries = FXCollections.observableArrayList();
    @FXML
    private ListView<BatchEntry> taskListView;
    @FXML
    private Label headerLabel;
    @FXML
    private Label taskTitleLabel;
    @FXML
    private Label statusValueLabel;
    @FXML
    private Label exitCodeValueLabel;
    @FXML
    private Label maxPointsValueLabel;
    @FXML
    private Spinner<Double> pointsSpinner;
    @FXML
    private TextArea logArea;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private Button closeButton;
    private EvaluationWorkflow workflow;
    private Stage stage;
    private BatchEntry currentEntry;
    private boolean updatingSpinnerValue;

    @FXML
    private void initialize() {
        taskListView.setItems(entries);
        taskListView.setCellFactory(list -> new TaskCell());
        taskListView.getSelectionModel().selectedItemProperty().addListener((obs, oldEntry, newEntry) -> {
            if (oldEntry != null) {
                commitSpinnerEditor(pointsSpinner);
            }
            currentEntry = newEntry;
            updateDetailView(newEntry);
        });

        pointsSpinner.setEditable(true);
        pointsSpinner.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commitSpinnerEditor(pointsSpinner);
            }
        });
        pointsSpinner.getEditor().setOnAction(event -> commitSpinnerEditor(pointsSpinner));
        pointsSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingSpinnerValue && currentEntry != null && newValue != null) {
                currentEntry.getNode().setAchievedPoints(newValue);
            }
        });
        logArea.setEditable(false);
        progressIndicator.setVisible(false);
    }

    public void configure(Stage stage, EvaluationWorkflow workflow, String categoryName, List<EvaluationNode> runnableNodes) {
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
        this.workflow = Objects.requireNonNull(workflow, "workflow must not be null");
        Objects.requireNonNull(runnableNodes, "runnableNodes must not be null");

        headerLabel.setText(categoryName);
        closeButton.setOnAction(event -> this.stage.close());

        entries.setAll(runnableNodes.stream().map(BatchEntry::new).toList());
        if (!entries.isEmpty()) {
            taskListView.getSelectionModel().select(0);
        } else {
            updateDetailView(null);
        }

        runSequentially();
    }

    private void runSequentially() {
        if (entries.isEmpty()) {
            return;
        }
        progressIndicator.setVisible(true);
        closeButton.setDisable(true);

        CompletableFuture.runAsync(() -> {
            for (BatchEntry entry : entries) {
                executeEntry(entry);
            }
        }).whenComplete((ignored, throwable) -> Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            closeButton.setDisable(false);
            if (throwable != null) {
                logArea.appendText(System.lineSeparator() + "[ERR] " + throwable.getMessage() + System.lineSeparator());
            }
        }));
    }

    private void executeEntry(BatchEntry entry) {
        Optional<CommandExecutionContext> contextOptional = workflow.createCommandExecutionContext(entry.getNode());
        if (contextOptional.isEmpty()) {
            Platform.runLater(() -> {
                entry.setStatus(EvaluationStatus.FAILED);
                entry.setExitCode(0, false);
                appendLogSnapshot(entry, "[ERR] Kontext konnte nicht erstellt werden.");
                refreshDetailsIfSelected(entry);
            });
            return;
        }

        CommandExecutionContext context = contextOptional.get();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger lastExitCode = new AtomicInteger(0);
        AtomicBoolean encounteredFailure = new AtomicBoolean(false);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        entry.clearLogBuffer();
        Platform.runLater(() -> {
            entry.setLogText("");
            entry.clearExitCode();
            entry.setStatus(EvaluationStatus.RUNNING);
            if (taskListView.getSelectionModel().getSelectedItem() == null) {
                taskListView.getSelectionModel().select(entry);
            }
            updateDetailView(entry);
            workflow.onCommandExecutionStarted(entry.getNode());
        });

        context.commandRunner().runCommands(
                entry.getNode().getCommands(),
                context.repositoryPath(),
                new CommandRunner.CommandOutputListener() {
                    @Override
                    public void onCommandStarted(String command) {
                        appendLogSnapshot(entry, "$ " + command);
                    }

                    @Override
                    public void onStdout(String line) {
                        appendLogSnapshot(entry, "[OUT] " + line);
                    }

                    @Override
                    public void onStderr(String line) {
                        appendLogSnapshot(entry, "[ERR] " + line);
                    }

                    @Override
                    public void onCommandFinished(String command, int exitCode) {
                        appendLogSnapshot(entry, "Command beendet (Exit " + exitCode + ")");
                        lastExitCode.set(exitCode);
                        if (exitCode != 0) {
                            encounteredFailure.set(true);
                        }
                    }

                    @Override
                    public void onFailure(String command, Exception exception) {
                        encounteredFailure.set(true);
                        appendLogSnapshot(entry, "Fehler bei '" + command + "': " + exception.getMessage());
                    }

                    @Override
                    public void onAllCommandsFinished(boolean cancelledExecution) {
                        cancelled.set(cancelledExecution);
                        latch.countDown();
                    }
                });

        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            encounteredFailure.set(true);
        }

        CommandOutcome outcome;
        if (cancelled.get()) {
            outcome = CommandOutcome.CANCELLED;
        } else if (encounteredFailure.get()) {
            outcome = CommandOutcome.FAILED;
        } else {
            outcome = CommandOutcome.SUCCESS;
        }

        int exitCodeValue =
                outcome == CommandOutcome.SUCCESS ?
                0 :
                lastExitCode.get();
        Path logPath = null;
        try {
            logPath = context.commandLogService().writeLog(
                    context.evaluationDirectory(),
                    entry.getNode().getQualifiedName(),
                    entry.getNode().getCommands(),
                    entry.snapshotLog());
        } catch (IOException ex) {
            appendLogSnapshot(entry, "[ERR] Log konnte nicht gespeichert werden: " + ex.getMessage());
        }

        Path finalLogPath = logPath;
        Platform.runLater(() -> {
            entry.setExitCode(exitCodeValue, true);
            switch (outcome) {
                case SUCCESS -> {
                    entry.setStatus(EvaluationStatus.SUCCESS);
                    entry.getNode().setAchievedPoints(entry.getNode().getMaxPoints());
                }
                case FAILED -> {
                    entry.setStatus(EvaluationStatus.FAILED);
                    entry.getNode().setAchievedPoints(0.0);
                }
                case CANCELLED -> {
                    entry.setStatus(EvaluationStatus.CANCELLED);
                    entry.getNode().setAchievedPoints(0.0);
                }
            }

            workflow.onCommandExecutionFinished(entry.getNode(), outcome);
            if (finalLogPath != null) {
                workflow.recordLogReference(entry.getNode(), finalLogPath);
            }
            refreshDetailsIfSelected(entry);
        });
    }

    private void updateDetailView(BatchEntry entry) {
        if (entry == null) {
            taskTitleLabel.setText("Aufgabe: -");
            statusValueLabel.setText("-");
            exitCodeValueLabel.setText("-");
            maxPointsValueLabel.setText("-");
            logArea.clear();
            pointsSpinner.setDisable(true);
            return;
        }
        taskTitleLabel.setText("Aufgabe: " + entry.getNode().getName());
        statusValueLabel.setText(describeStatus(entry.getStatus()));
        exitCodeValueLabel.setText(
                entry.isExitCodeDefined() ?
                Integer.toString(entry.getExitCode()) :
                "-");
        double maxPoints = entry.getNode().getMaxPoints();
        maxPointsValueLabel.setText(formatPoints(maxPoints));
        double spinnerMax =
                maxPoints > 0.0 ?
                maxPoints :
                Math.max(Math.max(entry.getNode().getAchievedPoints(), 0.0), 100.0);
        SpinnerValueFactory.DoubleSpinnerValueFactory factory =
                new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, spinnerMax, entry.getNode().getAchievedPoints(), 0.5);
        updatingSpinnerValue = true;
        pointsSpinner.setValueFactory(factory);
        updatingSpinnerValue = false;
        pointsSpinner.setDisable(false);
        logArea.setText(entry.getLogText());
    }

    private void refreshDetailsIfSelected(BatchEntry entry) {
        if (entry != null && entry == currentEntry) {
            updateDetailView(entry);
        }
    }

    private void appendLogSnapshot(BatchEntry entry, String line) {
        String snapshot = entry.appendLine(line);
        Platform.runLater(() -> {
            entry.setLogText(snapshot);
            if (entry == currentEntry) {
                logArea.setText(snapshot);
            }
        });
    }

    private void commitSpinnerEditor(Spinner<Double> spinner) {
        if (spinner == null) {
            return;
        }
        String text = spinner.getEditor().getText();
        try {
            double value = Double.parseDouble(text.replace(',', '.'));
            SpinnerValueFactory<Double> factory = spinner.getValueFactory();
            if (factory instanceof SpinnerValueFactory.DoubleSpinnerValueFactory doubleFactory) {
                value = Math.max(doubleFactory.getMin(), Math.min(value, doubleFactory.getMax()));
            }
            factory.setValue(value);
        } catch (NumberFormatException ex) {
            spinner.increment(0);
        }
    }

    private String describeStatus(EvaluationStatus status) {
        if (status == null) {
            return "Unbekannt";
        }
        return switch (status) {
            case PENDING ->
                    "Ausstehend";
            case RUNNING ->
                    "Laeuft";
            case SUCCESS ->
                    "Erfolgreich";
            case FAILED ->
                    "Fehlgeschlagen";
            case CANCELLED ->
                    "Abgebrochen";
        };
    }

    private String formatPoints(double value) {
        if (value == (long) value) {
            return String.format(Locale.ROOT, "%d", (long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static final class BatchEntry {
        private final EvaluationNode node;
        private final StringBuilder logBuffer = new StringBuilder();
        private final StringProperty logText = new SimpleStringProperty("");
        private final ObjectProperty<EvaluationStatus> status = new SimpleObjectProperty<>(EvaluationStatus.PENDING);
        private final IntegerProperty exitCode = new SimpleIntegerProperty(0);
        private final BooleanProperty exitCodeDefined = new SimpleBooleanProperty(false);

        private BatchEntry(EvaluationNode node) {
            this.node = Objects.requireNonNull(node, "node");
        }

        EvaluationNode getNode() {
            return node;
        }

        ObjectProperty<EvaluationStatus> statusProperty() {
            return status;
        }

        EvaluationStatus getStatus() {
            return status.get();
        }

        void setStatus(EvaluationStatus newStatus) {
            status.set(newStatus);
        }

        String appendLine(String line) {
            synchronized (logBuffer) {
                logBuffer.append(line).append(System.lineSeparator());
                return logBuffer.toString();
            }
        }

        CharSequence snapshotLog() {
            synchronized (logBuffer) {
                return new StringBuilder(logBuffer);
            }
        }

        void clearLogBuffer() {
            synchronized (logBuffer) {
                logBuffer.setLength(0);
            }
        }

        String getLogText() {
            return logText.get();
        }

        void setLogText(String text) {
            logText.set(text);
        }

        void setExitCode(int value, boolean defined) {
            exitCode.set(value);
            exitCodeDefined.set(defined);
        }

        int getExitCode() {
            return exitCode.get();
        }

        boolean isExitCodeDefined() {
            return exitCodeDefined.get();
        }

        void clearExitCode() {
            setExitCode(0, false);
        }
    }

    private final class TaskCell extends ListCell<BatchEntry> {
        private final javafx.beans.value.ChangeListener<EvaluationStatus> statusListener =
                (obs, oldStatus, newStatus) -> {
                    BatchEntry item = getItem();
                    if (item != null) {
                        updateCellText(item);
                    }
                    applyStyle(newStatus);
                };

        @Override
        protected void updateItem(BatchEntry entry, boolean empty) {
            BatchEntry previous = getItem();
            if (previous != null) {
                previous.statusProperty().removeListener(statusListener);
            }
            super.updateItem(entry, empty);

            getStyleClass().removeAll(ROW_STYLE_CLASSES);
            if (empty || entry == null) {
                setText(null);
                return;
            }

            updateCellText(entry);
            entry.statusProperty().addListener(statusListener);
            applyStyle(entry.getStatus());
        }

        private void updateCellText(BatchEntry entry) {
            setText(entry.getNode().getName() + " — " + describeStatus(entry.getStatus()));
        }

        private void applyStyle(EvaluationStatus status) {
            getStyleClass().removeAll(ROW_STYLE_CLASSES);
            if (status == null) {
                getStyleClass().add("batch-pending");
                return;
            }
            switch (status) {
                case SUCCESS ->
                        getStyleClass().add("batch-success");
                case FAILED ->
                        getStyleClass().add("batch-failed");
                case RUNNING ->
                        getStyleClass().add("batch-running");
                case CANCELLED ->
                        getStyleClass().add("batch-cancelled");
                case PENDING ->
                        getStyleClass().add("batch-pending");
            }
        }
    }
}
