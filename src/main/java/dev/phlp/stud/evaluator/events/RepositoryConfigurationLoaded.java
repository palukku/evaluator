package dev.phlp.stud.evaluator.events;

import java.time.LocalDate;

import dev.phlp.stud.evaluator.core.events.AppEvent;

public record RepositoryConfigurationLoaded(
        String repositoryTemplate,
        String tag,
        LocalDate deadline) implements AppEvent {
}
