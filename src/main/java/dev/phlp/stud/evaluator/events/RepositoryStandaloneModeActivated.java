package dev.phlp.stud.evaluator.events;

import dev.phlp.stud.evaluator.core.events.AppEvent;

public record RepositoryStandaloneModeActivated(
        int placeholderValue) implements AppEvent {
}
