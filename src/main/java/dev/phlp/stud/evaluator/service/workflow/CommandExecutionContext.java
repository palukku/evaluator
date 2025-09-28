package dev.phlp.stud.evaluator.service.workflow;

import java.nio.file.Path;
import java.util.Objects;

import dev.phlp.stud.evaluator.service.command.CommandLogService;
import dev.phlp.stud.evaluator.service.command.CommandRunner;

/**
 * Provides all dependencies required to launch the command terminal for a
 * specific evaluation node.
 */
public record CommandExecutionContext(
        Path repositoryPath,
        Path evaluationDirectory,
        CommandRunner commandRunner,
        CommandLogService commandLogService,
        double maxPoints,
        double achievedPoints) {

    public CommandExecutionContext {
        Objects.requireNonNull(repositoryPath, "repositoryPath must not be null");
        Objects.requireNonNull(evaluationDirectory, "evaluationDirectory must not be null");
        Objects.requireNonNull(commandRunner, "commandRunner must not be null");
        Objects.requireNonNull(commandLogService, "commandLogService must not be null");
    }
}
