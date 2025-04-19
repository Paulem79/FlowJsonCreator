package ovh.paulem.fjc;

import atlantafx.base.theme.PrimerDark;
import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import javafx.scene.image.Image;
import ovh.paulem.fjc.gui.components.PropertiesViewerPopup;
import ovh.paulem.fjc.gui.content.containers.CurseforgeContainer;
import ovh.paulem.fjc.flow.Mod;
import ovh.paulem.fjc.flow.ModsJson;
import ovh.paulem.fjc.gui.content.SearchType;
import ovh.paulem.fjc.gui.content.containers.ModrinthContainer;
import ovh.paulem.fjc.gui.content.containers.UrlContainer;
import ovh.paulem.fjc.utils.CFUtils;
import ovh.paulem.fjc.utils.ModrinthUtils;
import ovh.paulem.modrinthapi.Modrinth;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
import ovh.paulem.modrinthapi.types.project.Project;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static ovh.paulem.fjc.utils.JsonUtils.*;
import static ovh.paulem.fjc.utils.ManipulationUtils.checkOptArg;

public class Main extends Application {
    public static @Nullable String CF_API_KEY;

    public static final Modrinth MODRINTH = new Modrinth(null, "paulem", "FlowJsonCreator", "1.3");

    public static ModsJson jsonContent;

    public static ListView<String> list;
    @Nullable
    public static CurseForgeAPI cfApi = null;

    public GridPane mainGrid;
    public GridPane subGrid;

    public ComboBox<String> searchType;
    public @Nullable String oldSearchValue;

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        stage.setTitle("FlowJsonCreator v1.3.2");
        stage.setFullScreen(false);
        stage.centerOnScreen();
        stage.getIcons().add(new Image("assets/icons.png"));

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

        HBox modsViewerBox = new HBox(10);
        modsJsonBox.getChildren().add(modsViewerBox);

        Label modsJsonLabel = new Label("Mods.json");
        modsJsonLabel.setTranslateY(5);
        modsViewerBox.getChildren().add(modsJsonLabel);

        Button btn = new Button("Ouvrir");
        modsViewerBox.getChildren().add(btn);
        btn.setOnAction(actionEvent -> {
            try {
                Desktop.getDesktop().open(modsJson.getParentFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

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

        jsonContent.mods.forEach(mod -> {
            items.add(mod.name());
        });

        jsonContent.curseFiles.forEach(mod -> {
            io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod modFromId = CFUtils.getModFromId(mod.projectID());
            if(modFromId == null) return;

            items.add("CF " + modFromId.name() + " - " + mod.projectID() + " - " + mod.fileID());
        });

        jsonContent.modrinthMods.forEach(mod -> {
            Project modFromSlug = ModrinthUtils.getModFromSlug(mod.getProjectReference());
            if(modFromSlug == null) return;

            items.add("MOD " + modFromSlug.title() + " - " + mod.getProjectReference() + " - " + mod.getVersionNumber());
        });

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
            cfApi = null;
        }

        launch(args);
    }

    @Override
    public void stop() throws Exception {
        saveFile(jsonContent);

        super.stop();
    }
}