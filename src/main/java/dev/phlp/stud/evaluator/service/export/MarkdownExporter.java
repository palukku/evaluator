package dev.phlp.stud.evaluator.service.export;

import dev.phlp.stud.evaluator.model.EvaluationNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public class MarkdownExporter {
    private static final DecimalFormat POINT_FORMAT = new DecimalFormat("0.##");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.##%");

    public Path export(Path repositoryRoot, List<EvaluationNode> roots, String contextLabel,
                       String overallComment, String outputFileName) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("# Evaluation\n\n");
        if (appendBlockQuote(builder, "", overallComment)) {
            builder.append("\n");
        }
        if (contextLabel != null && !contextLabel.isBlank()) {
            builder.append("**Kontext:** ").append(contextLabel).append("  \\n");
        }
        builder.append("**Erstellt am:** ").append(Instant.now()).append("\n\n");

        List<EvaluationNode> visibleRoots = roots.stream()
                .filter(node -> !node.isPseudo())
                .toList();
        double totalAchieved = visibleRoots.stream().mapToDouble(EvaluationNode::getAchievedPoints).sum();
        double totalMax = visibleRoots.stream().mapToDouble(EvaluationNode::getMaxPoints).sum();
        double totalRatio = Double.compare(totalMax, 0.0) == 0 ? 0.0 : totalAchieved / totalMax;

        builder.append("| Kategorie | Erreicht | Max | % |\n");
        builder.append("| --- | ---: | ---: | ---: |\n");
        visibleRoots.forEach(root -> builder
                .append("| ")
                .append(root.getName())
                .append(" | ")
                .append(POINT_FORMAT.format(root.getAchievedPoints()))
                .append(" | ")
                .append(POINT_FORMAT.format(root.getMaxPoints()))
                .append(" | ")
                .append(PERCENT_FORMAT.format(root.getCompletionRatio()))
                .append(" |\n"));
        builder.append("| **Gesamt** | ")
                .append(POINT_FORMAT.format(totalAchieved))
                .append(" | ")
                .append(POINT_FORMAT.format(totalMax))
                .append(" | ")
                .append(PERCENT_FORMAT.format(totalRatio))
                .append(" |\n\n");

        visibleRoots.forEach(root -> {
            builder.append("## ").append(root.getName())
                    .append(" (")
                    .append(POINT_FORMAT.format(root.getAchievedPoints()))
                    .append(" / ")
                    .append(POINT_FORMAT.format(root.getMaxPoints()))
                    .append(")\n\n");
            appendNodeDetails(builder, root, 0);
            builder.append("\n");
        });

        String targetFileName = (outputFileName != null && !outputFileName.isBlank()) ? outputFileName : "evaluation.md";
        if (!targetFileName.toLowerCase(Locale.ROOT).endsWith(".md")) {
            targetFileName = targetFileName + ".md";
        }
        Path markdownFile = repositoryRoot.resolve(targetFileName);
        Files.writeString(markdownFile, builder.toString(), StandardCharsets.UTF_8);
        return markdownFile;
    }

    private void appendNodeDetails(StringBuilder builder, EvaluationNode node, int depth) {
        if (node.isPseudo()) {
            return;
        }
        String indent = "  ".repeat(depth);
        builder.append(indent)
                .append("- ")
                .append(node.getName())
                .append(": ")
                .append(POINT_FORMAT.format(node.getAchievedPoints()))
                .append(" / ")
                .append(POINT_FORMAT.format(node.getMaxPoints()))
                .append("\n");
        appendNodeComments(builder, indent + "  ", node);
        if (node.isLeaf()) {
            return;
        }
        node.getChildren().forEach(child -> appendNodeDetails(builder, child, depth + 1));
    }

    private void appendNodeComments(StringBuilder builder, String prefix, EvaluationNode node) {
        boolean wroteConfig = appendBlockQuote(builder, prefix, node.getConfigurationComment());
        boolean wroteEvaluation = appendBlockQuote(builder, prefix, node.getComment());
        if ((wroteConfig || wroteEvaluation) && !node.isLeaf()) {
            builder.append("\n");
        }
    }

    private boolean appendBlockQuote(StringBuilder builder, String prefix, String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return false;
        }
        String normalized = trimmed.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        for (String line : lines) {
            if (line.isBlank()) {
                builder.append(prefix).append("> ").append("\n");
            } else {
                builder.append(prefix).append("> ").append(line.stripTrailing()).append("\n");
            }
        }
        return true;
    }
}
