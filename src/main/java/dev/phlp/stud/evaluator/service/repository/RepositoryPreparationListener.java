package dev.phlp.stud.evaluator.service.repository;

@FunctionalInterface
public interface RepositoryPreparationListener {
    void onProgress(int completed, int total);
}
