package dev.phlp.stud.evaluator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import dev.phlp.stud.evaluator.controller.CommentPaneController;
import dev.phlp.stud.evaluator.controller.ConfigurationEditorController;
import dev.phlp.stud.evaluator.controller.EvaluationTreeController;
import dev.phlp.stud.evaluator.controller.MainController;
import dev.phlp.stud.evaluator.controller.RepositoryToolbarController;
import dev.phlp.stud.evaluator.controller.StartViewController;
import dev.phlp.stud.evaluator.controller.StatusBarController;
import dev.phlp.stud.evaluator.core.di.DefaultServiceRegistry;
import dev.phlp.stud.evaluator.core.di.ServiceRegistry;
import dev.phlp.stud.evaluator.core.events.EventBus;
import dev.phlp.stud.evaluator.core.events.SimpleEventBus;
import dev.phlp.stud.evaluator.model.config.EvaluationConfig;
import dev.phlp.stud.evaluator.service.command.CommandLogService;
import dev.phlp.stud.evaluator.service.command.CommandRunner;
import dev.phlp.stud.evaluator.service.dialog.DialogService;
import dev.phlp.stud.evaluator.service.dialog.FxDialogService;
import dev.phlp.stud.evaluator.service.export.MarkdownExporter;
import dev.phlp.stud.evaluator.service.git.GitService;
import dev.phlp.stud.evaluator.service.storage.AutoSaveService;
import dev.phlp.stud.evaluator.service.storage.ConfigService;
import dev.phlp.stud.evaluator.service.workflow.DefaultEvaluationWorkflow;
import dev.phlp.stud.evaluator.service.workflow.EvaluationWorkflow;

public class EvaluatorApp extends Application {

    private static final String WINDOW_TITLE_SUFFIX = " | Evaluator by github.com/palukku";
    private final ConfigService configService = new ConfigService();
    private final AutoSaveService autoSaveService = new AutoSaveService();
    private final CommandRunner commandRunner = new CommandRunner();
    private final CommandLogService commandLogService = new CommandLogService();
    private final MarkdownExporter markdownExporter = new MarkdownExporter();
    private final GitService gitService = new GitService();
    private final DialogService dialogService = new FxDialogService();
    private final ServiceRegistry serviceRegistry = new DefaultServiceRegistry();
    private final EventBus eventBus = new SimpleEventBus();
    private final Path applicationRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath();

    private Stage primaryStage;
    private MainController mainController;
    private EvaluationWorkflow evaluationWorkflow;

    public EvaluatorApp() {
        serviceRegistry.add(ConfigService.class, configService);
        serviceRegistry.add(AutoSaveService.class, autoSaveService);
        serviceRegistry.add(CommandRunner.class, commandRunner);
        serviceRegistry.add(CommandLogService.class, commandLogService);
        serviceRegistry.add(MarkdownExporter.class, markdownExporter);
        serviceRegistry.add(GitService.class, gitService);
        serviceRegistry.add(DialogService.class, dialogService);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        showStartView();
    }

    private void showStartView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/start_view.fxml"));
            Parent root = loader.load();
            StartViewController controller = loader.getController();
            controller.init(this);

            Scene scene = new Scene(root);
            applyStyles(scene);
            primaryStage.setTitle(buildWindowTitle(""));
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException ex) {
            showErrorAlert("Editor konnte nicht geoeffnet werden", ex.getMessage());
            Platform.exit();
        }
    }

    public void handleLoadConfigurationRequest() {
        File configFile = promptForConfig(primaryStage);
        if (configFile == null) {
            return;
        }
        try {
            EvaluationConfig config = configService.load(configFile);
            openMainView(config);
        } catch (IOException ex) {
            showErrorAlert("Konfiguration konnte nicht geladen werden", ex.getMessage());
        }
    }

    public void handleOpenEditorRequest(EvaluationConfig config, File sourceFile) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/config_editor.fxml"));
            Parent root = loader.load();
            ConfigurationEditorController controller = loader.getController();
            controller.init(this, primaryStage, configService, config, sourceFile, applicationRoot);

            Scene scene = new Scene(root);
            applyStyles(scene);
            primaryStage.setTitle(buildWindowTitle("Konfigurationseditor"));
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException ex) {
            showErrorAlert("Editor konnte nicht geoeffnet werden", ex.getMessage());
        }
    }

    public void startEvaluationFromEditor(EvaluationConfig config) {
        openMainView(config);
    }

    private void openMainView(EvaluationConfig config) {
        try {
            evaluationWorkflow = new DefaultEvaluationWorkflow(serviceRegistry, eventBus);
            serviceRegistry.add(EvaluationWorkflow.class, evaluationWorkflow);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_view.fxml"));
            loader.setControllerFactory(type -> {
                try {
                    if (type == MainController.class) {
                        return new MainController(evaluationWorkflow, dialogService);
                    }
                    if (type == RepositoryToolbarController.class) {
                        return new RepositoryToolbarController(evaluationWorkflow, eventBus);
                    }
                    if (type == EvaluationTreeController.class) {
                        return new EvaluationTreeController(evaluationWorkflow, eventBus, dialogService);
                    }
                    if (type == StatusBarController.class) {
                        return new StatusBarController(eventBus);
                    }
                    if (type == CommentPaneController.class) {
                        return new CommentPaneController(eventBus);
                    }
                    return type.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Parent root = loader.load();
            Scene scene = new Scene(root);
            applyStyles(scene);

            if (mainController != null) {
                mainController.shutdown();
            }
            mainController = loader.getController();
            mainController.init(primaryStage, config, applicationRoot);

            String baseTitle = config.getTitle();
            primaryStage.setTitle(buildWindowTitle(baseTitle));
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException ex) {
            ex.printStackTrace();
            showErrorAlert("Hauptansicht konnte nicht geladen werden", ex.getMessage());
        }
    }

    public void returnToStart() {
        showStartView();
    }

    public void shutdownApplication() {
        Platform.exit();
    }

    public String buildWindowTitle(String baseTitle) {
        String sanitized = "";
        if (baseTitle != null) {
            sanitized = baseTitle.strip();
        }

        if (sanitized.isBlank()) {
            return WINDOW_TITLE_SUFFIX.replace('|', ' ').strip();
        }
        return sanitized + WINDOW_TITLE_SUFFIX;
    }

    private File promptForConfig(Stage owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Konfigurationsdatei auswaehlen");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        if (Files.isDirectory(applicationRoot)) {
            chooser.setInitialDirectory(applicationRoot.toFile());
        }
        return chooser.showOpenDialog(owner);
    }

    private void showFatalError(Stage owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.initOwner(owner);
        alert.showAndWait();
    }

    public void showErrorAlert(String title, String message) {
        showFatalError(primaryStage, title, message);
    }

    private void applyStyles(Scene scene) {
        scene.getStylesheets().clear();
        scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
    }

    @Override
    public void stop() {
        if (mainController != null) {
            mainController.shutdown();
        }
    }
}
