package dev.phlp.stud.evaluator;

import dev.phlp.stud.evaluator.model.EvaluationNode;
import dev.phlp.stud.evaluator.service.export.MarkdownExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownExporterTest {
    @TempDir
    Path tempDir;

    @Test
    void createsMarkdownSummary() throws IOException {
        EvaluationNode task = new EvaluationNode(null, "Task 1", 10.0, List.of(), "Konfig Kommentar", false);
        EvaluationNode category = new EvaluationNode(null, "Project", 0.0, List.of(), "Projektbeschreibung", false);
        category.addChild(task);
        task.setAchievedPoints(7.5);
        task.setComment("Bewertung: solide Umsetzung.");
        category.refreshAggregatedPoints();

        MarkdownExporter exporter = new MarkdownExporter();
        Path file = exporter.export(tempDir, List.of(category), "Index 5", null, null);
        String content = Files.readString(file);

        assertTrue(content.contains("**Kontext:** Index 5"));
        assertTrue(content.contains("| Project | 7.5 | 10 | 75% |"));
        assertTrue(content.contains("| **Gesamt** | 7.5 | 10 | 75% |"));
        assertTrue(content.contains("- Task 1: 7.5 / 10"));
        assertTrue(content.contains("> Projektbeschreibung"));
        assertTrue(content.contains("> Bewertung: solide Umsetzung."));
    }

    @Test
    void usesCustomOutputFileName() throws IOException {
        EvaluationNode task = new EvaluationNode(null, "Task", 5.0, List.of(), "", false);
        EvaluationNode root = new EvaluationNode(null, "Root", 0.0, List.of(), "", false);
        root.addChild(task);
        task.setAchievedPoints(3.0);
        root.refreshAggregatedPoints();

        MarkdownExporter exporter = new MarkdownExporter();
        Path file = exporter.export(tempDir, List.of(root), null, null, "feedback-custom");

        assertTrue(file.getFileName().toString().equals("feedback-custom.md"));
    }

    @Test
    void omitsPseudoNodesFromExport() throws IOException {
        EvaluationNode visibleTask = new EvaluationNode(null, "Bewerteter Task", 4.0, List.of(), "", false);
        visibleTask.setAchievedPoints(2.0);
        EvaluationNode scoringCategory = new EvaluationNode(null, "Bewertung", 0.0, List.of(), "", false);
        scoringCategory.addChild(visibleTask);

        EvaluationNode pseudoTask = new EvaluationNode(null, "Hilfstask", 0.0, List.of("echo hi"), "", true);
        EvaluationNode pseudoCategory = new EvaluationNode(null, "Pseudo", 0.0, List.of(), "", true);
        pseudoCategory.addChild(pseudoTask);

        MarkdownExporter exporter = new MarkdownExporter();
        Path file = exporter.export(tempDir, List.of(scoringCategory, pseudoCategory), null, null, null);
        String content = Files.readString(file);

        assertTrue(content.contains("| Bewertung | 2 | 4 | 50% |"));
        assertTrue(content.contains("| **Gesamt** | 2 | 4 | 50% |"));
        assertTrue(content.contains("- Bewerteter Task: 2 / 4"));
        assertFalse(content.contains("Pseudo"));
        assertFalse(content.contains("Hilfstask"));
    }
}

