package dev.phlp.stud.evaluator;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import dev.phlp.stud.evaluator.model.config.EvaluationConfig;
import dev.phlp.stud.evaluator.model.config.EvaluationNodeConfig;
import dev.phlp.stud.evaluator.service.storage.ConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ConfigServiceTest {
    @Test
    void cloneProducesDeepCopy() throws IOException {
        ConfigService service = new ConfigService();

        EvaluationConfig original = new EvaluationConfig();
        original.setTitle("Original");
        original.setRepositoryUrlTemplate("https://example.com/{{number}}");
        original.setTag("v1.0.0");
        original.setDeadline(LocalDate.of(2024, 9, 15));

        EvaluationNodeConfig category = new EvaluationNodeConfig();
        category.setName("Category");
        category.setMaxPoints(5.0);
        EvaluationNodeConfig task = new EvaluationNodeConfig();
        task.setName("Task");
        task.setMaxPoints(5.0);
        task.setCommands(List.of("mvn test"));
        category.getChildren().add(task);
        original.getRootCategories().add(category);

        EvaluationConfig copy = service.clone(original);

        assertNotSame(original, copy);
        assertEquals(original.getTitle(), copy.getTitle());
        assertEquals(1, copy.getRootCategories().size());
        assertEquals("v1.0.0", copy.getTag());
        assertEquals(LocalDate.of(2024, 9, 15), copy.getDeadline());

        copy.setTitle("Copy");
        copy.setTag("changed");
        copy.setDeadline(LocalDate.of(2025, 1, 1));
        copy.getRootCategories().get(0).setName("Changed Category");

        assertEquals("Original", original.getTitle());
        assertEquals("v1.0.0", original.getTag());
        assertEquals(LocalDate.of(2024, 9, 15), original.getDeadline());
        assertEquals("Category", original.getRootCategories().get(0).getName());
    }
}
