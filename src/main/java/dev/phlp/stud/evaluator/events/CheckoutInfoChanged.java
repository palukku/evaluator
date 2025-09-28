package dev.phlp.stud.evaluator.events;

import dev.phlp.stud.evaluator.core.events.AppEvent;

public record CheckoutInfoChanged(
        String text) implements AppEvent {
}
