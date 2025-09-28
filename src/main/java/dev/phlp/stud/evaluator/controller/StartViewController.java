package dev.phlp.stud.evaluator.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;

import dev.phlp.stud.evaluator.EvaluatorApp;

public class StartViewController {
    @FXML
    private Button loadConfigButton;
    @FXML
    private Button openEditorButton;
    @FXML
    private Button exitButton;

    private EvaluatorApp application;

    public void init(EvaluatorApp application) {
        this.application = application;
        registerHandlers();
    }

    private void registerHandlers() {
        loadConfigButton.setOnAction(event -> application.handleLoadConfigurationRequest());
        openEditorButton.setOnAction(event -> application.handleOpenEditorRequest(null, null));
        exitButton.setOnAction(event -> application.shutdownApplication());
    }
}
