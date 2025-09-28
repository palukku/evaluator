package dev.phlp.stud.evaluator.service.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.phlp.stud.evaluator.model.EvaluationNode;
import dev.phlp.stud.evaluator.model.state.NodeSaveState;

public final class EvaluationStateSynchronizer {
    private EvaluationStateSynchronizer() {
    }

    public static Map<String, NodeSaveState> captureNodeStates(List<EvaluationNode> roots, Map<String, String> logReferences) {
        Map<String, NodeSaveState> snapshot = new HashMap<>();
        roots.forEach(root -> captureNodeState(root, snapshot, logReferences));
        return snapshot;
    }

    private static void captureNodeState(EvaluationNode node, Map<String, NodeSaveState> snapshot, Map<String, String> logReferences) {
        boolean isLeaf = node.isLeaf();
        boolean hasComment = node.getComment() != null && !node.getComment().isBlank();
        if (isLeaf || hasComment) {
            NodeSaveState state = new NodeSaveState();
            if (isLeaf) {
                state.setAchievedPoints(node.getAchievedPoints());
                state.setAchievedPointsDefined(node.isAchievedPointsDefined());
                if (logReferences != null) {
                    state.setLastLogFile(logReferences.get(node.getQualifiedName()));
                }
                state.setStatus(node.getStatus());
            }
            state.setComment(node.getComment());
            snapshot.put(node.getQualifiedName(), state);
        }
        node.getChildren().forEach(child -> captureNodeState(child, snapshot, logReferences));
    }

    public static void applyNodeStates(List<EvaluationNode> roots, Map<String, NodeSaveState> savedStates, Map<String, String> logReferences) {
        if (savedStates == null || savedStates.isEmpty()) {
            return;
        }
        roots.forEach(root -> applyNodeState(root, savedStates, logReferences));
        roots.forEach(EvaluationNode::refreshAggregatedPoints);
        roots.forEach(EvaluationNode::refreshAggregatedStatus);
    }

    private static void applyNodeState(EvaluationNode node, Map<String, NodeSaveState> savedStates, Map<String, String> logReferences) {
        NodeSaveState state = savedStates.get(node.getQualifiedName());
        if (state != null && state.getComment() != null) {
            node.setCommentFromStorage(state.getComment());
        }
        if (node.isLeaf() && state != null) {
            Boolean defined = state.getAchievedPointsDefined();
            boolean pointsDefined;
            if (defined != null) {
                pointsDefined = defined;
            } else {
                pointsDefined = Double.compare(state.getAchievedPoints(), 0.0) != 0;
            }
            node.setAchievedPointsFromStorage(state.getAchievedPoints(), pointsDefined);
            if (logReferences != null && state.getLastLogFile() != null) {
                logReferences.put(node.getQualifiedName(), state.getLastLogFile());
            }
            if (state.getStatus() != null) {
                node.setStatusFromStorage(state.getStatus());
            }
        }
        node.getChildren().forEach(child -> applyNodeState(child, savedStates, logReferences));
    }
}
