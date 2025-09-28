package dev.phlp.stud.evaluator.service.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import dev.phlp.stud.evaluator.model.EvaluationNode;

public class MarkdownExporter {
    private static final DecimalFormat POINT_FORMAT = new DecimalFormat("0.##");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.##%");

    public Path export(Path repositoryRoot, List<EvaluationNode> roots, String contextLabel, String outputFileName) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("# Evaluation\n\n");
        if (contextLabel != null && !contextLabel.isBlank()) {
            builder.append("**Kontext:** ").append(contextLabel).append("  \n");
        }
        builder.append("**Erstellt am:** ").append(Instant.now()).append("\n\n");

        builder.append("| Kategorie | Erreicht | Max | % |\n");
        builder.append("| --- | ---: | ---: | ---: |\n");
        roots.forEach(root -> builder
                .append("| ")
                .append(root.getName())
                .append(" | ")
                .append(POINT_FORMAT.format(root.getAchievedPoints()))
                .append(" | ")
                .append(POINT_FORMAT.format(root.getMaxPoints()))
                .append(" | ")
                .append(PERCENT_FORMAT.format(root.getCompletionRatio()))
                .append(" |\n"));
        builder.append("\n");

        roots.forEach(root -> {
            builder.append("## ").append(root.getName())
                   .append(" (")
                   .append(POINT_FORMAT.format(root.getAchievedPoints()))
                   .append(" / ")
                   .append(POINT_FORMAT.format(root.getMaxPoints()))
                   .append(")\n\n");
            appendNodeDetails(builder, root, 0);
            builder.append("\n");
        });

        String targetFileName =
                (outputFileName != null && !outputFileName.isBlank()) ?
                outputFileName :
                "evaluation.md";
        if (!targetFileName.toLowerCase(Locale.ROOT).endsWith(".md")) {
            targetFileName = targetFileName + ".md";
        }
        Path markdownFile = repositoryRoot.resolve(targetFileName);
        Files.writeString(markdownFile, builder.toString(), StandardCharsets.UTF_8);
        return markdownFile;
    }

    private void appendNodeDetails(StringBuilder builder, EvaluationNode node, int depth) {
        String indent = "  ".repeat(depth);
        if (node.isLeaf()) {
            builder.append(indent)
                   .append("- ")
                   .append("[")
                   .append(node.getAchievedPoints() >= node.getMaxPoints() ? "x" : " ")
                   .append("] (")
                   .append(POINT_FORMAT.format(node.getAchievedPoints()))
                   .append(" / ")
                   .append(POINT_FORMAT.format(node.getMaxPoints()))
                   .append(" Punkt" + (node.getMaxPoints() != 1D ? "e) " : ") "))
                   .append(node.getName())
                   .append("\n");
            return;
        }
        builder.append(indent)
               .append("- ")
               .append(node.getName())
               .append(" (")
               .append(POINT_FORMAT.format(node.getAchievedPoints()))
               .append(" / ")
               .append(POINT_FORMAT.format(node.getMaxPoints()))
               .append(" Punkte)\n");
        node.getChildren().forEach(child -> appendNodeDetails(builder, child, depth + 1));
    }
}
