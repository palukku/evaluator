package dev.phlp.stud.evaluator.service.repository;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public final class RepositoryPreparationRequest {
    private final String repositoryTemplate;
    private final PlaceholderRange placeholderRange;
    private final Path repositoriesRoot;
    private final Path evaluationsRoot;
    private final String evaluationFileName;
    private final String evaluationTitle;
    private final String repositoryNumberPlaceholder;
    private final Optional<String> tag;
    private final Optional<LocalDate> deadline;

    private RepositoryPreparationRequest(Builder builder) {
        this.repositoryTemplate = Objects.requireNonNull(builder.repositoryTemplate, "repositoryTemplate");
        this.placeholderRange = Objects.requireNonNull(builder.placeholderRange, "placeholderRange");
        this.repositoriesRoot = Objects.requireNonNull(builder.repositoriesRoot, "repositoriesRoot");
        this.evaluationsRoot = Objects.requireNonNull(builder.evaluationsRoot, "evaluationsRoot");
        this.evaluationFileName = Objects.requireNonNull(builder.evaluationFileName, "evaluationFileName");
        this.evaluationTitle = Optional.ofNullable(builder.evaluationTitle).orElse("");
        this.repositoryNumberPlaceholder = Optional.ofNullable(builder.repositoryNumberPlaceholder).orElse("{{number}}");
        this.tag = Optional.ofNullable(builder.tag).map(String::trim).filter(value -> !value.isEmpty());
        this.deadline = Optional.ofNullable(builder.deadline);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String repositoryTemplate() {
        return repositoryTemplate;
    }

    public PlaceholderRange placeholderRange() {
        return placeholderRange;
    }

    public Path repositoriesRoot() {
        return repositoriesRoot;
    }

    public Path evaluationsRoot() {
        return evaluationsRoot;
    }

    public String evaluationFileName() {
        return evaluationFileName;
    }

    public String evaluationTitle() {
        return evaluationTitle;
    }

    public String repositoryNumberPlaceholder() {
        return repositoryNumberPlaceholder;
    }

    public Optional<String> tag() {
        return tag;
    }

    public Optional<LocalDate> deadline() {
        return deadline;
    }

    public static final class Builder {
        private String repositoryTemplate;
        private PlaceholderRange placeholderRange;
        private Path repositoriesRoot;
        private Path evaluationsRoot;
        private String evaluationFileName;
        private String evaluationTitle;
        private String repositoryNumberPlaceholder;
        private String tag;
        private LocalDate deadline;

        private Builder() {
        }

        public Builder repositoryTemplate(String repositoryTemplate) {
            this.repositoryTemplate = repositoryTemplate;
            return this;
        }

        public Builder placeholderRange(PlaceholderRange placeholderRange) {
            this.placeholderRange = placeholderRange;
            return this;
        }

        public Builder repositoriesRoot(Path repositoriesRoot) {
            this.repositoriesRoot = repositoriesRoot;
            return this;
        }

        public Builder evaluationsRoot(Path evaluationsRoot) {
            this.evaluationsRoot = evaluationsRoot;
            return this;
        }

        public Builder evaluationFileName(String evaluationFileName) {
            this.evaluationFileName = evaluationFileName;
            return this;
        }

        public Builder evaluationTitle(String evaluationTitle) {
            this.evaluationTitle = evaluationTitle;
            return this;
        }

        public Builder repositoryNumberPlaceholder(String repositoryNumberPlaceholder) {
            this.repositoryNumberPlaceholder = repositoryNumberPlaceholder;
            return this;
        }

        public Builder tag(String tag) {
            this.tag = tag;
            return this;
        }

        public Builder deadline(LocalDate deadline) {
            this.deadline = deadline;
            return this;
        }

        public RepositoryPreparationRequest build() {
            return new RepositoryPreparationRequest(this);
        }
    }
}
