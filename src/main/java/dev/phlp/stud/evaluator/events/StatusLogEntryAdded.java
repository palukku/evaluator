package dev.phlp.stud.evaluator.events;

import dev.phlp.stud.evaluator.core.events.AppEvent;

public record StatusLogEntryAdded(
        String message,
        String stackTrace,
        boolean error) implements AppEvent {
}
