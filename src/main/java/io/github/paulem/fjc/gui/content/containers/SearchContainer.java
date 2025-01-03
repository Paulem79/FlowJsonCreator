package io.github.paulem.fjc.gui.content.containers;

import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Abstract base class for creating containers that manage a GridPane and are used to add a Mod.
 */
public abstract class SearchContainer {
    private final Stage stage;
    private final GridPane subGrid;

    private final HBox hbBtn;

    public SearchContainer(Stage stage, GridPane subGrid) {
        subGrid.getChildren().clear();

        this.stage = stage;
        this.subGrid = subGrid;

        this.hbBtn = new HBox(10);

        show();
    }

    public abstract void show();

    /**
     * Adds a search button to the grid
     * @param columnIndex the column index
     * @param rowIndex the row index
     */
    protected void addSearchButton(int columnIndex, int rowIndex) {
        addSearchButton(columnIndex, rowIndex, 1, 1, Pos.BASELINE_LEFT, 0);
    }

    /**
     * Adds a search button to the grid
     * @param columnIndex the column index
     * @param rowIndex the row index
     */
    protected void addSearchButton(int columnIndex, int rowIndex, int columnSpan, int rowSpan, Pos alignment, int translateY) {
        Button btn = new Button("Rechercher");

        hbBtn.setAlignment(alignment);
        hbBtn.setTranslateY(translateY);
        hbBtn.getChildren().add(btn);

        subGrid.add(hbBtn, columnIndex, rowIndex, columnSpan, rowSpan);

        btn.setOnAction(this::finishButtonAction);
    }

    /**
     * Adds a finish button to the grid
     * @param columnIndex the column index
     * @param rowIndex the row index
     */
    protected void addFinishButton(int columnIndex, int rowIndex) {
        addFinishButton(columnIndex, rowIndex, 1, 1, Pos.BOTTOM_RIGHT, 0);
    }

    /**
     * Adds a finish button to the grid
     * @param columnIndex the column index
     * @param rowIndex the row index
     */
    protected void addFinishButton(int columnIndex, int rowIndex, int columnSpan, int rowSpan, Pos alignment, int translateY) {
        Button btn = new Button("Ajouter");

        hbBtn.setAlignment(alignment);
        hbBtn.setTranslateY(translateY);
        hbBtn.getChildren().add(btn);

        subGrid.add(hbBtn, columnIndex, rowIndex, columnSpan, rowSpan);

        btn.setOnAction(this::finishButtonAction);
    }

    /**
     * Action to perform when the finish button is clicked
     * @param event the event
     */
    protected abstract void finishButtonAction(ActionEvent event);

    public Stage getStage() {
        return stage;
    }

    public GridPane getGrid() {
        return subGrid;
    }

    public HBox getHbBtn() {
        return hbBtn;
    }
}
