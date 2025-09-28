package dev.phlp.stud.evaluator.service.workflow;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javafx.stage.Stage;

import dev.phlp.stud.evaluator.controller.CommandTerminalController;
import dev.phlp.stud.evaluator.model.EvaluationNode;
import dev.phlp.stud.evaluator.model.config.EvaluationConfig;

/**
 * Coordinates evaluation specific workflows and exposes domain operations to
 * UI controllers.
 */
public interface EvaluationWorkflow {

    /**
     * Prepares the workflow for a new evaluation session.
     *
     * @param stage         primary application stage
     * @param config        evaluation configuration
     * @param baseDirectory working directory for repositories/evaluations
     */
    void initialize(Stage stage, EvaluationConfig config, Path baseDirectory);

    /**
     * @return immutable view of the evaluation tree root nodes
     */
    List<EvaluationNode> getRootNodes();

    /**
     * @return whether a repository context is currently available
     */
    boolean isRepositoryReady();

    /**
     * Creates an execution context for launching the command terminal for the
     * given node. Implementations may present error dialogs and return an empty
     * optional if execution is not possible.
     *
     * @param node selected evaluation node
     * @return optional execution context
     */
    Optional<CommandExecutionContext> createCommandExecutionContext(EvaluationNode node);

    /**
     * Notifies the workflow that command execution started for the given node.
     *
     * @param node affected node
     */
    void onCommandExecutionStarted(EvaluationNode node);

    /**
     * Notifies the workflow that command execution finished.
     *
     * @param node    affected node
     * @param outcome execution outcome
     */
    void onCommandExecutionFinished(EvaluationNode node, CommandTerminalController.CommandOutcome outcome);

    /**
     * Registers a produced log file for the given node.
     *
     * @param node         evaluation node
     * @param relativePath log file path relative to evaluation root
     */
    void recordLogReference(EvaluationNode node, Path relativePath);

    /**
     * Invoked when the user adjusted placeholder navigation.
     *
     * @param delta relative adjustment
     */
    void adjustPlaceholder(int delta);

    /**
     * Updates the standalone placeholder value when no repositories are loaded.
     *
     * @param value new placeholder value
     */
    void updateStandalonePlaceholder(int value);

    /**
     * Starts repository preparation with the provided settings.
     */
    void prepareRepositories(String template, int startIndex, int endIndex);

    /**
     * Exports the current evaluation as markdown, if possible.
     */
    void exportMarkdown();

    /**
     * Releases resources held by the workflow.
     */
    void shutdown();
}
