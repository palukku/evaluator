package dev.phlp.stud.evaluator.events;

import dev.phlp.stud.evaluator.core.events.AppEvent;

public record RepositoryActionsAvailabilityChanged(
        boolean cloneEnabled,
        boolean exportEnabled) implements AppEvent {
}
