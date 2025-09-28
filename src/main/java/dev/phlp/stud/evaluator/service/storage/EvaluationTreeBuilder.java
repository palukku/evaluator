package dev.phlp.stud.evaluator.service.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import dev.phlp.stud.evaluator.model.EvaluationNode;
import dev.phlp.stud.evaluator.model.config.EvaluationNodeConfig;

public class EvaluationTreeBuilder {
    public List<EvaluationNode> buildTree(List<EvaluationNodeConfig> configs) {
        if (configs == null) {
            return List.of();
        }
        return configs.stream()
                      .map(config -> buildNode(config, null))
                      .collect(Collectors.toCollection(ArrayList::new));
    }

    private EvaluationNode buildNode(EvaluationNodeConfig config, EvaluationNode parent) {
        EvaluationNode node = new EvaluationNode(null, config.getName(), config.getMaxPoints(),
                config.getCommands(), config.getComment(), config.isPseudo());
        if (parent != null) {
            parent.addChild(node);
        }
        if (config.getChildren() != null && !config.getChildren().isEmpty()) {
            config.getChildren().forEach(childConfig -> buildNode(childConfig, node));
        }
        return node;
    }
}
