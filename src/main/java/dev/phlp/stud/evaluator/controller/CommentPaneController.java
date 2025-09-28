package dev.phlp.stud.evaluator.controller;

import java.util.Objects;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

import dev.phlp.stud.evaluator.core.events.EventBus;
import dev.phlp.stud.evaluator.events.EvaluationNodeSelected;
import dev.phlp.stud.evaluator.model.EvaluationNode;

public final class CommentPaneController {
    private final EventBus events;

    @FXML
    private TextArea commentTextArea;

    private EvaluationNode activeNode;
    private ChangeListener<String> commentListener;
    private AutoCloseable selectionSubscription;
    private boolean updating;

    public CommentPaneController(EventBus events) {
        this.events = Objects.requireNonNull(events, "EventBus must not be null");
    }

    @FXML
    public void initialize() {
        commentTextArea.setDisable(true);
        commentTextArea.setText("");
        commentListener = (obs, oldVal, newVal) -> updateEditor(newVal);
        commentTextArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (updating || activeNode == null) {
                return;
            }
            updating = true;
            activeNode.setComment(newVal);
            updating = false;
        });
        selectionSubscription = events.subscribe(EvaluationNodeSelected.class, this::handleSelectionEvent);
    }

    private void handleSelectionEvent(EvaluationNodeSelected event) {
        Runnable task = () -> updateSelection(event.node());
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    private void updateSelection(EvaluationNode node) {
        if (activeNode != null && commentListener != null) {
            activeNode.commentProperty().removeListener(commentListener);
        }
        activeNode = node;
        if (activeNode == null) {
            clearEditor();
            return;
        }
        activeNode.commentProperty().addListener(commentListener);
        updateEditor(activeNode.getComment());
        commentTextArea.setDisable(false);
    }

    private void updateEditor(String value) {
        if (updating) {
            return;
        }
        updating = true;
        commentTextArea.setText(
                value != null ?
                value :
                "");
        updating = false;
    }

    private void clearEditor() {
        updating = true;
        commentTextArea.clear();
        commentTextArea.setDisable(true);
        updating = false;
    }

    public void shutdown() {
        if (selectionSubscription != null) {
            try {
                selectionSubscription.close();
            } catch (Exception ignored) {
                // ignore shutdown errors
            }
            selectionSubscription = null;
        }
        if (activeNode != null && commentListener != null) {
            activeNode.commentProperty().removeListener(commentListener);
        }
    }
}
