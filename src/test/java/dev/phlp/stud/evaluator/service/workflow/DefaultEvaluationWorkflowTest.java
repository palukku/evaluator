package dev.phlp.stud.evaluator.service.workflow;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.phlp.stud.evaluator.core.di.DefaultServiceRegistry;
import dev.phlp.stud.evaluator.core.di.ServiceRegistry;
import dev.phlp.stud.evaluator.core.events.EventBus;
import dev.phlp.stud.evaluator.core.events.SimpleEventBus;
import dev.phlp.stud.evaluator.events.RepositoryConfigurationLoaded;
import dev.phlp.stud.evaluator.events.RepositoryStandaloneModeActivated;
import dev.phlp.stud.evaluator.events.TotalsUpdated;
import dev.phlp.stud.evaluator.model.config.EvaluationConfig;
import dev.phlp.stud.evaluator.model.config.EvaluationNodeConfig;
import dev.phlp.stud.evaluator.service.command.CommandLogService;
import dev.phlp.stud.evaluator.service.command.CommandRunner;
import dev.phlp.stud.evaluator.service.dialog.DialogService;
import dev.phlp.stud.evaluator.service.export.MarkdownExporter;
import dev.phlp.stud.evaluator.service.git.GitService;
import dev.phlp.stud.evaluator.service.storage.AutoSaveService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultEvaluationWorkflowTest {

    private ServiceRegistry registry;
    private TestDialogService dialogService;
    private EventBus eventBus;
    private DefaultEvaluationWorkflow workflow;

    @BeforeEach
    void setUp() {
        registry = new DefaultServiceRegistry();
        registry.add(AutoSaveService.class, new AutoSaveService());
        registry.add(CommandRunner.class, new CommandRunner());
        registry.add(CommandLogService.class, new CommandLogService());
        registry.add(MarkdownExporter.class, new MarkdownExporter());
        registry.add(GitService.class, new GitService());
        dialogService = new TestDialogService();
        registry.add(DialogService.class, dialogService);
        eventBus = new SimpleEventBus();
        workflow = new DefaultEvaluationWorkflow(registry, eventBus);
    }

    @AfterEach
    void tearDown() {
        workflow.shutdown();
    }

    @Test
    void initializePublishesConfigurationAndTotals(@TempDir Path tempDir) {
        EvaluationConfig config = sampleConfig();

        AtomicReference<RepositoryConfigurationLoaded> configEvent = new AtomicReference<>();
        AtomicReference<TotalsUpdated> totalsEvent = new AtomicReference<>();
        AtomicReference<RepositoryStandaloneModeActivated> placeholderEvent = new AtomicReference<>();
        eventBus.subscribe(RepositoryConfigurationLoaded.class, configEvent::set);
        eventBus.subscribe(TotalsUpdated.class, totalsEvent::set);
        eventBus.subscribe(RepositoryStandaloneModeActivated.class, placeholderEvent::set);

        workflow.initialize(null, config, tempDir);

        assertNotNull(configEvent.get());
        assertEquals("https://example/{{number}}", configEvent.get().repositoryTemplate());
        assertEquals("v1.0", configEvent.get().tag());
        assertEquals(LocalDate.of(2024, 1, 1), configEvent.get().deadline());

        assertNotNull(totalsEvent.get());
        assertEquals(0.0, totalsEvent.get().achievedPoints());
        assertEquals(5.0, totalsEvent.get().maxPoints());

        assertNotNull(placeholderEvent.get());
        assertEquals(1, placeholderEvent.get().placeholderValue());
    }

    @Test
    void prepareRepositoriesWithMissingTemplateShowsError(@TempDir Path tempDir) {
        EvaluationConfig config = sampleConfig();
        workflow.initialize(null, config, tempDir);

        workflow.prepareRepositories("   ", 1, 3);

        assertEquals("Repository-Template fehlt", dialogService.lastErrorTitle);
        assertNotNull(dialogService.lastErrorMessage);
        assertTrue(dialogService.lastErrorMessage.contains("Bitte eine Repository-URL angeben"));
    }

    @Test
    void standalonePlaceholderUpdatesArePropagated(@TempDir Path tempDir) {
        EvaluationConfig config = sampleConfig();
        AtomicReference<RepositoryStandaloneModeActivated> placeholderEvent = new AtomicReference<>();
        eventBus.subscribe(RepositoryStandaloneModeActivated.class, placeholderEvent::set);

        workflow.initialize(null, config, tempDir);
        workflow.updateStandalonePlaceholder(7);

        assertNotNull(placeholderEvent.get());
        assertEquals(7, placeholderEvent.get().placeholderValue());
    }

    private EvaluationConfig sampleConfig() {
        EvaluationNodeConfig node = new EvaluationNodeConfig();
        node.setName("Task");
        node.setMaxPoints(5.0);

        EvaluationConfig config = new EvaluationConfig();
        config.setTitle("Sample Evaluation");
        config.setRepositoryUrlTemplate("https://example/{{number}}");
        config.setTag("v1.0");
        config.setDeadline(LocalDate.of(2024, 1, 1));
        config.setRootCategories(List.of(node));
        return config;
    }

    private static final class TestDialogService implements DialogService {
        private String lastErrorTitle;
        private String lastErrorMessage;
        private String lastInfoTitle;
        private String lastInfoMessage;

        @Override
        public void showError(String title, String message) {
            this.lastErrorTitle = title;
            this.lastErrorMessage = message;
        }

        @Override
        public void showInfo(String title, String message) {
            this.lastInfoTitle = title;
            this.lastInfoMessage = message;
        }
    }
}
