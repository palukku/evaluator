package dev.phlp.stud.evaluator.controller;

import dev.phlp.stud.evaluator.EvaluatorApp;
import dev.phlp.stud.evaluator.model.config.EvaluationConfig;
import dev.phlp.stud.evaluator.model.config.EvaluationNodeConfig;
import dev.phlp.stud.evaluator.service.storage.ConfigService;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ConfigurationEditorController {
    private static final PseudoClass DROP_BEFORE = PseudoClass.getPseudoClass("drop-before");
    private static final PseudoClass DROP_AFTER = PseudoClass.getPseudoClass("drop-after");
    private static final PseudoClass DROP_INTO = PseudoClass.getPseudoClass("drop-into");
    private static final PseudoClass DROP_ROOT = PseudoClass.getPseudoClass("drop-root");
    @FXML
    private TextField titleField;
    @FXML
    private TextField repositoryTemplateField;
    @FXML
    private TextField placeholderField;
    @FXML
    private TextField tagField;
    @FXML
    private DatePicker deadlinePicker;
    @FXML
    private TextArea generalCommentArea;
    @FXML
    private TreeView<EvaluationNodeConfig> categoryTree;
    @FXML
    private Button addRootNodeButton;
    @FXML
    private Button addChildNodeButton;
    @FXML
    private Button removeNodeButton;
    @FXML
    private TextField nodeNameField;
    @FXML
    private Spinner<Double> maxPointsSpinner;
    @FXML
    private TextArea commandsArea;
    @FXML
    private TextArea nodeCommentArea;
    @FXML
    private CheckBox pseudoCheckBox;
    @FXML
    private Button loadButton;
    @FXML
    private Button saveButton;
    @FXML
    private Button saveAsButton;
    @FXML
    private Button startEvaluationButton;
    @FXML
    private Button backButton;
    @FXML
    private Label statusLabel;

    private final TreeItem<EvaluationNodeConfig> treeRoot = new TreeItem<>();

    private EvaluatorApp application;
    private Stage stage;
    private ConfigService configService;
    private EvaluationConfig currentConfig;
    private File currentFile;
    private Path baseDirectory;

    private TreeItem<EvaluationNodeConfig> selectedTreeItem;
    private boolean updatingNodeForm;
    private boolean updatingGeneralFields;
    private TreeItem<EvaluationNodeConfig> draggedItem;

    public void init(EvaluatorApp application, Stage stage, ConfigService configService,
                     EvaluationConfig config, File sourceFile, Path baseDirectory) {
        this.application = Objects.requireNonNull(application);
        this.stage = Objects.requireNonNull(stage);
        this.configService = Objects.requireNonNull(configService);
        this.currentFile = sourceFile;
        this.baseDirectory = baseDirectory != null ? baseDirectory.toAbsolutePath() : Path.of(System.getProperty("user.dir")).toAbsolutePath();

        configureControls();
        EvaluationConfig initial = config != null ? copyConfiguration(config) : createDefaultConfiguration();
        loadConfiguration(initial);
    }

    private void configureControls() {
        treeRoot.setExpanded(true);
        categoryTree.setRoot(treeRoot);
        categoryTree.setShowRoot(false);
        categoryTree.setCellFactory(tv -> new ConfigurationTreeCell());
        categoryTree.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> onTreeSelectionChanged(newVal));

        categoryTree.setOnDragOver(event -> {
            if (draggedItem != null && isRootDropTarget(event)) {
                categoryTree.pseudoClassStateChanged(DROP_ROOT, true);
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            } else {
                categoryTree.pseudoClassStateChanged(DROP_ROOT, false);
            }
        });
        categoryTree.setOnDragExited(event -> categoryTree.pseudoClassStateChanged(DROP_ROOT, false));
        categoryTree.setOnDragDropped(event -> {
            boolean completed = false;
            if (draggedItem != null && isRootDropTarget(event)) {
                moveTreeItem(draggedItem, treeRoot, treeRoot.getChildren().size());
                updateStatus("Element verschoben");
                event.setDropCompleted(true);
                draggedItem = null;
                completed = true;
            }
            categoryTree.pseudoClassStateChanged(DROP_ROOT, false);
            if (completed) {
                event.consume();
            }
        });
        categoryTree.setOnDragDone(event -> categoryTree.pseudoClassStateChanged(DROP_ROOT, false));

        addRootNodeButton.setOnAction(event -> addRootNode());
        addChildNodeButton.setOnAction(event -> addChildNode());
        removeNodeButton.setOnAction(event -> removeSelectedNode());
        maxPointsSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, Double.MAX_VALUE, 0.0, 0.5));
        maxPointsSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingNodeForm && selectedTreeItem != null && selectedTreeItem.getValue() != null && newVal != null) {
                if (selectedTreeItem.getValue().isPseudo()) {
                    return;
                }
                selectedTreeItem.getValue().setMaxPoints(newVal);
                updateStatus("Max-Punkte aktualisiert");
            }
        });

        nodeNameField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingNodeForm && selectedTreeItem != null && selectedTreeItem.getValue() != null) {
                selectedTreeItem.getValue().setName(newVal);
                categoryTree.refresh();
            }
        });

        commandsArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingNodeForm && selectedTreeItem != null && selectedTreeItem.getValue() != null) {
                selectedTreeItem.getValue().setCommands(parseCommands(newVal));
            }
        });

        nodeCommentArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingNodeForm && selectedTreeItem != null && selectedTreeItem.getValue() != null) {
                selectedTreeItem.getValue().setComment(newVal);
            }
        });

        pseudoCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingNodeForm || selectedTreeItem == null || selectedTreeItem.getValue() == null) {
                return;
            }
            EvaluationNodeConfig node = selectedTreeItem.getValue();
            node.setPseudo(Boolean.TRUE.equals(newVal));
            updatingNodeForm = true;
            if (Boolean.TRUE.equals(newVal)) {
                node.setMaxPoints(0.0);
                maxPointsSpinner.getValueFactory().setValue(0.0);
            }
            maxPointsSpinner.setDisable(Boolean.TRUE.equals(newVal));
            updatingNodeForm = false;
            categoryTree.refresh();
            updateStatus("Pseudo-Status aktualisiert");
        });

        loadButton.setOnAction(event -> chooseAndLoadConfiguration());
        saveButton.setOnAction(event -> saveConfiguration(false));
        saveAsButton.setOnAction(event -> saveConfiguration(true));
        startEvaluationButton.setOnAction(event -> startEvaluation());
        backButton.setOnAction(event -> application.returnToStart());

        titleField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingGeneralFields && currentConfig != null) {
                currentConfig.setTitle(newVal);
                updateStageTitle();
            }
        });
        repositoryTemplateField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingGeneralFields && currentConfig != null) {
                currentConfig.setRepositoryUrlTemplate(newVal);
            }
        });
        placeholderField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingGeneralFields && currentConfig != null) {
                currentConfig.setRepositoryNumberPlaceholder(newVal);
            }
        });
        tagField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingGeneralFields && currentConfig != null) {
                currentConfig.setTag(newVal);
            }
        });
        deadlinePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingGeneralFields && currentConfig != null) {
                currentConfig.setDeadline(newVal);
            }
        });

        generalCommentArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingGeneralFields && currentConfig != null) {
                currentConfig.setComment(newVal);
            }
        });

        disableNodeForm(true);
        updateNodeButtons();
    }

    private EvaluationConfig createDefaultConfiguration() {
        EvaluationConfig config = new EvaluationConfig();
        config.setRepositoryNumberPlaceholder("{{number}}");
        config.setComment("");
        config.setRootCategories(new ArrayList<>());
        return config;
    }

    private void loadConfiguration(EvaluationConfig config) {
        if (config == null) {
            config = createDefaultConfiguration();
        }
        this.currentConfig = config;

        updatingGeneralFields = true;
        titleField.setText(Optional.ofNullable(config.getTitle()).orElse(""));
        repositoryTemplateField.setText(Optional.ofNullable(config.getRepositoryUrlTemplate()).orElse(""));
        placeholderField.setText(Optional.ofNullable(config.getRepositoryNumberPlaceholder()).orElse("{{number}}"));
        tagField.setText(Optional.ofNullable(config.getTag()).orElse(""));
        deadlinePicker.setValue(config.getDeadline());
        generalCommentArea.setText(Optional.ofNullable(config.getComment()).orElse(""));
        updatingGeneralFields = false;
        updateStageTitle();

        populateTree();
        updateStatus("Konfiguration geladen");
    }

    private void populateTree() {
        treeRoot.getChildren().clear();
        ensureConfigurationAvailable();
        currentConfig.getRootCategories().forEach(node -> treeRoot.getChildren().add(createTreeItem(node)));
        treeRoot.setExpanded(true);
        categoryTree.refresh();
        disableNodeForm(true);
        updateNodeButtons();
    }

    private TreeItem<EvaluationNodeConfig> createTreeItem(EvaluationNodeConfig node) {
        TreeItem<EvaluationNodeConfig> item = new TreeItem<>(node);
        item.setExpanded(true);
        if (node.getChildren() != null) {
            node.getChildren().forEach(child -> item.getChildren().add(createTreeItem(child)));
        }
        return item;
    }

    private void onTreeSelectionChanged(TreeItem<EvaluationNodeConfig> item) {
        selectedTreeItem = item;
        if (item == null || item.getValue() == null) {
            disableNodeForm(true);
            if (nodeCommentArea != null) {
                nodeCommentArea.clear();
            }
            updateNodeButtons();
            return;
        }
        disableNodeForm(false);
        updatingNodeForm = true;
        nodeNameField.setText(Optional.ofNullable(item.getValue().getName()).orElse(""));
        maxPointsSpinner.getValueFactory().setValue(item.getValue().getMaxPoints());
        commandsArea.setText(String.join(System.lineSeparator(), item.getValue().getCommands()));
        nodeCommentArea.setText(Optional.ofNullable(item.getValue().getComment()).orElse(""));
        boolean pseudo = item.getValue().isPseudo();
        pseudoCheckBox.setSelected(pseudo);
        maxPointsSpinner.setDisable(pseudo);
        updatingNodeForm = false;
        updateNodeButtons();
    }

    private void disableNodeForm(boolean disable) {
        nodeNameField.setDisable(disable);
        maxPointsSpinner.setDisable(disable);
        commandsArea.setDisable(disable);
        if (nodeCommentArea != null) {
            nodeCommentArea.setDisable(disable);
            if (disable) {
                nodeCommentArea.clear();
            }
        }
        if (pseudoCheckBox != null) {
            pseudoCheckBox.setDisable(disable);
            if (disable) {
                pseudoCheckBox.setSelected(false);
            }
        }
    }

    private void addRootNode() {
        ensureConfigurationAvailable();
        EvaluationNodeConfig node = createNode("Neue Kategorie");
        currentConfig.getRootCategories().add(node);
        TreeItem<EvaluationNodeConfig> item = createTreeItem(node);
        treeRoot.getChildren().add(item);
        categoryTree.getSelectionModel().select(item);
        updateStatus("Kategorie hinzugefuegt");
    }

    private void addChildNode() {
        if (selectedTreeItem == null) {
            updateStatus("Bitte erst ein Element auswaehlen");
            return;
        }
        EvaluationNodeConfig parent = selectedTreeItem.getValue();
        if (parent.getChildren() == null) {
            parent.setChildren(new ArrayList<>());
        }
        EvaluationNodeConfig child = createNode("Neues Element");
        parent.getChildren().add(child);
        TreeItem<EvaluationNodeConfig> childItem = createTreeItem(child);
        selectedTreeItem.getChildren().add(childItem);
        selectedTreeItem.setExpanded(true);
        categoryTree.getSelectionModel().select(childItem);
        updateStatus("Unterelement hinzugefuegt");
    }

    private void removeSelectedNode() {
        if (selectedTreeItem == null) {
            return;
        }
        TreeItem<EvaluationNodeConfig> parentItem = selectedTreeItem.getParent();
        if (parentItem == null) {
            return;
        }
        EvaluationNodeConfig node = selectedTreeItem.getValue();
        if (parentItem == treeRoot) {
            currentConfig.getRootCategories().remove(node);
        } else {
            EvaluationNodeConfig parent = parentItem.getValue();
            if (parent.getChildren() != null) {
                parent.getChildren().remove(node);
            }
        }
        parentItem.getChildren().remove(selectedTreeItem);
        selectedTreeItem = null;
        disableNodeForm(true);
        updateNodeButtons();
        updateStatus("Element entfernt");
    }

    private void moveTreeItem(TreeItem<EvaluationNodeConfig> item, TreeItem<EvaluationNodeConfig> newParent, int targetIndex) {
        ensureConfigurationAvailable();
        if (item == null || newParent == null || item == treeRoot || newParent == item || isDescendant(item, newParent)) {
            return;
        }
        TreeItem<EvaluationNodeConfig> oldParent = item.getParent();
        if (oldParent == null) {
            return;
        }
        List<TreeItem<EvaluationNodeConfig>> oldSiblings = oldParent.getChildren();
        int oldIndex = oldSiblings.indexOf(item);
        if (oldIndex < 0) {
            return;
        }
        oldSiblings.remove(oldIndex);
        List<EvaluationNodeConfig> oldModelChildren = getModelChildren(oldParent);
        if (oldModelChildren != null && oldIndex < oldModelChildren.size()) {
            oldModelChildren.remove(oldIndex);
        }

        List<TreeItem<EvaluationNodeConfig>> newSiblings = newParent.getChildren();
        if (targetIndex < 0) {
            targetIndex = 0;
        }
        if (oldParent == newParent && targetIndex > oldIndex) {
            targetIndex--;
        }
        if (targetIndex > newSiblings.size()) {
            targetIndex = newSiblings.size();
        }
        newSiblings.add(targetIndex, item);
        List<EvaluationNodeConfig> newModelChildren = getModelChildren(newParent);
        if (newModelChildren != null) {
            if (targetIndex > newModelChildren.size()) {
                targetIndex = newModelChildren.size();
            }
            newModelChildren.add(targetIndex, item.getValue());
        }
        categoryTree.getSelectionModel().select(item);
        selectedTreeItem = item;
        updateNodeButtons();
    }

    private List<EvaluationNodeConfig> getModelChildren(TreeItem<EvaluationNodeConfig> parentItem) {
        ensureConfigurationAvailable();
        if (parentItem == treeRoot) {
            return currentConfig.getRootCategories();
        }
        EvaluationNodeConfig parentNode = parentItem.getValue();
        if (parentNode == null) {
            return null;
        }
        ensureChildrenList(parentNode);
        return parentNode.getChildren();
    }

    private void ensureChildrenList(EvaluationNodeConfig node) {
        if (node.getChildren() == null) {
            node.setChildren(new ArrayList<>());
        }
    }

    private boolean isDescendant(TreeItem<EvaluationNodeConfig> potentialAncestor, TreeItem<EvaluationNodeConfig> candidate) {
        if (potentialAncestor == null || candidate == null) {
            return false;
        }
        TreeItem<EvaluationNodeConfig> current = candidate;
        while (current != null) {
            if (current == potentialAncestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean isRootDropTarget(DragEvent event) {
        if (event == null) {
            return false;
        }
        Object target = event.getGestureTarget();
        return !(target instanceof TreeCell<?>);
    }

    private DropPosition determineDropPosition(DragEvent event, TreeCell<EvaluationNodeConfig> cell) {
        double y = event.getY();
        double height = cell.getHeight() <= 0 ? cell.getBoundsInLocal().getHeight() : cell.getHeight();
        if (height <= 0) {
            return DropPosition.INTO;
        }
        if (y < height / 3) {
            return DropPosition.BEFORE;
        }
        if (y > height * 2 / 3) {
            return DropPosition.AFTER;
        }
        return DropPosition.INTO;
    }

    private enum DropPosition {
        BEFORE,
        AFTER,
        INTO
    }

    private class ConfigurationTreeCell extends TreeCell<EvaluationNodeConfig> {
        ConfigurationTreeCell() {
            setOnDragDetected(event -> {
                if (getItem() == null) {
                    return;
                }
                draggedItem = getTreeItem();
                categoryTree.pseudoClassStateChanged(DROP_ROOT, false);
                Dragboard db = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(Optional.ofNullable(getItem().getName()).orElse(""));
                db.setContent(content);
                event.consume();
            });

            setOnDragOver(event -> {
                TreeItem<EvaluationNodeConfig> targetItem = getTreeItem();
                if (draggedItem == null || targetItem == null || draggedItem == targetItem
                        || isDescendant(draggedItem, targetItem)) {
                    showDropIndicator(null);
                    return;
                }
                DropPosition position = determineDropPosition(event, this);
                showDropIndicator(position);
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            });

            setOnDragExited(event -> showDropIndicator(null));

            setOnDragDropped(event -> {
                TreeItem<EvaluationNodeConfig> targetItem = getTreeItem();
                if (draggedItem == null || targetItem == null || draggedItem == targetItem
                        || isDescendant(draggedItem, targetItem)) {
                    showDropIndicator(null);
                    return;
                }
                DropPosition position = determineDropPosition(event, this);
                showDropIndicator(null);
                categoryTree.pseudoClassStateChanged(DROP_ROOT, false);
                switch (position) {
                    case BEFORE -> {
                        TreeItem<EvaluationNodeConfig> parent = targetItem.getParent();
                        if (parent != null) {
                            int index = parent.getChildren().indexOf(targetItem);
                            moveTreeItem(draggedItem, parent, index);
                            updateStatus("Element verschoben");
                        }
                    }
                    case AFTER -> {
                        TreeItem<EvaluationNodeConfig> parent = targetItem.getParent();
                        if (parent != null) {
                            int index = parent.getChildren().indexOf(targetItem);
                            moveTreeItem(draggedItem, parent, index + 1);
                            updateStatus("Element verschoben");
                        }
                    }
                    case INTO -> {
                        ensureChildrenList(targetItem.getValue());
                        moveTreeItem(draggedItem, targetItem, targetItem.getChildren().size());
                        targetItem.setExpanded(true);
                        updateStatus("Element verschoben");
                    }
                }
                event.setDropCompleted(true);
                draggedItem = null;
                event.consume();
            });

            setOnDragDone(event -> {
                draggedItem = null;
                showDropIndicator(null);
            });
        }

        @Override
        protected void updateItem(EvaluationNodeConfig item, boolean empty) {
            super.updateItem(item, empty);
            showDropIndicator(null);
            if (empty || item == null) {
                setText(null);
            } else {
                String label = Optional.ofNullable(item.getName()).orElse("(Unbenannt)");
                if (item.isPseudo()) {
                    label = label + " [Pseudo]";
                }
                setText(label);
            }
        }

        private void showDropIndicator(DropPosition position) {
            pseudoClassStateChanged(DROP_BEFORE, position == DropPosition.BEFORE);
            pseudoClassStateChanged(DROP_AFTER, position == DropPosition.AFTER);
            pseudoClassStateChanged(DROP_INTO, position == DropPosition.INTO);
        }
    }

    private void updateNodeButtons() {
        boolean hasSelection = selectedTreeItem != null;
        addChildNodeButton.setDisable(!hasSelection);
        removeNodeButton.setDisable(!hasSelection || selectedTreeItem.getParent() == null);
    }

    private EvaluationNodeConfig createNode(String name) {
        EvaluationNodeConfig node = new EvaluationNodeConfig();
        node.setName(name);
        node.setMaxPoints(0.0);
        node.setCommands(new ArrayList<>());
        node.setComment("");
        node.setChildren(new ArrayList<>());
        node.setPseudo(false);
        return node;
    }

    private void configureInitialDirectory(FileChooser chooser) {
        if (baseDirectory == null) {
            return;
        }
        File baseDir = baseDirectory.toFile();
        if (baseDir.exists() && baseDir.isDirectory()) {
            chooser.setInitialDirectory(baseDir);
        }
    }

    private void chooseAndLoadConfiguration() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Konfiguration laden");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        configureInitialDirectory(chooser);
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            loadConfigurationFromFile(file);
        }
    }

    private void loadConfigurationFromFile(File file) {
        try {
            EvaluationConfig config = configService.load(file);
            currentFile = file;
            loadConfiguration(copyConfiguration(config));
            updateStageTitle();
            updateStatus("Konfiguration geladen: " + file.getName());
        } catch (IOException ex) {
            application.showErrorAlert("Konfiguration konnte nicht geladen werden", ex.getMessage());
        }
    }

    private void saveConfiguration(boolean saveAs) {
        if (currentConfig == null) {
            return;
        }
        if (saveAs || currentFile == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Konfiguration speichern");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
            configureInitialDirectory(chooser);
            if (currentFile != null) {
                chooser.setInitialFileName(currentFile.getName());
            }
            File selected = chooser.showSaveDialog(stage);
            if (selected == null) {
                return;
            }
            currentFile = selected;
        }
        try {
            configService.save(currentConfig, currentFile);
            updateStageTitle();
            updateStatus("Konfiguration gespeichert: " + currentFile.getName());
        } catch (IOException ex) {
            application.showErrorAlert("Konfiguration konnte nicht gespeichert werden", ex.getMessage());
        }
    }

    private void startEvaluation() {
        if (currentConfig == null) {
            application.showErrorAlert("Keine Konfiguration", "Bitte erstellen oder laden Sie eine Konfiguration.");
            return;
        }
        application.startEvaluationFromEditor(copyConfiguration(currentConfig));
    }

    private EvaluationConfig copyConfiguration(EvaluationConfig config) {
        if (config == null) {
            return null;
        }
        try {
            return configService.clone(config);
        } catch (IOException ex) {
            return config;
        }
    }

    private void ensureConfigurationAvailable() {
        if (currentConfig == null) {
            currentConfig = createDefaultConfiguration();
        }
        if (currentConfig.getRootCategories() == null) {
            currentConfig.setRootCategories(new ArrayList<>());
        }
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void updateStageTitle() {
        StringBuilder builder = new StringBuilder("Konfigurationseditor");
        if (currentConfig != null && currentConfig.getTitle() != null && !currentConfig.getTitle().isBlank()) {
            builder.append(" - ").append(currentConfig.getTitle());
        }
        if (currentFile != null) {
            builder.append(" [").append(currentFile.getName()).append("]");
        }
        stage.setTitle(application.buildWindowTitle(builder.toString()));
    }

    private List<String> parseCommands(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
