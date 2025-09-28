package dev.phlp.stud.evaluator.model.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.phlp.stud.evaluator.model.EvaluationStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeSaveState {
    private double achievedPoints;
    private Boolean achievedPointsDefined;
    private String lastLogFile;
    private EvaluationStatus status;
    private String comment;

    public NodeSaveState() {
    }

    public double getAchievedPoints() {
        return achievedPoints;
    }

    public void setAchievedPoints(double achievedPoints) {
        this.achievedPoints = achievedPoints;
    }

    public Boolean getAchievedPointsDefined() {
        return achievedPointsDefined;
    }

    public void setAchievedPointsDefined(Boolean achievedPointsDefined) {
        this.achievedPointsDefined = achievedPointsDefined;
    }

    public String getLastLogFile() {
        return lastLogFile;
    }

    public void setLastLogFile(String lastLogFile) {
        this.lastLogFile = lastLogFile;
    }

    public EvaluationStatus getStatus() {
        return status;
    }

    public void setStatus(EvaluationStatus status) {
        this.status = status;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
