package dev.phlp.stud.evaluator.events;

import dev.phlp.stud.evaluator.core.events.AppEvent;

public record CloneProgressUpdated(
        int completed,
        int total) implements AppEvent {
}
