package dev.phlp.stud.evaluator.controller;

import java.nio.file.Path;
import java.util.Objects;

import javafx.fxml.FXML;
import javafx.stage.Stage;

import dev.phlp.stud.evaluator.model.config.EvaluationConfig;
import dev.phlp.stud.evaluator.service.dialog.DialogService;
import dev.phlp.stud.evaluator.service.workflow.EvaluationWorkflow;

/**
 * Composition root for the main view. Delegates all domain logic to dedicated
 * services and controllers.
 */
public final class MainController {

    private final EvaluationWorkflow workflow;
    private final DialogService dialogService;

    @FXML
    private RepositoryToolbarController repositoryToolbarController;
    @FXML
    private EvaluationTreeController evaluationTreeController;
    @FXML
    private CommentPaneController commentPaneController;
    @FXML
    private StatusBarController statusBarController;

    public MainController(EvaluationWorkflow workflow, DialogService dialogService) {
        this.workflow = Objects.requireNonNull(workflow, "EvaluationWorkflow must not be null");
        this.dialogService = Objects.requireNonNull(dialogService, "DialogService must not be null");
    }

    @FXML
    private void initialize() {
        // no-op; controllers initialize themselves
    }

    public void init(Stage stage, EvaluationConfig config, Path baseDirectory) {
        dialogService.setOwner(stage);
        workflow.initialize(stage, config, baseDirectory);
    }

    public void shutdown() {
        if (repositoryToolbarController != null) {
            repositoryToolbarController.shutdown();
        }
        if (evaluationTreeController != null) {
            evaluationTreeController.shutdown();
        }
        if (commentPaneController != null) {
            commentPaneController.shutdown();
        }
        if (statusBarController != null) {
            statusBarController.shutdown();
        }
        workflow.shutdown();
    }
}
