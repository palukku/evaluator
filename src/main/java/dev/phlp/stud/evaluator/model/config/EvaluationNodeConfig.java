package dev.phlp.stud.evaluator.model.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluationNodeConfig {
    @JsonProperty(required = true)
    private String name;

    @JsonProperty(required = true)
    private double maxPoints;

    private List<String> commands = new ArrayList<>();

    private String comment;

    private boolean pseudo;

    private List<EvaluationNodeConfig> children = new ArrayList<>();

    public EvaluationNodeConfig() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(double maxPoints) {
        this.maxPoints = maxPoints;
    }

    public List<String> getCommands() {
        return commands;
    }

    public void setCommands(List<String> commands) {
        this.commands =
                commands != null ?
                commands :
                new ArrayList<>();
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isPseudo() {
        return pseudo;
    }

    public void setPseudo(boolean pseudo) {
        this.pseudo = pseudo;
    }

    public List<EvaluationNodeConfig> getChildren() {
        return children;
    }

    public void setChildren(List<EvaluationNodeConfig> children) {
        this.children =
                children != null ?
                children :
                new ArrayList<>();
    }
}
