package dev.phlp.stud.evaluator.model.config;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluationConfig {
    private String title;

    private String repositoryUrlTemplate;

    private String repositoryNumberPlaceholder = "{{number}}";

    private String tag;

    private LocalDate deadline;

    @JsonProperty("categories")
    private List<EvaluationNodeConfig> rootCategories = new ArrayList<>();

    public EvaluationConfig() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRepositoryUrlTemplate() {
        return repositoryUrlTemplate;
    }

    public void setRepositoryUrlTemplate(String repositoryUrlTemplate) {
        this.repositoryUrlTemplate = repositoryUrlTemplate;
    }

    public String getRepositoryNumberPlaceholder() {
        return repositoryNumberPlaceholder;
    }

    public void setRepositoryNumberPlaceholder(String repositoryNumberPlaceholder) {
        this.repositoryNumberPlaceholder = repositoryNumberPlaceholder;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public List<EvaluationNodeConfig> getRootCategories() {
        return rootCategories;
    }

    public void setRootCategories(List<EvaluationNodeConfig> rootCategories) {
        this.rootCategories =
                rootCategories != null ?
                rootCategories :
                new ArrayList<>();
    }
}
