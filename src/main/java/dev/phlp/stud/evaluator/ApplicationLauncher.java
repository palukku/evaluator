package dev.phlp.stud.evaluator;

public final class ApplicationLauncher {
    private ApplicationLauncher() {
        // Utility class
    }

    static void main(String[] args) {
        EvaluatorApp.launch(EvaluatorApp.class, args);
    }
}
