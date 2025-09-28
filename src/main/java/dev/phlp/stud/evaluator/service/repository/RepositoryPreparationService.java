package dev.phlp.stud.evaluator.service.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import dev.phlp.stud.evaluator.model.state.EvaluationSaveData;
import dev.phlp.stud.evaluator.service.git.GitService;
import dev.phlp.stud.evaluator.service.git.GitServiceException;
import dev.phlp.stud.evaluator.service.storage.AutoSaveService;

public class RepositoryPreparationService {
    private final GitService gitService;
    private final AutoSaveService autoSaveService;

    public RepositoryPreparationService(GitService gitService, AutoSaveService autoSaveService) {
        this.gitService = gitService;
        this.autoSaveService = autoSaveService;
    }

    public RepositoryPreparationResult prepareRepositories(RepositoryPreparationRequest request,
                                                           RepositoryPreparationListener listener) {
        List<Integer> placeholderValues = request.placeholderRange().values();
        int total = placeholderValues.size();
        List<RepositoryContext> contexts = new ArrayList<>();
        StringBuilder errors = new StringBuilder();
        Path legacyEvaluationsRoot = Optional.ofNullable(request.evaluationsRoot().getParent()).orElse(null);

        int completed = 0;
        for (int value : placeholderValues) {
            try {
                RepositoryContext context = prepareSingleRepository(request, value, legacyEvaluationsRoot);
                contexts.add(context);
            } catch (GitServiceException | IOException ex) {
                appendError(errors, value, ex);
            } finally {
                completed++;
                if (listener != null) {
                    listener.onProgress(completed, total);
                }
            }
        }
        return new RepositoryPreparationResult(contexts, errors.toString());
    }

    private RepositoryContext prepareSingleRepository(RepositoryPreparationRequest request, int placeholderValue,
                                                      Path legacyEvaluationsRoot) throws IOException, GitServiceException {
        String repositoryUrl = buildRepositoryUrl(request.repositoryTemplate(), request.repositoryNumberPlaceholder(), placeholderValue);
        Path repositoryPath = request.repositoriesRoot().resolve(formatPlaceholder(placeholderValue));
        Files.createDirectories(repositoryPath.getParent());
        gitService.cloneOrUpdate(repositoryUrl, repositoryPath);

        CheckoutInfo checkoutInfo = resolveCheckoutInfo(repositoryPath, request.tag(), request.deadline());

        Path evaluationDirectory = request.evaluationsRoot().resolve(formatPlaceholder(placeholderValue));
        Files.createDirectories(evaluationDirectory);
        Path evaluationFile = evaluationDirectory.resolve(request.evaluationFileName());
        migrateLegacyEvaluationFile(evaluationFile, placeholderValue, request.evaluationsRoot(), legacyEvaluationsRoot);

        if (Files.notExists(evaluationFile)) {
            EvaluationSaveData initial = new EvaluationSaveData();
            initial.setPlaceholderValue(placeholderValue);
            initial.setRepositoryUrl(repositoryUrl);
            checkoutInfo.reference().ifPresent(initial::setCheckedOutReference);
            initial.setEvaluationTitle(request.evaluationTitle());
            checkoutInfo.strategy().encode().ifPresent(initial::setCheckoutStrategy);
            autoSaveService.writeImmediately(evaluationFile, initial);
        }

        migrateLegacyLogs(repositoryPath, evaluationDirectory);
        Path logsDirectory = evaluationDirectory.resolve("logs");
        Files.createDirectories(logsDirectory);

        return new RepositoryContext(placeholderValue, repositoryUrl, repositoryPath, evaluationDirectory,
                evaluationFile, logsDirectory, checkoutInfo);
    }

    private CheckoutInfo resolveCheckoutInfo(Path repositoryPath, Optional<String> tag, Optional<LocalDate> deadline)
            throws GitServiceException {
        if (tag.isPresent()) {
            String tagName = tag.get();
            if (deadline.isPresent()) {
                LocalDate deadlineDate = deadline.get();
                try {
                    Instant tagInstant = gitService.resolveTagCommitInstant(repositoryPath, tagName);
                    Instant deadlineInstant = deadlineDate.atTime(LocalTime.MAX)
                                                          .atZone(ZoneId.systemDefault())
                                                          .toInstant();
                    if (tagInstant.isAfter(deadlineInstant)) {
                        return checkoutByDeadline(repositoryPath, deadlineDate);
                    }
                } catch (GitServiceException ex) {
                    return checkoutByDeadlineOrHead(repositoryPath, deadline);
                }
            }
            try {
                String ref = gitService.checkoutTag(repositoryPath, tagName);
                return new CheckoutInfo(ref, CheckoutStrategy.of(CheckoutMode.TAG, tagName));
            } catch (GitServiceException ex) {
                return checkoutByDeadlineOrHead(repositoryPath, deadline);
            }
        }
        if (deadline.isPresent()) {
            return checkoutByDeadline(repositoryPath, deadline.get());
        }
        return checkoutHead(repositoryPath);
    }

    private CheckoutInfo checkoutByDeadlineOrHead(Path repositoryPath, Optional<LocalDate> deadline)
            throws GitServiceException {
        if (deadline.isPresent()) {
            return checkoutByDeadline(repositoryPath, deadline.get());
        }
        return checkoutHead(repositoryPath);
    }

    private CheckoutInfo checkoutByDeadline(Path repositoryPath, LocalDate deadline) throws GitServiceException {
        String ref = gitService.checkoutLatestBefore(repositoryPath, deadline);
        return new CheckoutInfo(ref, CheckoutStrategy.of(CheckoutMode.DEADLINE, deadline.toString()));
    }

    private CheckoutInfo checkoutHead(Path repositoryPath) throws GitServiceException {
        String ref = gitService.resolveCurrentCommit(repositoryPath);
        return new CheckoutInfo(ref, CheckoutStrategy.of(CheckoutMode.HEAD, null));
    }

    private void migrateLegacyEvaluationFile(Path evaluationFile, int placeholderValue, Path configEvaluationsRoot,
                                             Path legacyEvaluationsRoot) throws IOException {
        if (Files.exists(evaluationFile)) {
            return;
        }
        Files.createDirectories(evaluationFile.getParent());
        String placeholder = formatPlaceholder(placeholderValue);
        Path legacyFlatFile = configEvaluationsRoot.resolve(placeholder + ".json");
        if (Files.exists(legacyFlatFile)) {
            moveWithFallback(legacyFlatFile, evaluationFile);
            return;
        }
        if (legacyEvaluationsRoot != null) {
            Path olderFlatFile = legacyEvaluationsRoot.resolve(placeholder + ".json");
            if (Files.exists(olderFlatFile)) {
                moveWithFallback(olderFlatFile, evaluationFile);
            }
        }
    }

    private void moveWithFallback(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.deleteIfExists(source);
            } catch (IOException ignored) {
                // best effort cleanup
            }
        }
    }

    private void migrateLegacyLogs(Path repositoryPath, Path evaluationDirectory) {
        Path legacyLogs = repositoryPath.resolve(".eval").resolve("logs");
        if (!Files.isDirectory(legacyLogs)) {
            return;
        }
        Path targetLogs = evaluationDirectory.resolve("logs");
        try {
            Files.createDirectories(targetLogs);
            try (Stream<Path> files = Files.walk(legacyLogs)) {
                files.filter(Files::isRegularFile).forEach(file -> {
                    Path target = targetLogs.resolve(file.getFileName());
                    try {
                        Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException moveEx) {
                        try {
                            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException copyEx) {
                            System.err.println("Log migration failed for " + file + ": " + copyEx.getMessage());
                        }
                    }
                });
            }
            try (Stream<Path> cleanup = Files.walk(legacyLogs)) {
                cleanup.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // ignore cleanup errors
                    }
                });
            }
            Path evalRoot = legacyLogs.getParent();
            if (evalRoot != null) {
                try (Stream<Path> remaining = Files.list(evalRoot)) {
                    if (remaining.findAny().isEmpty()) {
                        Files.deleteIfExists(evalRoot);
                    }
                } catch (IOException ignored) {
                    // ignore cleanup errors
                }
            }
        } catch (IOException ex) {
            System.err.println("Log migration failed for repository " + repositoryPath + ": " + ex.getMessage());
        }
    }

    private void appendError(StringBuilder errors, int placeholderValue, Exception ex) {
        errors.append('[')
              .append(formatPlaceholder(placeholderValue))
              .append("] ")
              .append(ex.getMessage());
        Optional.ofNullable(ex.getCause())
                .map(Throwable::getMessage)
                .filter(message -> !message.isBlank())
                .ifPresent(message -> errors.append(" (").append(message).append(')'));
        errors.append(System.lineSeparator());
    }

    private String buildRepositoryUrl(String template, String configuredPlaceholder, int value) {
        String placeholder = Optional.ofNullable(configuredPlaceholder)
                                     .map(String::trim)
                                     .filter(s -> !s.isBlank())
                                     .orElse("{{number}}");
        String formattedValue = formatPlaceholder(value);
        if (template.contains(placeholder)) {
            return template.replace(placeholder, formattedValue);
        }
        if (template.contains("{{number}}")) {
            return template.replace("{{number}}", formattedValue);
        }
        return template;
    }

    private String formatPlaceholder(int value) {
        return String.format(Locale.ROOT, "%03d", value);
    }
}
