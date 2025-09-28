package dev.phlp.stud.evaluator.events;

import dev.phlp.stud.evaluator.core.events.AppEvent;

public record RepositoryContextActivated(
        int placeholderValue,
        int currentIndex,
        int totalContexts,
        double achievedPoints,
        double maxPoints) implements AppEvent {
}
