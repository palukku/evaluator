package dev.phlp.stud.evaluator.controller.ui;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

import dev.phlp.stud.evaluator.model.EvaluationNode;
import dev.phlp.stud.evaluator.model.EvaluationStatus;

/**
 * Provides a central place to initialise the evaluation tree view.
 */
public class EvaluationTreeViewAdapter {

    private final TreeTableView<EvaluationNode> evaluationTreeTable;
    private final TreeTableColumn<EvaluationNode, String> nameColumn;
    private final TreeTableColumn<EvaluationNode, EvaluationNode> actionsColumn;
    private final TreeTableColumn<EvaluationNode, Number> maxPointsColumn;
    private final TreeTableColumn<EvaluationNode, EvaluationNode> achievedPointsColumn;
    private final Function<Number, String> pointFormatter;
    private final String[] rowStyleClasses;
    private final Function<EvaluationNode, String> rowStyleResolver;
    private final Consumer<EvaluationNode> playCommandHandler;
    private final BiConsumer<EvaluationNode, CheckBox> checkboxHandler;
    private final BiConsumer<EvaluationNode, CheckBox> checkboxStateUpdater;
    private final BooleanSupplier repositoryReadySupplier;

    public EvaluationTreeViewAdapter(TreeTableView<EvaluationNode> evaluationTreeTable,
                                     TreeTableColumn<EvaluationNode, String> nameColumn,
                                     TreeTableColumn<EvaluationNode, EvaluationNode> actionsColumn,
                                     TreeTableColumn<EvaluationNode, Number> maxPointsColumn,
                                     TreeTableColumn<EvaluationNode, EvaluationNode> achievedPointsColumn,
                                     Function<Number, String> pointFormatter,
                                     String[] rowStyleClasses,
                                     Function<EvaluationNode, String> rowStyleResolver,
                                     Consumer<EvaluationNode> playCommandHandler,
                                     BiConsumer<EvaluationNode, CheckBox> checkboxHandler,
                                     BiConsumer<EvaluationNode, CheckBox> checkboxStateUpdater,
                                     BooleanSupplier repositoryReadySupplier) {
        this.evaluationTreeTable = evaluationTreeTable;
        this.nameColumn = nameColumn;
        this.actionsColumn = actionsColumn;
        this.maxPointsColumn = maxPointsColumn;
        this.achievedPointsColumn = achievedPointsColumn;
        this.pointFormatter = pointFormatter;
        this.rowStyleClasses = rowStyleClasses;
        this.rowStyleResolver = rowStyleResolver;
        this.playCommandHandler = playCommandHandler;
        this.checkboxHandler = checkboxHandler;
        this.checkboxStateUpdater = checkboxStateUpdater;
        this.repositoryReadySupplier = repositoryReadySupplier;
    }

    public void initialize() {
        nameColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getName()));
        actionsColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue()));
        actionsColumn.setCellFactory(column -> new ActionCell());

        maxPointsColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue().getMaxPoints()));
        maxPointsColumn.setCellFactory(column -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(pointFormatter.apply(item));
                }
            }
        });

        achievedPointsColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue()));
        achievedPointsColumn.setCellFactory(column -> new PointsCell());
        nameColumn.setSortable(false);
        actionsColumn.setSortable(false);
        maxPointsColumn.setSortable(false);
        achievedPointsColumn.setSortable(false);

        evaluationTreeTable.setEditable(true);
        evaluationTreeTable.setShowRoot(false);
        evaluationTreeTable.setSortPolicy(tree -> false);
        evaluationTreeTable.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        evaluationTreeTable.setRowFactory(table -> new TreeTableRow<>() {
            private final javafx.beans.value.ChangeListener<EvaluationStatus> statusListener =
                    (obs, oldStatus, newStatus) -> applyStyle(getItem());
            private final javafx.beans.value.ChangeListener<Boolean> manualPointsListener =
                    (obs, oldValue, newValue) -> applyStyle(getItem());

            @Override
            protected void updateItem(EvaluationNode node, boolean empty) {
                EvaluationNode previous = getItem();
                if (previous != null) {
                    previous.statusProperty().removeListener(statusListener);
                    previous.achievedPointsDefinedProperty().removeListener(manualPointsListener);
                }
                super.updateItem(node, empty);
                ObservableList<String> classes = getStyleClass();
                classes.removeAll(rowStyleClasses);
                if (empty || node == null) {
                    return;
                }
                node.statusProperty().addListener(statusListener);
                node.achievedPointsDefinedProperty().addListener(manualPointsListener);
                applyStyle(node);
            }

            private void applyStyle(EvaluationNode node) {
                ObservableList<String> classes = getStyleClass();
                classes.removeAll(rowStyleClasses);
                if (node == null) {
                    return;
                }
                String styleClass = rowStyleResolver.apply(node);
                if (styleClass != null) {
                    classes.add(styleClass);
                }
            }
        });
    }

    private boolean hasExecutableCommands(EvaluationNode node) {
        if (node == null) {
            return false;
        }
        if (!node.getCommands().isEmpty()) {
            return true;
        }
        for (EvaluationNode child : node.getChildren()) {
            if (hasExecutableCommands(child)) {
                return true;
            }
        }
        return false;
    }

    private class ActionCell extends TreeTableCell<EvaluationNode, EvaluationNode> {
        private final Button playButton = new Button(">");
        private final CheckBox completeBox = new CheckBox();
        private final HBox container = new HBox(8, playButton, completeBox);

        private ActionCell() {
            completeBox.setAllowIndeterminate(true);
            playButton.setOnAction(event -> {
                EvaluationNode node = getItem();
                if (node != null) {
                    playCommandHandler.accept(node);
                }
            });
            completeBox.setOnAction(event -> {
                EvaluationNode node = getItem();
                if (node != null) {
                    checkboxHandler.accept(node, completeBox);
                }
            });
            HBox.setHgrow(playButton, Priority.NEVER);
            HBox.setHgrow(completeBox, Priority.ALWAYS);
        }

        @Override
        protected void updateItem(EvaluationNode node, boolean empty) {
            super.updateItem(node, empty);
            if (empty || node == null) {
                setGraphic(null);
                return;
            }
            playButton.setDisable(!repositoryReadySupplier.getAsBoolean() || !hasExecutableCommands(node));
            checkboxStateUpdater.accept(node, completeBox);
            setGraphic(container);
        }
    }

    private class PointsCell extends TreeTableCell<EvaluationNode, EvaluationNode> {
        private final Spinner<Double> spinner = new Spinner<>();
        private boolean updating;

        private PointsCell() {
            spinner.setEditable(true);
            spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (updating) {
                    return;
                }
                EvaluationNode node = getItem();
                if (node != null && node.isLeaf() && newVal != null) {
                    updating = true;
                    node.setAchievedPoints(newVal);
                    evaluationTreeTable.refresh();
                    updating = false;
                }
            });
            spinner.focusedProperty().addListener((obs, oldValue, focused) -> {
                if (!focused) {
                    spinner.increment(0);
                }
            });
        }

        @Override
        protected void updateItem(EvaluationNode node, boolean empty) {
            super.updateItem(node, empty);
            if (empty || node == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            if (node.isLeaf()) {
                updating = true;
                SpinnerValueFactory.DoubleSpinnerValueFactory valueFactory =
                        new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, node.getMaxPoints(), node.getAchievedPoints(), 0.5);
                valueFactory.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(Double value) {
                        if (!node.isAchievedPointsDefined()) {
                            return "-";
                        }
                        return pointFormatter.apply(value);
                    }

                    @Override
                    public Double fromString(String text) {
                        if (text == null) {
                            return valueFactory.getValue();
                        }
                        String trimmed = text.trim();
                        if (trimmed.isEmpty() || "-".equals(trimmed)) {
                            return valueFactory.getValue();
                        }
                        try {
                            String normalised = trimmed.replace(',', '.');
                            double parsed = Double.parseDouble(normalised);
                            if (parsed < valueFactory.getMin()) {
                                return valueFactory.getMin();
                            }
                            if (parsed > valueFactory.getMax()) {
                                return valueFactory.getMax();
                            }
                            return parsed;
                        } catch (NumberFormatException ex) {
                            return valueFactory.getValue();
                        }
                    }
                });
                spinner.setValueFactory(valueFactory);
                if (!node.isAchievedPointsDefined()) {
                    spinner.getEditor().setText("-");
                } else {
                    spinner.getEditor().setText(pointFormatter.apply(node.getAchievedPoints()));
                }
                setGraphic(spinner);
                setText(null);
                updating = false;
            } else {
                setGraphic(null);
                if (node.isAchievedPointsDefined()) {
                    setText(pointFormatter.apply(node.getAchievedPoints()));
                } else {
                    setText("-");
                }
            }
        }
    }
}
