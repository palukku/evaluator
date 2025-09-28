package dev.phlp.stud.evaluator.service.workflow;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javafx.application.Platform;
import javafx.stage.Stage;

import dev.phlp.stud.evaluator.controller.CommandTerminalController;
import dev.phlp.stud.evaluator.core.di.ServiceRegistry;
import dev.phlp.stud.evaluator.core.events.EventBus;
import dev.phlp.stud.evaluator.events.CheckoutInfoChanged;
import dev.phlp.stud.evaluator.events.CloneProgressUpdated;
import dev.phlp.stud.evaluator.events.CloneProgressVisibilityChanged;
import dev.phlp.stud.evaluator.events.EvaluationTreeAvailabilityChanged;
import dev.phlp.stud.evaluator.events.EvaluationTreeRefreshRequested;
import dev.phlp.stud.evaluator.events.EvaluationTreeSelectionCleared;
import dev.phlp.stud.evaluator.events.RepositoryActionsAvailabilityChanged;
import dev.phlp.stud.evaluator.events.RepositoryConfigurationLoaded;
import dev.phlp.stud.evaluator.events.RepositoryContextActivated;
import dev.phlp.stud.evaluator.events.RepositoryStandaloneModeActivated;
import dev.phlp.stud.evaluator.events.StatusLogEntryAdded;
import dev.phlp.stud.evaluator.events.StatusMessageUpdated;
import dev.phlp.stud.evaluator.events.TotalsUpdated;
import dev.phlp.stud.evaluator.model.EvaluationNode;
import dev.phlp.stud.evaluator.model.EvaluationStatus;
import dev.phlp.stud.evaluator.model.config.EvaluationConfig;
import dev.phlp.stud.evaluator.model.state.EvaluationSaveData;
import dev.phlp.stud.evaluator.model.state.NodeSaveState;
import dev.phlp.stud.evaluator.service.command.CommandLogService;
import dev.phlp.stud.evaluator.service.command.CommandRunner;
import dev.phlp.stud.evaluator.service.dialog.DialogService;
import dev.phlp.stud.evaluator.service.export.MarkdownExporter;
import dev.phlp.stud.evaluator.service.git.GitService;
import dev.phlp.stud.evaluator.service.repository.CheckoutInfo;
import dev.phlp.stud.evaluator.service.repository.CheckoutMode;
import dev.phlp.stud.evaluator.service.repository.CheckoutStrategy;
import dev.phlp.stud.evaluator.service.repository.PlaceholderRange;
import dev.phlp.stud.evaluator.service.repository.RepositoryContext;
import dev.phlp.stud.evaluator.service.repository.RepositoryPreparationListener;
import dev.phlp.stud.evaluator.service.repository.RepositoryPreparationRequest;
import dev.phlp.stud.evaluator.service.repository.RepositoryPreparationResult;
import dev.phlp.stud.evaluator.service.repository.RepositoryPreparationService;
import dev.phlp.stud.evaluator.service.storage.AutoSaveService;
import dev.phlp.stud.evaluator.service.storage.EvaluationStateSynchronizer;
import dev.phlp.stud.evaluator.service.storage.EvaluationTreeBuilder;

/**
 * Default implementation that preserves the existing evaluation logic while
 * exposing it as a reusable service.
 */
public final class DefaultEvaluationWorkflow implements EvaluationWorkflow {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final EventBus events;
    private final AutoSaveService autoSaveService;
    private final CommandRunner commandRunner;
    private final CommandLogService commandLogService;
    private final MarkdownExporter markdownExporter;
    private final DialogService dialogService;
    private final RepositoryPreparationService repositoryPreparationService;
    private final List<EvaluationNode> rootNodes = new ArrayList<>();
    private final Map<String, String> logReferences = new HashMap<>();
    private final List<RepositoryContext> repositoryContexts = new ArrayList<>();
    private EvaluationConfig evaluationConfig;
    private Path baseDirectory;
    private int currentPlaceholderValue = 1;
    private int currentContextIndex = -1;

    private Path repositoriesRoot;
    private Path evaluationsRoot;

    private Path currentRepositoryPath;
    private String currentRepositoryUrl;
    private String currentCheckedOutRef;
    private CheckoutStrategy currentCheckoutStrategy = CheckoutStrategy.none();
    private boolean suppressAutoSave;

    public DefaultEvaluationWorkflow(ServiceRegistry services, EventBus events) {
        Objects.requireNonNull(services, "ServiceRegistry must not be null");
        this.events = Objects.requireNonNull(events, "EventBus must not be null");
        this.autoSaveService = Objects.requireNonNull(services.get(AutoSaveService.class), "AutoSaveService not registered");
        this.commandRunner = Objects.requireNonNull(services.get(CommandRunner.class), "CommandRunner not registered");
        this.commandLogService = Objects.requireNonNull(services.get(CommandLogService.class), "CommandLogService not registered");
        this.markdownExporter = Objects.requireNonNull(services.get(MarkdownExporter.class), "MarkdownExporter not registered");
        this.dialogService = Objects.requireNonNull(services.get(DialogService.class), "DialogService not registered");
        GitService gitService = Objects.requireNonNull(services.get(GitService.class), "GitService not registered");
        this.repositoryPreparationService = new RepositoryPreparationService(gitService, autoSaveService);
    }

    @Override
    public void initialize(Stage stage, EvaluationConfig config, Path baseDirectory) {
        this.evaluationConfig = config;
        this.baseDirectory = Optional.ofNullable(baseDirectory)
                                     .map(Path::toAbsolutePath)
                                     .orElse(Path.of(System.getProperty("user.dir")).toAbsolutePath());

        LocalDate configuredDeadline = config.getDeadline();
        events.publish(new RepositoryConfigurationLoaded(
                Optional.ofNullable(config.getRepositoryUrlTemplate()).orElse(""),
                config.getTag(),
                configuredDeadline));

        EvaluationTreeBuilder builder = new EvaluationTreeBuilder();
        rootNodes.clear();
        rootNodes.addAll(builder.buildTree(config.getRootCategories()));
        rootNodes.forEach(this::registerNodeListeners);

        updateTotals();
        updatePlaceholderDisplay();
        events.publish(new EvaluationTreeRefreshRequested());
        events.publish(new EvaluationTreeAvailabilityChanged(false));
    }

    @Override
    public List<EvaluationNode> getRootNodes() {
        return List.copyOf(rootNodes);
    }

    @Override
    public boolean isRepositoryReady() {
        return currentRepositoryPath != null;
    }

    @Override
    public Optional<CommandExecutionContext> createCommandExecutionContext(EvaluationNode node) {
        if (currentRepositoryPath == null) {
            dialogService.showError("Kein Repository", "Bitte zuerst die Repositories vorbereiten.");
            return Optional.empty();
        }
        List<String> commands = node.getCommands();
        if (commands.isEmpty()) {
            dialogService.showInfo("Keine Kommandos", "Fuer diesen Eintrag sind keine Befehle definiert.");
            return Optional.empty();
        }
        if (currentContextIndex < 0 || currentContextIndex >= repositoryContexts.size()) {
            dialogService.showError("Keine Evaluation", "Es wurde kein Repository-Kontext geladen.");
            return Optional.empty();
        }
        RepositoryContext context = repositoryContexts.get(currentContextIndex);
        return Optional.of(new CommandExecutionContext(
                currentRepositoryPath,
                context.evaluationDirectory(),
                commandRunner,
                commandLogService,
                node.getMaxPoints(),
                node.getAchievedPoints()));
    }

    @Override
    public void onCommandExecutionStarted(EvaluationNode node) {
        node.setStatus(EvaluationStatus.RUNNING);
        events.publish(new EvaluationTreeRefreshRequested());
    }

    @Override
    public void onCommandExecutionFinished(EvaluationNode node, CommandTerminalController.CommandOutcome outcome) {
        switch (outcome) {
            case SUCCESS ->
                    node.setStatus(EvaluationStatus.SUCCESS);
            case FAILED ->
                    node.setStatus(EvaluationStatus.FAILED);
            case CANCELLED ->
                    node.setStatus(EvaluationStatus.CANCELLED);
        }
        events.publish(new EvaluationTreeRefreshRequested());
        triggerAutoSave();
    }

    @Override
    public void recordLogReference(EvaluationNode node, Path relativePath) {
        if (relativePath == null) {
            return;
        }
        String stored = relativePath.toString().replace("\\", "/");
        logReferences.put(node.getQualifiedName(), stored);
        triggerAutoSave();
    }

    @Override
    public void adjustPlaceholder(int delta) {
        if (repositoryContexts.isEmpty()) {
            return;
        }
        int newIndex = Math.max(0, Math.min(repositoryContexts.size() - 1, currentContextIndex + delta));
        if (newIndex != currentContextIndex) {
            selectContext(newIndex);
        }
    }

    @Override
    public void updateStandalonePlaceholder(int value) {
        if (!repositoryContexts.isEmpty()) {
            return;
        }
        currentPlaceholderValue = value;
        updatePlaceholderDisplay();
    }

    @Override
    public void prepareRepositories(String template, int startIndex, int endIndex) {
        String sanitizedTemplate = Optional.ofNullable(template).map(String::trim).orElse("");
        if (sanitizedTemplate.isBlank()) {
            dialogService.showError("Repository-Template fehlt", "Bitte eine Repository-URL angeben.");
            return;
        }

        PlaceholderRange placeholderRange;
        try {
            placeholderRange = new PlaceholderRange(startIndex, endIndex);
        } catch (IllegalArgumentException ex) {
            dialogService.showError("Platzhalter-Bereich", ex.getMessage());
            return;
        }
        currentPlaceholderValue = placeholderRange.start();

        repositoriesRoot = baseDirectory.resolve("repos");
        evaluationsRoot = resolveEvaluationsRootDirectory();
        try {
            Files.createDirectories(repositoriesRoot);
            Files.createDirectories(evaluationsRoot);
        } catch (IOException ex) {
            dialogService.showError("Verzeichnisse", "Konnte Arbeitsverzeichnisse nicht anlegen: " + ex.getMessage());
            return;
        }

        saveCurrentContext();
        repositoryContexts.clear();
        currentContextIndex = -1;
        currentRepositoryPath = null;
        currentRepositoryUrl = null;
        currentCheckedOutRef = null;
        currentCheckoutStrategy = CheckoutStrategy.none();
        updateCheckoutInfoLabel();
        logReferences.clear();
        runWithoutAutoSave(() -> rootNodes.forEach(this::resetNodeState));
        events.publish(new EvaluationTreeRefreshRequested());
        updateTotals();
        events.publish(new EvaluationTreeAvailabilityChanged(false));
        events.publish(new RepositoryActionsAvailabilityChanged(false, false));
        events.publish(new EvaluationTreeSelectionCleared());

        updateStatus("Bereite Repositories vor...");
        List<Integer> values = placeholderRange.values();
        events.publish(new CloneProgressVisibilityChanged(true));
        events.publish(new CloneProgressUpdated(0, values.size()));

        RepositoryPreparationRequest request = RepositoryPreparationRequest.builder()
                                                                           .repositoryTemplate(sanitizedTemplate)
                                                                           .placeholderRange(placeholderRange)
                                                                           .repositoriesRoot(repositoriesRoot)
                                                                           .evaluationsRoot(evaluationsRoot)
                                                                           .evaluationFileName(buildEvaluationFileName())
                                                                           .evaluationTitle(Optional.ofNullable(evaluationConfig.getTitle()).orElse(""))
                                                                           .repositoryNumberPlaceholder(evaluationConfig.getRepositoryNumberPlaceholder())
                                                                           .tag(evaluationConfig.getTag())
                                                                           .deadline(evaluationConfig.getDeadline())
                                                                           .build();

        RepositoryPreparationListener listener = (completed, total) ->
                Platform.runLater(() -> events.publish(new CloneProgressUpdated(completed, total)));

        new Thread(() -> {
            RepositoryPreparationResult result = repositoryPreparationService.prepareRepositories(request, listener);
            Platform.runLater(() -> handlePreparationResult(result));
        }, "repository-preparation").start();
    }

    private void handlePreparationResult(RepositoryPreparationResult result) {
        events.publish(new RepositoryActionsAvailabilityChanged(true, false));
        events.publish(new CloneProgressVisibilityChanged(false));
        String errors = result.errors();
        if (!errors.isBlank()) {
            dialogService.showError("Vorbereitung abgeschlossen", "Einige Repositories konnten nicht vorbereitet werden:\n" + errors);
            publishLogEntry("Fehler bei der Vorbereitung:\n" + errors, null, true);
        }
        List<RepositoryContext> contexts = result.contexts();
        if (contexts.isEmpty()) {
            updateStatus("Keine Repositories verfuegbar");
            repositoryContexts.clear();
            currentCheckedOutRef = null;
            currentCheckoutStrategy = CheckoutStrategy.none();
            updateCheckoutInfoLabel();
            updatePlaceholderDisplay();
            events.publish(new EvaluationTreeAvailabilityChanged(false));
            return;
        }
        repositoryContexts.clear();
        repositoryContexts.addAll(contexts);
        events.publish(new EvaluationTreeAvailabilityChanged(true));
        events.publish(new RepositoryActionsAvailabilityChanged(true, true));
        selectContext(0);
        updateStatus("Repositories vorbereitet: " + contexts.size());
    }

    private void selectContext(int index) {
        if (index < 0 || index >= repositoryContexts.size()) {
            return;
        }
        saveCurrentContext();
        currentContextIndex = index;
        RepositoryContext context = repositoryContexts.get(index);
        currentPlaceholderValue = context.placeholderValue();
        currentRepositoryPath = context.repositoryPath();
        currentRepositoryUrl = context.repositoryUrl();
        CheckoutInfo checkoutInfo = context.checkoutInfo();
        currentCheckoutStrategy = checkoutInfo.strategy();
        currentCheckedOutRef = checkoutInfo.reference().orElse(null);
        updateCheckoutInfoLabel();
        loadEvaluationForContext(context);
    }

    private void loadEvaluationForContext(RepositoryContext context) {
        logReferences.clear();
        runWithoutAutoSave(() -> {
            rootNodes.forEach(this::resetNodeState);
            Optional<EvaluationSaveData> maybeData = autoSaveService.load(context.evaluationFile());
            if (maybeData.isPresent()) {
                EvaluationSaveData data = maybeData.get();
                Map<String, NodeSaveState> nodes = data.getNodes();
                normalizeLegacyLogReferences(nodes);
                EvaluationStateSynchronizer.applyNodeStates(rootNodes, nodes, logReferences);
                if (currentCheckoutStrategy.mode().isEmpty()) {
                    CheckoutStrategy.decode(data.getCheckoutStrategy())
                                    .ifPresent(strategy -> currentCheckoutStrategy = strategy);
                }
                currentCheckedOutRef = Optional.ofNullable(data.getCheckedOutReference())
                                               .filter(ref -> !ref.isBlank())
                                               .orElse(currentCheckedOutRef);
                if (data.getPlaceholderValue() != null) {
                    currentPlaceholderValue = data.getPlaceholderValue();
                }
            }
        });
        events.publish(new EvaluationTreeRefreshRequested());
        events.publish(new EvaluationTreeSelectionCleared());
        updateTotals();
        updateCheckoutInfoLabel();
        triggerAutoSave();
    }

    @Override
    public void exportMarkdown() {
        if (currentContextIndex < 0 || currentContextIndex >= repositoryContexts.size()) {
            dialogService.showError("Keine Evaluation", "Bitte zuerst Repositories vorbereiten.");
            return;
        }
        if (currentRepositoryPath == null) {
            dialogService.showError("Kein Repository", "Das aktuelle Repository wurde noch nicht vorbereitet.");
            return;
        }
        RepositoryContext context = repositoryContexts.get(currentContextIndex);
        try {
            String sheetComment = evaluationConfig != null ? evaluationConfig.getComment() : null;
            Path file = markdownExporter.export(currentRepositoryPath, rootNodes,
                    "Repository " + formatPlaceholder(context.placeholderValue()), sheetComment, buildFeedbackFileName());
            updateStatus("Markdown exportiert: " + file.getFileName());
        } catch (IOException ex) {
            dialogService.showError("Export fehlgeschlagen", ex.getMessage());
            publishLogEntry("Export fehlgeschlagen: " + ex.getMessage(), ex, true);
        }
    }

    @Override
    public void shutdown() {
        saveCurrentContext();
        commandRunner.close();
        autoSaveService.close();
    }

    private void updateTotals() {
        double achieved = rootNodes.stream().mapToDouble(EvaluationNode::getAchievedPoints).sum();
        double max = rootNodes.stream().mapToDouble(EvaluationNode::getMaxPoints).sum();
        events.publish(new TotalsUpdated(achieved, max));
        updatePlaceholderDisplay();
    }

    private void updatePlaceholderDisplay() {
        if (repositoryContexts.isEmpty() || currentContextIndex < 0) {
            events.publish(new RepositoryStandaloneModeActivated(currentPlaceholderValue));
            return;
        }
        RepositoryContext context = repositoryContexts.get(currentContextIndex);
        double achieved = rootNodes.stream().mapToDouble(EvaluationNode::getAchievedPoints).sum();
        double max = rootNodes.stream().mapToDouble(EvaluationNode::getMaxPoints).sum();
        events.publish(new RepositoryContextActivated(
                context.placeholderValue(),
                currentContextIndex,
                repositoryContexts.size(),
                achieved,
                max));
    }

    private void updateCheckoutInfoLabel() {
        StringBuilder text = new StringBuilder("Checkout: ");
        Optional<CheckoutMode> checkoutMode = currentCheckoutStrategy.mode();
        if (checkoutMode.isEmpty()) {
            text.append('-');
        } else {
            String detail = currentCheckoutStrategy.detail().orElse(null);
            switch (checkoutMode.get()) {
                case TAG ->
                        text.append("Tag ").append(
                                detail != null && !detail.isBlank() ?
                                detail :
                                "-");
                case DEADLINE ->
                        text.append("Commit vor ").append(
                                detail != null && !detail.isBlank() ?
                                detail :
                                "-");
                case HEAD ->
                        text.append("Aktueller Stand");
            }
        }
        if (currentCheckedOutRef != null && !currentCheckedOutRef.isBlank()) {
            text.append(" (").append(currentCheckedOutRef).append(')');
        }
        events.publish(new CheckoutInfoChanged(text.toString()));
    }

    private void triggerAutoSave() {
        if (suppressAutoSave || currentContextIndex < 0 || currentContextIndex >= repositoryContexts.size()) {
            return;
        }
        RepositoryContext context = repositoryContexts.get(currentContextIndex);
        EvaluationSaveData data = buildSaveData();
        autoSaveService.scheduleSave(context.evaluationFile(), data);
    }

    private void saveCurrentContext() {
        if (currentContextIndex < 0 || currentContextIndex >= repositoryContexts.size()) {
            return;
        }
        RepositoryContext context = repositoryContexts.get(currentContextIndex);
        EvaluationSaveData data = buildSaveData();
        try {
            autoSaveService.writeImmediately(context.evaluationFile(), data);
        } catch (IOException ex) {
            dialogService.showError("Autosave", "Bewertung konnte nicht gespeichert werden: " + ex.getMessage());
            publishLogEntry("Autosave fehlgeschlagen: " + ex.getMessage(), ex, true);
        }
    }

    private EvaluationSaveData buildSaveData() {
        EvaluationSaveData data = new EvaluationSaveData();
        data.setCheckedOutReference(currentCheckedOutRef);
        data.setRepositoryUrl(currentRepositoryUrl);
        data.setPlaceholderValue(currentPlaceholderValue);
        data.setEvaluationTitle(Optional.ofNullable(evaluationConfig.getTitle()).orElse(""));
        data.setCheckoutStrategy(currentCheckoutStrategy.encode().orElse(null));
        Map<String, NodeSaveState> snapshot = EvaluationStateSynchronizer.captureNodeStates(rootNodes, logReferences);
        data.setNodes(snapshot);
        return data;
    }

    private void normalizeLegacyLogReferences(Map<String, NodeSaveState> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.values().forEach(state -> {
            String logFile = state.getLastLogFile();
            if (logFile == null || logFile.isBlank()) {
                return;
            }
            String sanitized = logFile.trim().replace('\\', '/');
            if (sanitized.startsWith(".eval/")) {
                sanitized = sanitized.substring(".eval/".length());
            }
            if (!sanitized.startsWith("logs/")) {
                int idx = sanitized.lastIndexOf('/');
                String fileName =
                        idx >= 0 ?
                        sanitized.substring(idx + 1) :
                        sanitized;
                sanitized =
                        fileName.isBlank() ?
                        null :
                        "logs/" + fileName;
            }
            if (sanitized == null || sanitized.isBlank()) {
                state.setLastLogFile(null);
            } else {
                state.setLastLogFile(sanitized);
            }
        });
    }

    private void runWithoutAutoSave(Runnable action) {
        boolean previous = suppressAutoSave;
        suppressAutoSave = true;
        try {
            action.run();
        } finally {
            suppressAutoSave = previous;
        }
    }

    private void resetNodeState(EvaluationNode node) {
        node.setComment(null);
        if (node.isLeaf()) {
            node.clearAchievedPoints();
        } else {
            node.getChildren().forEach(this::resetNodeState);
            node.refreshAggregatedPoints();
        }
        node.setStatus(EvaluationStatus.PENDING);
    }

    private void registerNodeListeners(EvaluationNode node) {
        node.achievedPointsProperty().addListener((obs, oldVal, newVal) -> {
            updateTotals();
            events.publish(new EvaluationTreeRefreshRequested());
            triggerAutoSave();
        });
        node.commentProperty().addListener((obs, oldVal, newVal) -> triggerAutoSave());
        node.getChildren().forEach(this::registerNodeListeners);
    }

    private Path resolveEvaluationsRootDirectory() {
        Path baseEvaluations = baseDirectory.resolve("evaluations");
        return baseEvaluations.resolve(buildConfigSlug());
    }

    private String buildConfigSlug() {
        String title = Optional.ofNullable(evaluationConfig)
                               .map(EvaluationConfig::getTitle)
                               .map(String::trim)
                               .filter(value -> !value.isBlank())
                               .orElse("default");
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                                      .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        normalized = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-_]+", "-");
        normalized = normalized.replaceAll("-+", "-");
        normalized = normalized.replaceAll("^-|-$", "");
        if (normalized.isBlank()) {
            normalized = "default";
        }
        return normalized;
    }

    private String buildFeedbackFileName() {
        return "feedback-" + buildConfigSlug() + ".md";
    }

    private String buildEvaluationFileName() {
        return buildConfigSlug() + ".json";
    }

    private void updateStatus(String message) {
        events.publish(new StatusMessageUpdated(message));
        publishLogEntry(message, null, false);
    }

    private void publishLogEntry(String message, Throwable throwable, boolean error) {
        String stackTrace =
                throwable != null ?
                stackTraceToString(throwable) :
                null;
        String timestamp = LocalTime.now().format(TIME_FORMAT);
        String normalizedMessage =
                message != null ?
                message :
                "";
        String entry = "[" + timestamp + "] " + normalizedMessage;
        events.publish(new StatusLogEntryAdded(entry, stackTrace, error));
    }

    private String stackTraceToString(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private String formatPlaceholder(int value) {
        return String.format(Locale.ROOT, "%03d", value);
    }
}
