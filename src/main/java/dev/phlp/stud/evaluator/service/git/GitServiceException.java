package dev.phlp.stud.evaluator.service.git;

public class GitServiceException extends Exception {
    public GitServiceException(String message) {
        super(message);
    }

    public GitServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
