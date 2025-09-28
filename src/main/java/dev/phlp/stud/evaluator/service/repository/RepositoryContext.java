package dev.phlp.stud.evaluator.service.repository;

import java.nio.file.Path;
import java.util.Objects;

public record RepositoryContext(
        int placeholderValue,
        String repositoryUrl,
        Path repositoryPath,
        Path evaluationDirectory,
        Path evaluationFile,
        Path logsDirectory,
        CheckoutInfo checkoutInfo) {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RepositoryContext(
                int value,
                String url,
                Path path,
                Path evalDirectory,
                Path file,
                Path logDirectory,
                CheckoutInfo info
        ))) {
            return false;
        }
        return placeholderValue == value && Objects.equals(repositoryUrl, url)
                && Objects.equals(repositoryPath, path)
                && Objects.equals(evaluationDirectory, evalDirectory)
                && Objects.equals(evaluationFile, file)
                && Objects.equals(logsDirectory, logDirectory)
                && Objects.equals(checkoutInfo, info);
    }
}
