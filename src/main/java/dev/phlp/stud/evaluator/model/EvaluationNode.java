package dev.phlp.stud.evaluator.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

public class EvaluationNode {
    EvaluationNode parent;
    private final String id;
    private final String name;
    private final double maxPoints;
    private final ObservableList<EvaluationNode> children = FXCollections.observableArrayList();
    private final ObservableList<String> commands = FXCollections.observableArrayList();
    private final DoubleProperty achievedPoints = new SimpleDoubleProperty(0.0);
    private final BooleanProperty achievedPointsDefined = new SimpleBooleanProperty(false);
    private final ObjectProperty<EvaluationStatus> status = new SimpleObjectProperty<>(EvaluationStatus.PENDING);
    private final StringProperty comment = new SimpleStringProperty("");

    public EvaluationNode(String id, String name, double maxPoints, List<String> commands) {
        this.id =
                id != null ?
                id :
                UUID.randomUUID().toString();
        this.name = Objects.requireNonNull(name, "name");
        this.maxPoints = maxPoints;
        if (commands != null) {
            this.commands.addAll(commands);
        }

        children.addListener((ListChangeListener<EvaluationNode>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    change.getAddedSubList().forEach(child -> {
                        child.parent = this;
                        child.achievedPointsProperty().addListener((obs, oldVal, newVal) -> refreshAggregatedPoints());
                        child.statusProperty().addListener((obs, oldStatus, newStatus) -> refreshAggregatedStatus());
                    });
                }
                if (change.wasRemoved()) {
                    change.getRemoved().forEach(child -> child.parent = null);
                }
            }
            refreshAggregatedPoints();
            refreshAggregatedStatus();
        });
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getQualifiedName() {
        if (parent == null) {
            return name;
        }
        return parent.getQualifiedName() + "/" + name;
    }

    public double getMaxPoints() {
        if (isLeaf()) {
            return maxPoints;
        }
        return children.stream().mapToDouble(EvaluationNode::getMaxPoints).sum();
    }

    public ObservableList<EvaluationNode> getChildren() {
        return children;
    }

    public ObservableList<String> getCommands() {
        return commands;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public DoubleProperty achievedPointsProperty() {
        return achievedPoints;
    }

    public double getAchievedPoints() {
        return achievedPoints.get();
    }

    public void setAchievedPoints(double points) {
        if (!isLeaf()) {
            achievedPoints.set(children.stream().mapToDouble(EvaluationNode::getAchievedPoints).sum());
        } else {
            achievedPoints.set(Math.max(0.0, Math.min(points, maxPoints)));
            achievedPointsDefined.set(true);
        }
    }

    public ReadOnlyBooleanProperty achievedPointsDefinedProperty() {
        return achievedPointsDefined;
    }

    public boolean isAchievedPointsDefined() {
        if (!isLeaf()) {
            return children.stream().anyMatch(EvaluationNode::isAchievedPointsDefined);
        }
        return achievedPointsDefined.get();
    }

    public void clearAchievedPoints() {
        if (!isLeaf()) {
            children.forEach(EvaluationNode::clearAchievedPoints);
            refreshAggregatedPoints();
            return;
        }
        achievedPoints.set(0.0);
        achievedPointsDefined.set(false);
        if (parent != null) {
            parent.refreshAggregatedPoints();
        }
    }

    public void setAchievedPointsFromStorage(double points, boolean defined) {
        if (!isLeaf()) {
            return;
        }
        achievedPoints.set(Math.max(0.0, Math.min(points, maxPoints)));
        achievedPointsDefined.set(defined);
    }

    public ReadOnlyDoubleProperty achievedPointsReadOnlyProperty() {
        return achievedPoints;
    }

    public ObjectProperty<EvaluationStatus> statusProperty() {
        return status;
    }

    public ReadOnlyObjectProperty<EvaluationStatus> statusReadOnlyProperty() {
        return status;
    }

    public EvaluationStatus getStatus() {
        return status.get();
    }

    public void setStatus(EvaluationStatus newStatus) {
        status.set(
                newStatus != null ?
                newStatus :
                EvaluationStatus.PENDING);
        if (parent != null) {
            parent.refreshAggregatedStatus();
        } else if (!isLeaf()) {
            refreshAggregatedStatus();
        }
    }

    public StringProperty commentProperty() {
        return comment;
    }

    public String getComment() {
        return comment.get();
    }

    public void setComment(String value) {
        comment.set(
                value != null ?
                value :
                "");
    }

    public void setCommentFromStorage(String value) {
        comment.set(
                value != null ?
                value :
                "");
    }

    public void setStatusFromStorage(EvaluationStatus storedStatus) {
        if (storedStatus == null) {
            return;
        }
        status.set(storedStatus);
        if (isLeaf()) {
            if (parent != null) {
                parent.refreshAggregatedStatus();
            }
        } else {
            refreshAggregatedStatus();
        }
    }

    public EvaluationNode getParent() {
        return parent;
    }

    public void addChild(EvaluationNode child) {
        children.add(child);
    }

    public void addChildren(List<EvaluationNode> nodes) {
        children.addAll(nodes);
    }

    public void markFullyAwarded() {
        if (isLeaf()) {
            achievedPoints.set(maxPoints);
            achievedPointsDefined.set(true);
            if (parent != null) {
                parent.refreshAggregatedPoints();
                parent.refreshAggregatedStatus();
            }
            return;
        }
        children.forEach(EvaluationNode::markFullyAwarded);
        refreshAggregatedPoints();
        refreshAggregatedStatus();
    }

    public void resetToZero() {
        if (isLeaf()) {
            achievedPoints.set(0.0);
            achievedPointsDefined.set(false);
            if (parent != null) {
                parent.refreshAggregatedPoints();
                parent.refreshAggregatedStatus();
            }
            return;
        }
        children.forEach(EvaluationNode::resetToZero);
        refreshAggregatedPoints();
        refreshAggregatedStatus();
    }

    public void refreshAggregatedPoints() {
        if (!isLeaf()) {
            achievedPoints.set(children.stream().mapToDouble(EvaluationNode::getAchievedPoints).sum());
        }
        if (parent != null) {
            parent.refreshAggregatedPoints();
        }
    }

    public void refreshAggregatedStatus() {
        if (!isLeaf()) {
            EvaluationStatus aggregated = aggregateChildStatus();
            if (status.get() != aggregated) {
                status.set(aggregated);
            }
        }
        if (parent != null) {
            parent.refreshAggregatedStatus();
        }
    }

    private EvaluationStatus aggregateChildStatus() {
        if (children.isEmpty()) {
            return EvaluationStatus.PENDING;
        }
        boolean anyFailed = false;
        boolean anyRunning = false;
        boolean anyPending = false;
        boolean anyCancelled = false;
        boolean allSuccess = true;
        for (EvaluationNode child : children) {
            EvaluationStatus childStatus = child.getStatus();
            if (childStatus == null) {
                anyPending = true;
                allSuccess = false;
                continue;
            }
            switch (childStatus) {
                case FAILED -> {
                    anyFailed = true;
                    allSuccess = false;
                }
                case RUNNING -> {
                    anyRunning = true;
                    allSuccess = false;
                }
                case PENDING -> {
                    anyPending = true;
                    allSuccess = false;
                }
                case CANCELLED -> {
                    anyCancelled = true;
                    allSuccess = false;
                }
                case SUCCESS -> {
                    // no-op
                }
            }
        }
        if (anyFailed) {
            return EvaluationStatus.FAILED;
        }
        if (anyRunning) {
            return EvaluationStatus.RUNNING;
        }
        if (anyPending) {
            return EvaluationStatus.PENDING;
        }
        if (anyCancelled) {
            return EvaluationStatus.CANCELLED;
        }
        return allSuccess ?
               EvaluationStatus.SUCCESS :
               EvaluationStatus.PENDING;
    }

    public boolean isFullyAwarded() {
        if (isLeaf()) {
            return Double.compare(getAchievedPoints(), maxPoints) == 0;
        }
        return children.stream().allMatch(EvaluationNode::isFullyAwarded);
    }

    public boolean hasPartialAward() {
        if (isLeaf()) {
            return getAchievedPoints() > 0.0 && !isFullyAwarded();
        }
        long fullyAwarded = children.stream().filter(EvaluationNode::isFullyAwarded).count();
        long zeroAwarded = children.stream().filter(child -> Double.compare(child.getAchievedPoints(), 0.0) == 0).count();
        if (fullyAwarded == children.size() || zeroAwarded == children.size()) {
            return false;
        }
        return children.stream().anyMatch(EvaluationNode::hasPartialAward) || (fullyAwarded + zeroAwarded < children.size());
    }

    public double getCompletionRatio() {
        if (Double.compare(getMaxPoints(), 0.0) == 0) {
            return 0.0;
        }
        return getAchievedPoints() / getMaxPoints();
    }
}
