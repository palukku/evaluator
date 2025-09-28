package dev.phlp.stud.evaluator.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import dev.phlp.stud.evaluator.controller.ui.EvaluationTreeViewAdapter;
import dev.phlp.stud.evaluator.core.events.EventBus;
import dev.phlp.stud.evaluator.events.EvaluationNodeSelected;
import dev.phlp.stud.evaluator.events.EvaluationTreeAvailabilityChanged;
import dev.phlp.stud.evaluator.events.EvaluationTreeRefreshRequested;
import dev.phlp.stud.evaluator.events.EvaluationTreeSelectionCleared;
import dev.phlp.stud.evaluator.model.EvaluationNode;
import dev.phlp.stud.evaluator.model.EvaluationStatus;
import dev.phlp.stud.evaluator.service.dialog.DialogService;
import dev.phlp.stud.evaluator.service.workflow.CommandExecutionContext;
import dev.phlp.stud.evaluator.service.workflow.EvaluationWorkflow;

/**
 * Encapsulates the evaluation tree UI including command execution handling.
 */
public final class EvaluationTreeController {

    private enum RowVisualStatus {
        GREY,
        GREEN,
        ORANGE,
        RED
    }
    private static final String[] ROW_STYLE_CLASSES = {
            "run-neutral", "run-pending", "run-running", "run-success", "run-failed",
            "manual-grey", "manual-green", "manual-orange", "manual-red"
    };
    private final EvaluationWorkflow workflow;
    private final EventBus events;
    private final DialogService dialogService;
    @FXML
    private TreeTableView<EvaluationNode> evaluationTreeTable;
    @FXML
    private TreeTableColumn<EvaluationNode, String> nameColumn;
    @FXML
    private TreeTableColumn<EvaluationNode, EvaluationNode> actionsColumn;
    @FXML
    private TreeTableColumn<EvaluationNode, Number> maxPointsColumn;
    @FXML
    private TreeTableColumn<EvaluationNode, EvaluationNode> achievedPointsColumn;
    private TreeItem<EvaluationNode> treeRoot;
    private AutoCloseable availabilitySubscription;
    private AutoCloseable refreshSubscription;
    private AutoCloseable selectionClearSubscription;

    public EvaluationTreeController(EvaluationWorkflow workflow, EventBus events, DialogService dialogService) {
        this.workflow = Objects.requireNonNull(workflow, "EvaluationWorkflow must not be null");
        this.events = Objects.requireNonNull(events, "EventBus must not be null");
        this.dialogService = Objects.requireNonNull(dialogService, "DialogService must not be null");
    }

    private static String formatPoints(Number value) {
        if (value == null) {
            return "";
        }
        double doubleValue = value.doubleValue();
        if (doubleValue == (long) doubleValue) {
            return String.format(Locale.ROOT, "%d", (long) doubleValue);
        }
        return String.format(Locale.ROOT, "%.2f", doubleValue);
    }

    @FXML
    public void initialize() {
        EvaluationTreeViewAdapter adapter = new EvaluationTreeViewAdapter(
                evaluationTreeTable,
                nameColumn,
                actionsColumn,
                maxPointsColumn,
                achievedPointsColumn,
                EvaluationTreeController::formatPoints,
                ROW_STYLE_CLASSES,
                this::resolveRowStyle,
                this::playCommands,
                this::handleCheckBox,
                this::updateCheckBoxState,
                workflow::isRepositoryReady);
        adapter.initialize();

        treeRoot = new TreeItem<>(new EvaluationNode("root", "root", 0.0, List.of(), "", false));
        treeRoot.setExpanded(true);
        evaluationTreeTable.setRoot(treeRoot);
        evaluationTreeTable.setShowRoot(false);
        evaluationTreeTable.setDisable(true);

        populateTree();
        evaluationTreeTable.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            EvaluationNode selected =
                    newItem != null ?
                    newItem.getValue() :
                    null;
            events.publish(new EvaluationNodeSelected(selected));
        });

        availabilitySubscription = events.subscribe(EvaluationTreeAvailabilityChanged.class,
                event -> setTreeEnabled(event.enabled()));
        refreshSubscription = events.subscribe(EvaluationTreeRefreshRequested.class,
                event -> refreshTree());
        selectionClearSubscription = events.subscribe(EvaluationTreeSelectionCleared.class,
                event -> clearSelection());
    }

    private void populateTree() {
        treeRoot.getChildren().clear();
        workflow.getRootNodes().forEach(node -> treeRoot.getChildren().add(createTreeItem(node)));
    }

    private TreeItem<EvaluationNode> createTreeItem(EvaluationNode node) {
        TreeItem<EvaluationNode> item = new TreeItem<>(node);
        item.setExpanded(true);
        node.getChildren().forEach(child -> item.getChildren().add(createTreeItem(child)));
        return item;
    }

    private void setTreeEnabled(boolean enabled) {
        Runnable task = () -> evaluationTreeTable.setDisable(!enabled);
        runOnFxThread(task);
    }

    private void refreshTree() {
        Runnable task = () -> {
            if (treeRoot.getChildren().isEmpty() && !workflow.getRootNodes().isEmpty()) {
                populateTree();
            } else {
                evaluationTreeTable.refresh();
            }
        };
        runOnFxThread(task);
    }

    private void clearSelection() {
        Runnable task = () -> evaluationTreeTable.getSelectionModel().clearSelection();
        runOnFxThread(task);
    }

    private void runOnFxThread(Runnable task) {
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    private String resolveRowStyle(EvaluationNode node) {
        if (node == null) {
            return null;
        }
        if (!node.getCommands().isEmpty()) {
            EvaluationStatus status = node.getStatus();
            if (status == null) {
                status = EvaluationStatus.PENDING;
            }
            return switch (status) {
                case SUCCESS ->
                        "run-success";
                case FAILED ->
                        "run-failed";
                case RUNNING ->
                        "run-running";
                case PENDING,
                     CANCELLED ->
                        "run-pending";
            };
        }
        RowVisualStatus status = resolveRowStatus(node);
        return switch (status) {
            case GREEN ->
                    "manual-green";
            case ORANGE ->
                    "manual-orange";
            case RED ->
                    "manual-red";
            case GREY ->
                    "manual-grey";
        };
    }

    private RowVisualStatus resolveRowStatus(EvaluationNode node) {
        if (node == null) {
            return RowVisualStatus.GREY;
        }
        if (!node.getCommands().isEmpty()) {
            EvaluationStatus status = node.getStatus();
            if (status == null) {
                status = EvaluationStatus.PENDING;
            }
            return switch (status) {
                case SUCCESS ->
                        RowVisualStatus.GREEN;
                case FAILED ->
                        RowVisualStatus.RED;
                case RUNNING,
                     PENDING,
                     CANCELLED ->
                        RowVisualStatus.GREY;
            };
        }
        if (node.isLeaf() || node.getChildren().isEmpty()) {
            return node.isAchievedPointsDefined() ?
                   RowVisualStatus.GREEN :
                   RowVisualStatus.GREY;
        }
        boolean allGreen = true;
        boolean allRed = true;
        boolean anyRed = false;
        for (EvaluationNode child : node.getChildren()) {
            RowVisualStatus childStatus = resolveRowStatus(child);
            if (childStatus != RowVisualStatus.GREEN) {
                allGreen = false;
            }
            if (childStatus != RowVisualStatus.RED) {
                allRed = false;
            }
            if (childStatus == RowVisualStatus.RED || childStatus == RowVisualStatus.ORANGE) {
                anyRed = true;
            }
        }
        if (allGreen) {
            return RowVisualStatus.GREEN;
        }
        if (allRed) {
            return RowVisualStatus.RED;
        }
        if (anyRed) {
            return RowVisualStatus.ORANGE;
        }
        return RowVisualStatus.GREY;
    }

    private void playCommands(EvaluationNode node) {
        if (!node.getCommands().isEmpty()) {
            workflow.createCommandExecutionContext(node).ifPresent(context -> openCommandTerminal(node, context));
            return;
        }
        List<EvaluationNode> runnableChildren = collectRunnableChildren(node);
        if (runnableChildren.isEmpty()) {
            dialogService.showInfo("Keine Kommandos", "Fuer diesen Eintrag sind keine Befehle definiert.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/batch_command_runner.fxml"));
            Parent root = loader.load();
            Stage runnerStage = new Stage();
            runnerStage.initModality(Modality.NONE);
            runnerStage.setTitle("Ausgabe - " + node.getName());
            Scene runnerScene = new Scene(root);
            applySceneStyles(runnerScene);
            runnerStage.setScene(runnerScene);

            BatchCommandRunnerController controller = loader.getController();
            controller.configure(runnerStage, workflow, node.getQualifiedName(), runnableChildren);

            Stage owner = (Stage) evaluationTreeTable.getScene().getWindow();
            if (owner != null) {
                runnerStage.initOwner(owner);
            }
            runnerStage.show();
        } catch (IOException ex) {
            dialogService.showError("Terminal konnte nicht geoeffnet werden", ex.getMessage());
        }
    }

    private void openCommandTerminal(EvaluationNode node, CommandExecutionContext context) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/command_terminal.fxml"));
            Parent root = loader.load();
            Stage terminalStage = new Stage();
            terminalStage.initModality(Modality.NONE);
            terminalStage.setTitle("Ausgabe - " + node.getName());
            Scene terminalScene = new Scene(root);
            applySceneStyles(terminalScene);
            terminalStage.setScene(terminalScene);

            workflow.onCommandExecutionStarted(node);
            refreshTree();

            CommandTerminalController controller = loader.getController();
            controller.configure(terminalStage,
                    node.getQualifiedName(),
                    node.getCommands(),
                    context.repositoryPath(),
                    context.evaluationDirectory(),
                    node.getMaxPoints(),
                    context.achievedPoints(),
                    context.commandRunner(),
                    context.commandLogService(),
                    new CommandTerminalController.TerminalResultHandler() {
                        @Override
                        public void onExecutionStarted() {
                            workflow.onCommandExecutionStarted(node);
                            refreshTree();
                        }

                        @Override
                        public void onExecutionFinished(CommandTerminalController.CommandOutcome outcome) {
                            workflow.onCommandExecutionFinished(node, outcome);
                            refreshTree();
                        }

                        @Override
                        public void onPointsAwarded(double points) {
                            node.setAchievedPoints(points);
                            refreshTree();
                        }

                        @Override
                        public void onLogCreated(Path relativePath) {
                            workflow.recordLogReference(node, relativePath);
                        }
                    });
            Stage owner = (Stage) evaluationTreeTable.getScene().getWindow();
            if (owner != null) {
                terminalStage.initOwner(owner);
            }
            terminalStage.show();
        } catch (IOException ex) {
            dialogService.showError("Terminal konnte nicht geoeffnet werden", ex.getMessage());
        }
    }

    private void applySceneStyles(Scene scene) {
        Scene ownerScene = evaluationTreeTable.getScene();
        if (ownerScene != null) {
            scene.getStylesheets().addAll(ownerScene.getStylesheets());
        }
    }

    private List<EvaluationNode> collectRunnableChildren(EvaluationNode node) {
        List<EvaluationNode> result = new ArrayList<>();
        collectRunnableChildren(node, result);
        return result;
    }

    private void collectRunnableChildren(EvaluationNode node, List<EvaluationNode> target) {
        for (EvaluationNode child : node.getChildren()) {
            if (!child.getCommands().isEmpty()) {
                target.add(child);
            } else {
                collectRunnableChildren(child, target);
            }
        }
    }

    private void handleCheckBox(EvaluationNode node, CheckBox checkBox) {
        if (checkBox.isIndeterminate()) {
            return;
        }
        if (checkBox.isSelected()) {
            node.markFullyAwarded();
        } else {
            node.resetToZero();
        }
        refreshTree();
    }

    private void updateCheckBoxState(EvaluationNode node, CheckBox checkBox) {
        if (node.isFullyAwarded()) {
            checkBox.setIndeterminate(false);
            checkBox.setSelected(true);
        } else if (node.hasPartialAward()) {
            checkBox.setIndeterminate(true);
            checkBox.setSelected(false);
        } else {
            checkBox.setIndeterminate(false);
            checkBox.setSelected(false);
        }
    }

    public void shutdown() {
        closeQuietly(availabilitySubscription);
        closeQuietly(refreshSubscription);
        closeQuietly(selectionClearSubscription);
    }

    private void closeQuietly(AutoCloseable handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (Exception ignored) {
            // ignore errors during shutdown
        }
    }
}
