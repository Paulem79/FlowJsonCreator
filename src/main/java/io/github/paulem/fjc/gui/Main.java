package io.github.paulem.fjc.gui;

import atlantafx.base.theme.PrimerDark;
import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import io.github.paulem.fjc.gui.components.PropertiesViewerPopup;
import io.github.paulem.fjc.gui.content.containers.CurseforgeContainer;
import io.github.paulem.fjc.flow.Mod;
import io.github.paulem.fjc.flow.ModsJson;
import io.github.paulem.fjc.gui.content.SearchType;
import io.github.paulem.fjc.gui.content.containers.ModrinthContainer;
import io.github.paulem.fjc.gui.content.containers.UrlContainer;
import io.github.paulem.fjc.utils.CFUtils;
import io.github.paulem.fjc.utils.ModrinthUtils;
import io.github.paulem.modrinthapi.Modrinth;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.jetbrains.annotations.Nullable;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static io.github.paulem.fjc.utils.JsonUtils.*;
import static io.github.paulem.fjc.utils.ManipulationUtils.checkOptArg;

public class Main extends Application {
    public static @Nullable String CF_API_KEY;

    public static final Modrinth MODRINTH = new Modrinth(null, "paulem", "FlowJsonCreator", "1.3");

    public static ModsJson jsonContent;

    public static ListView<String> list;
    public static CurseForgeAPI cfApi = null;

    public GridPane mainGrid;
    public GridPane subGrid;

    public ComboBox<String> searchType;
    public @Nullable String oldSearchValue;

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        stage.setTitle("FlowJsonCreator v1.3");

        mainGrid = new GridPane();
        mainGrid.setAlignment(Pos.CENTER);
        mainGrid.setHgap(10);
        mainGrid.setVgap(10);
        mainGrid.setPadding(new Insets(25));

        subGrid = new GridPane();
        subGrid.setPadding(new Insets(0, 10, 0, 10));
        mainGrid.add(subGrid, 0, 2);

        Text title = new Text("FlowJsonCreator");
        title.setFont(Font.font("Tahoma", FontWeight.NORMAL, 20));
        mainGrid.add(title, 0, 0, 2, 1);

        // -------- SEARCH --------
        HBox searchBox = new HBox(10);
        mainGrid.add(searchBox, 0, 1, 2, 1);

        Label searchLabel = new Label("Rechercher avec :");
        searchLabel.setTranslateY(5);
        searchBox.getChildren().add(searchLabel);

        // Search type chooser
        searchType = new ComboBox<>();

        List<String> searchTypeWords = Arrays.stream(SearchType.values()).map(SearchType::toWord).toList();
        searchType.getItems().addAll(searchTypeWords);
        searchBox.getChildren().add(searchType);
        // -------- END SEARCH --------

        // -------- Mods.json viewer --------
        VBox modsJsonBox = new VBox(10);
        mainGrid.add(modsJsonBox, 2, 0, 2, 3);

        Label modsJsonLabel = new Label("Mods.json");
        modsJsonLabel.setTranslateY(5);
        modsJsonBox.getChildren().add(modsJsonLabel);

        list = new ListView<>();
        modsJsonBox.getChildren().add(list);

        updateList();
        // -------- END Mods.json viewer --------

        Scene scene = new Scene(mainGrid, 750, 480);
        stage.setScene(scene);

        stage.show();

        // -------- EVENTS --------
        searchType.setOnAction(event -> {
            if(searchType.getValue().equals(oldSearchValue)) return;

            switch (SearchType.fromString(searchType.getValue())) {
                case URL -> {
                    new UrlContainer(stage, subGrid);
                }
                case MODRINTH -> {
                    new ModrinthContainer(stage, subGrid);
                }
                case CURSEFORGE -> {
                    new CurseforgeContainer(stage, subGrid);
                }
            }
        });

        list.setOnMouseClicked(mouseEvent -> {
            @Nullable String selectedItem = list.getSelectionModel().getSelectedItem();
            if(selectedItem == null) return;

            // Double click to remove item
            if(mouseEvent.getButton().equals(MouseButton.PRIMARY)
                    && mouseEvent.getClickCount() == 2){

                list.getItems().remove(selectedItem);
                removeMod(selectedItem);
            } else if(mouseEvent.getButton().equals(MouseButton.SECONDARY)) {

                Mod mod = getModFromString(selectedItem);
                if(mod == null) return;
                new PropertiesViewerPopup(stage)
                        .showPopup(mod);
            }
        });
        // -------- END EVENTS --------
    }

    /**
     * Update the list view with the current mods.
     */
    public static void updateList() {
        ObservableList<String> items = FXCollections.observableArrayList();

        jsonContent.mods.forEach(mod -> items.add(mod.name()));
        jsonContent.curseFiles.forEach(mod -> items.add("CF " + CFUtils.getModFromId(mod.projectID()).name() + " - " + mod.projectID() + " - " + mod.fileID()));
        jsonContent.modrinthMods.forEach(mod -> items.add("MOD " + ModrinthUtils.getModFromSlug(mod.getProjectReference()).title() + " - " + mod.getProjectReference() + " - " + mod.getVersionNumber()));

        Platform.runLater(() -> list.setItems(items));
    }

    public static void main(String[] args) throws IOException {
        createJsonFile();
        jsonContent = getJsonContent();

        OptionParser parser = new OptionParser();
        parser.accepts("cfKey").withOptionalArg().ofType(String.class);
        OptionSet options = parser.parse(args);

        CF_API_KEY = Objects.requireNonNullElse(checkOptArg(options, "cfKey"), "$2a$10$pEf8ZqqpXN3mWm.nZgjA0.dvobnxeWxPeffkd9dHBEabweZQhvqKi"); // Sorry Flow, I had to do itif(Main.cfApi == null) {
        try {
            cfApi = CurseForgeAPI.builder()
                    .apiKey(CF_API_KEY)
                    .build();
        } catch (LoginException e) {
            throw new RuntimeException(e);
        }

        launch(args);
    }

    @Override
    public void stop() throws Exception {
        saveFile(jsonContent);

        super.stop();
    }
}