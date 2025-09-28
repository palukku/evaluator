package dev.phlp.stud.evaluator.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import dev.phlp.stud.evaluator.service.command.CommandLogService;
import dev.phlp.stud.evaluator.service.command.CommandRunner;

public class CommandTerminalController {
    public enum CommandOutcome {
        SUCCESS,
        FAILED,
        CANCELLED
    }
    private static final DecimalFormat POINT_FORMAT = new DecimalFormat("0.##");
    private final StringBuilder logBuffer = new StringBuilder();
    @FXML
    private Label titleLabel;
    @FXML
    private TextArea outputArea;
    @FXML
    private Label instructionsLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Button cancelButton;
    @FXML
    private Button closeButton;
    @FXML
    private Button rerunButton;
    @FXML
    private Button applyPointsButton;
    @FXML
    private Button maxPointsButton;
    @FXML
    private Button zeroPointsButton;
    @FXML
    private Spinner<Double> pointsSpinner;
    @FXML
    private ProgressIndicator progressIndicator;
    private Stage stage;
    private CommandRunner commandRunner;
    private CommandLogService commandLogService;
    private CommandRunner.CommandExecution execution;
    private List<String> commands;
    private Path repositoryPath;
    private Path evaluationDirectory;
    private String nodeQualifiedName;
    private TerminalResultHandler resultHandler;
    private double maxPoints;
    private double currentPoints;
    private boolean encounteredFailure;
    private boolean isRunning;

    public void configure(Stage stage, String nodeQualifiedName, List<String> commands, Path repositoryPath,
                          Path evaluationDirectory, double maxPoints, double currentPoints, CommandRunner commandRunner,
                          CommandLogService commandLogService, TerminalResultHandler resultHandler) {
        this.stage = stage;
        this.nodeQualifiedName = nodeQualifiedName;
        this.commands = commands;
        this.repositoryPath = repositoryPath;
        this.evaluationDirectory = evaluationDirectory;
        this.commandRunner = commandRunner;
        this.commandLogService = commandLogService;
        this.resultHandler = resultHandler;
        this.maxPoints = maxPoints;
        this.currentPoints = currentPoints;

        titleLabel.setText(nodeQualifiedName);
        outputArea.clear();
        statusLabel.setText("");
        progressIndicator.setVisible(true);
        cancelButton.setDisable(false);
        rerunButton.setDisable(true);
        configurePointsControls();
        updateInstructions();

        cancelButton.setOnAction(event -> {
            if (execution != null) {
                execution.cancel();
                cancelButton.setDisable(true);
                statusLabel.setText("Abbruch wird ausgefuehrt...");
            }
        });
        rerunButton.setOnAction(event -> runCommands(true));
        closeButton.setOnAction(event -> stage.close());

        runCommands(false);
    }

    private void configurePointsControls() {
        if (pointsSpinner != null) {
            double spinnerMax =
                    maxPoints > 0 ?
                    maxPoints :
                    Math.max(Math.max(currentPoints, 0.0), 100.0);
            SpinnerValueFactory.DoubleSpinnerValueFactory factory =
                    new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, spinnerMax, clampPoints(currentPoints), 0.5);
            pointsSpinner.setValueFactory(factory);
            pointsSpinner.setEditable(true);
            pointsSpinner.focusedProperty().addListener((obs, oldVal, focused) -> {
                if (!focused) {
                    commitSpinnerEditor(pointsSpinner);
                }
            });
        }
        if (applyPointsButton != null) {
            applyPointsButton.setOnAction(event -> {
                commitSpinnerEditor(pointsSpinner);
                Double value =
                        pointsSpinner != null ?
                        pointsSpinner.getValue() :
                        currentPoints;
                awardPoints(
                        value != null ?
                        value :
                        0.0);
                stage.close();
            });
        }
        if (maxPointsButton != null) {
            maxPointsButton.setDisable(maxPoints <= 0.0);
            maxPointsButton.setOnAction(event -> {
                double target =
                        maxPoints > 0.0 ?
                        maxPoints :
                        currentPoints;
                if (pointsSpinner != null) {
                    ensureSpinnerRange(target);
                    pointsSpinner.getValueFactory().setValue(clampPoints(target));
                }
                awardPoints(target);
                stage.close();
            });
        }
        if (zeroPointsButton != null) {
            zeroPointsButton.setOnAction(event -> {
                if (pointsSpinner != null) {
                    pointsSpinner.getValueFactory().setValue(0.0);
                }
                awardPoints(0.0);
                stage.close();
            });
        }
    }

    private void updateInstructions() {
        if (instructionsLabel == null) {
            return;
        }
        if (maxPoints > 0) {
            instructionsLabel.setText("Hinweis: Ueber die Punktsteuerung lassen sich bis zu "
                    + POINT_FORMAT.format(maxPoints) + " Punkte vergeben.");
        } else {
            instructionsLabel.setText("Hinweis: Punktzahl bitte per Spinner eingeben und setzen.");
        }
    }

    private void runCommands(boolean isRetry) {
        if (isRunning || commandRunner == null || commands == null || repositoryPath == null) {
            return;
        }
        encounteredFailure = false;
        isRunning = true;
        logBuffer.setLength(0);
        progressIndicator.setVisible(true);
        cancelButton.setDisable(false);
        rerunButton.setDisable(true);
        statusLabel.setText("Ausfuehrung laeuft...");
        if (!isRetry) {
            outputArea.clear();
        } else {
            appendLine("");
            appendLine("=== Erneute Ausfuehrung ===");
        }
        if (resultHandler != null) {
            resultHandler.onExecutionStarted();
        }
        execution = commandRunner.runCommands(commands, repositoryPath, new CommandRunner.CommandOutputListener() {
            @Override
            public void onCommandStarted(String command) {
                appendLine("$ " + command);
                logBuffer.append("$ ").append(command).append(System.lineSeparator());
            }

            @Override
            public void onStdout(String line) {
                appendLine("[OUT] " + line);
                logBuffer.append(line).append(System.lineSeparator());
            }

            @Override
            public void onStderr(String line) {
                appendLine("[ERR] " + line);
                logBuffer.append("[ERR] ").append(line).append(System.lineSeparator());
            }

            @Override
            public void onCommandFinished(String command, int exitCode) {
                appendLine("Command beendet (Exit " + exitCode + ")");
                if (exitCode != 0) {
                    encounteredFailure = true;
                }
            }

            @Override
            public void onFailure(String command, Exception exception) {
                encounteredFailure = true;
                appendLine("Fehler bei '" + command + "': " + exception.getMessage());
            }

            @Override
            public void onAllCommandsFinished(boolean cancelled) {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    cancelButton.setDisable(true);
                    rerunButton.setDisable(false);
                    isRunning = false;
                    execution = null;

                    CommandOutcome outcome;
                    if (cancelled) {
                        statusLabel.setText("Abgebrochen");
                        outcome = CommandOutcome.CANCELLED;
                    } else if (encounteredFailure) {
                        statusLabel.setText("Fehlgeschlagen");
                        outcome = CommandOutcome.FAILED;
                    } else {
                        statusLabel.setText("Fertig");
                        outcome = CommandOutcome.SUCCESS;
                    }

                    persistLogAsync();
                    if (resultHandler != null) {
                        resultHandler.onExecutionFinished(outcome);
                    }
                });
            }
        });
    }

    private void appendLine(String line) {
        Platform.runLater(() -> outputArea.appendText(line + System.lineSeparator()));
    }

    private void persistLogAsync() {
        if (evaluationDirectory == null || commandLogService == null || commands == null || commands.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Path logFile = commandLogService.writeLog(evaluationDirectory, nodeQualifiedName, commands, logBuffer);
                if (resultHandler != null) {
                    Platform.runLater(() -> resultHandler.onLogCreated(logFile));
                }
            } catch (IOException ex) {
                Platform.runLater(() -> appendLine("[ERR] Log konnte nicht gespeichert werden: " + ex.getMessage()));
            }
        });
    }

    private void awardPoints(double points) {
        double effective = clampPoints(points);
        ensureSpinnerRange(effective);
        if (pointsSpinner != null) {
            pointsSpinner.getValueFactory().setValue(effective);
        }
        currentPoints = effective;
        statusLabel.setText("Punkte gesetzt: " + POINT_FORMAT.format(effective));
        if (resultHandler != null) {
            resultHandler.onPointsAwarded(effective);
        }
    }

    private void ensureSpinnerRange(double value) {
        if (pointsSpinner == null) {
            return;
        }
        SpinnerValueFactory<Double> factory = pointsSpinner.getValueFactory();
        if (factory instanceof SpinnerValueFactory.DoubleSpinnerValueFactory doubleFactory && maxPoints <= 0.0) {
            if (value > doubleFactory.getMax()) {
                doubleFactory.setMax(Math.max(value, doubleFactory.getMax() * 2));
            }
        }
    }

    private double clampPoints(double points) {
        if (maxPoints > 0.0) {
            return Math.max(0.0, Math.min(points, maxPoints));
        }
        return Math.max(0.0, points);
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

    public interface TerminalResultHandler {
        void onExecutionStarted();

        void onExecutionFinished(CommandOutcome outcome);

        void onPointsAwarded(double points);

        void onLogCreated(Path relativeLogPath);
    }
}
