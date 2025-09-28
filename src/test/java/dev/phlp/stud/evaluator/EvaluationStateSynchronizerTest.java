package dev.phlp.stud.evaluator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.phlp.stud.evaluator.model.EvaluationNode;
import dev.phlp.stud.evaluator.model.state.NodeSaveState;
import dev.phlp.stud.evaluator.service.storage.EvaluationStateSynchronizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationStateSynchronizerTest {
    @Test
    void captureAndRestoreNodeState() {
        EvaluationNode taskA = new EvaluationNode(null, "Task A", 5.0, List.of());
        EvaluationNode taskB = new EvaluationNode(null, "Task B", 5.0, List.of());
        EvaluationNode category = new EvaluationNode(null, "Category", 0.0, List.of());
        category.addChild(taskA);
        category.addChild(taskB);

        taskA.setAchievedPoints(3.0);
        taskB.setAchievedPoints(4.0);
        category.refreshAggregatedPoints();

        Map<String, NodeSaveState> snapshot = EvaluationStateSynchronizer.captureNodeStates(List.of(category), new HashMap<>());
        assertEquals(2, snapshot.size());
        assertTrue(snapshot.containsKey("Category/Task A"));

        taskA.resetToZero();
        taskB.resetToZero();
        category.refreshAggregatedPoints();

        Map<String, String> logRefs = new HashMap<>();
        EvaluationStateSynchronizer.applyNodeStates(List.of(category), snapshot, logRefs);

        assertEquals(3.0, taskA.getAchievedPoints(), 1e-6);
        assertEquals(4.0, taskB.getAchievedPoints(), 1e-6);
        assertEquals(7.0, category.getAchievedPoints(), 1e-6);
    }
}
