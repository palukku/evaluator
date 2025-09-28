package dev.phlp.stud.evaluator.service.dialog;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * JavaFX backed {@link DialogService} implementation. Alerts are executed on
 * the JavaFX application thread and use the configured owner stage when
 * present.
 */
public final class FxDialogService implements DialogService {

    private final AtomicReference<Stage> owner = new AtomicReference<>();

    /**
     * Configures the owner stage for subsequent dialogs.
     *
     * @param stage JavaFX stage to use as owner; may be {@code null}
     */
    @Override
    public void setOwner(Stage stage) {
        owner.set(stage);
    }

    @Override
    public void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    @Override
    public void showInfo(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Objects.requireNonNull(type, "AlertType must not be null");
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message);
            Stage stage = owner.get();
            if (stage != null) {
                alert.initOwner(stage);
            }
            alert.showAndWait();
        });
    }
}
