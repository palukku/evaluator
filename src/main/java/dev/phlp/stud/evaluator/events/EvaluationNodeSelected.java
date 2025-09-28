package dev.phlp.stud.evaluator.events;

import dev.phlp.stud.evaluator.core.events.AppEvent;
import dev.phlp.stud.evaluator.model.EvaluationNode;

public record EvaluationNodeSelected(
        EvaluationNode node) implements AppEvent {
}
