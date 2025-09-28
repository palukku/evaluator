package dev.phlp.stud.evaluator.model.state;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluationSaveData {
    private String repositoryUrl;
    private String checkedOutReference;
    private Integer placeholderValue;
    private String evaluationTitle;
    private String checkoutStrategy;
    private Instant savedAt;
    private Map<String, NodeSaveState> nodes = new HashMap<>();

    public EvaluationSaveData() {
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getCheckedOutReference() {
        return checkedOutReference;
    }

    public void setCheckedOutReference(String checkedOutReference) {
        this.checkedOutReference = checkedOutReference;
    }

    public Integer getPlaceholderValue() {
        return placeholderValue;
    }

    public void setPlaceholderValue(Integer placeholderValue) {
        this.placeholderValue = placeholderValue;
    }

    public String getEvaluationTitle() {
        return evaluationTitle;
    }

    public void setEvaluationTitle(String evaluationTitle) {
        this.evaluationTitle = evaluationTitle;
    }

    public String getCheckoutStrategy() {
        return checkoutStrategy;
    }

    public void setCheckoutStrategy(String checkoutStrategy) {
        this.checkoutStrategy = checkoutStrategy;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(Instant savedAt) {
        this.savedAt = savedAt;
    }

    public Map<String, NodeSaveState> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, NodeSaveState> nodes) {
        this.nodes =
                nodes != null ?
                nodes :
                new HashMap<>();
    }
}
