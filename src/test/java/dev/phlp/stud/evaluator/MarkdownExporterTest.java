package dev.phlp.stud.evaluator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.phlp.stud.evaluator.model.EvaluationNode;
import dev.phlp.stud.evaluator.service.export.MarkdownExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownExporterTest {
    @TempDir
    Path tempDir;

    @Test
    void createsMarkdownSummary() throws IOException {
        EvaluationNode task = new EvaluationNode(null, "Task 1", 10.0, List.of());
        EvaluationNode category = new EvaluationNode(null, "Project", 0.0, List.of());
        category.addChild(task);
        task.setAchievedPoints(7.5);
        category.refreshAggregatedPoints();

        MarkdownExporter exporter = new MarkdownExporter();
        Path file = exporter.export(tempDir, List.of(category), "Index 5", null);
        String content = Files.readString(file);

        assertTrue(content.contains("**Kontext:** Index 5"));
        assertTrue(content.contains("| Project | 7.5"));
        assertTrue(content.contains("- Task 1: 7.5 / 10"));
    }

    @Test
    void usesCustomOutputFileName() throws IOException {
        EvaluationNode task = new EvaluationNode(null, "Task", 5.0, List.of());
        EvaluationNode root = new EvaluationNode(null, "Root", 0.0, List.of());
        root.addChild(task);
        task.setAchievedPoints(3.0);
        root.refreshAggregatedPoints();

        MarkdownExporter exporter = new MarkdownExporter();
        Path file = exporter.export(tempDir, List.of(root), null, "feedback-custom");

        assertEquals("feedback-custom.md", file.getFileName().toString());
    }
}

