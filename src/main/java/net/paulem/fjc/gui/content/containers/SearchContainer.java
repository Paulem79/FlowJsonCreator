package net.paulem.fjc.gui.content.containers;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Abstract base class for creating containers that manage a GridPane and are used to add a Mod.
 */
public abstract class SearchContainer {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "fjc-search-worker");
        t.setDaemon(true);
        return t;
    });

    private final Stage stage;
    private final GridPane subGrid;

    private final HBox hbBtn;
    private Button actionButton;

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
        addButton("Rechercher", FontIcon.of(Material2MZ.SEARCH, 14), columnIndex, rowIndex, columnSpan, rowSpan, alignment, translateY);
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
        addButton("Ajouter", FontIcon.of(Material2AL.ADD_CIRCLE, 14), columnIndex, rowIndex, columnSpan, rowSpan, alignment, translateY);
    }

    private void addButton(String text, Node icon, int columnIndex, int rowIndex, int columnSpan, int rowSpan, Pos alignment, int translateY) {
        Button btn = new Button(text, icon);
        this.actionButton = btn;

        hbBtn.setAlignment(alignment);
        hbBtn.setTranslateY(translateY);
        hbBtn.getChildren().add(btn);

        subGrid.add(hbBtn, columnIndex, rowIndex, columnSpan, rowSpan);

        btn.setOnAction(this::finishButtonAction);
    }

    /**
     * Runs network/IO work off the JavaFX thread so the UI never freezes while waiting on
     * CurseForge/Modrinth, showing a wait cursor and disabling the action button meanwhile.
     * The given task is responsible for pushing its results back via {@link Platform#runLater}.
     * @param task the work to run in the background.
     */
    protected void runInBackground(Runnable task) {
        if (actionButton != null) actionButton.setDisable(true);
        stage.getScene().setCursor(Cursor.WAIT);

        EXECUTOR.submit(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Erreur");
                    alert.setHeaderText("La requête a échoué");
                    alert.setContentText(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
                    alert.initOwner(stage);
                    alert.showAndWait();
                });
            } finally {
                Platform.runLater(() -> {
                    if (actionButton != null) actionButton.setDisable(false);
                    stage.getScene().setCursor(Cursor.DEFAULT);
                });
            }
        });
    }

    /** Lets pressing Enter in a text field trigger the same action as the button, for quicker input. */
    protected void submitOnEnter(TextField field) {
        field.setOnAction(this::finishButtonAction);
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
