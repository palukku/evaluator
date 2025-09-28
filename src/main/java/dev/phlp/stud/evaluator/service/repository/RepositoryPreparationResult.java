package dev.phlp.stud.evaluator.service.repository;

import java.util.Collections;
import java.util.List;

public record RepositoryPreparationResult(
        List<RepositoryContext> contexts,
        String errors) {
    public RepositoryPreparationResult(List<RepositoryContext> contexts, String errors) {
        this.contexts =
                contexts != null ?
                List.copyOf(contexts) :
                List.of();
        this.errors =
                errors != null ?
                errors :
                "";
    }

    @Override
    public List<RepositoryContext> contexts() {
        return Collections.unmodifiableList(contexts);
    }
}
