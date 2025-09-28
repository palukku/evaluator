package dev.phlp.stud.evaluator.service.dialog;

import javafx.stage.Stage;

/**
 * Abstraction for presenting user facing dialogs. Allows services to surface
 * messages without accessing UI controllers directly.
 */
public interface DialogService {

    /**
     * Displays an error dialog to the user.
     *
     * @param title   window title
     * @param message human readable error description
     */
    void showError(String title, String message);

    /**
     * Displays an informational dialog to the user.
     *
     * @param title   window title
     * @param message informational text
     */
    void showInfo(String title, String message);

    /**
     * Provides the owner stage for subsequent dialogs. Default implementation
     * is a no-op allowing headless or test implementations to ignore it.
     *
     * @param stage JavaFX stage used as dialog owner
     */
    default void setOwner(Stage stage) {
        // default no-op
    }
}
