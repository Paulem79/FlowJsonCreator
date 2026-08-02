package net.paulem.fjc;

import atlantafx.base.theme.PrimerDark;
import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import javafx.scene.image.Image;
import net.paulem.fjc.gui.content.ModsListPanel;
import net.paulem.fjc.gui.content.containers.CurseforgeContainer;
import net.paulem.fjc.flow.ModsJson;
import net.paulem.fjc.gui.content.SearchType;
import net.paulem.fjc.gui.content.containers.ModrinthContainer;
import net.paulem.fjc.gui.content.containers.UrlContainer;
import net.paulem.fjc.utils.JsonUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

import static net.paulem.fjc.utils.JsonUtils.*;
import static net.paulem.fjc.utils.ManipulationUtils.checkOptArg;

public class Main extends Application {
    public static @Nullable String CF_API_KEY;

    public static final String VERSION = "1.4.2";
    public static final ovh.paulem.modrinthapi.Modrinth MODRINTH = new ovh.paulem.modrinthapi.Modrinth(null, "paulem", "FlowJsonCreator", VERSION);

    public static ModsJson jsonContent;

    /** The searchable/foldable mods list. Also used by {@link JsonUtils} to keep the UI in sync when mods.json changes. */
    public static ModsListPanel modsListPanel;

    @Nullable
    public static CurseForgeAPI cfApi = null;

    private static final Preferences PREFS = Preferences.userNodeForPackage(Main.class);
    private static final String PREF_WIDTH = "windowWidth";
    private static final String PREF_HEIGHT = "windowHeight";
    private static final String PREF_SEARCH_TYPE = "lastSearchType";

    public GridPane subGrid;

    public ComboBox<String> searchType;
    public @Nullable String oldSearchValue;

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        stage.setTitle("FlowJsonCreator v" + VERSION);
        stage.setFullScreen(false);
        stage.getIcons().add(new Image("assets/icons.png"));

        VBox root = new VBox(16);
        root.setPadding(new Insets(20));

        // -------- HEADER --------
        FontIcon appIcon = FontIcon.of(Material2AL.EXTENSION, 26, Color.web("#5aa9e6"));
        Text title = new Text("FlowJsonCreator");
        title.setFont(Font.font("Tahoma", FontWeight.BOLD, 22));
        Label versionLabel = new Label("v" + VERSION);
        versionLabel.getStyleClass().add("text-muted");
        HBox header = new HBox(10, appIcon, title, versionLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().add(header);
        // -------- END HEADER --------

        HBox mainRow = new HBox(20);

        // -------- LEFT: add a mod --------
        VBox addModBox = new VBox(10);
        addModBox.setPrefWidth(320);
        addModBox.setMinWidth(280);

        Label addModTitle = sectionTitle("Ajouter un mod", Material2AL.ADD_CIRCLE);
        addModBox.getChildren().add(addModTitle);

        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        Label searchLabel = new Label("Source :");
        searchBox.getChildren().add(searchLabel);

        searchType = new ComboBox<>();
        List<String> searchTypeWords = Arrays.stream(SearchType.values()).map(SearchType::toWord).toList();
        searchType.getItems().addAll(searchTypeWords);
        searchType.setCellFactory(lv -> searchTypeCell());
        searchType.setButtonCell(searchTypeCell());
        searchBox.getChildren().add(searchType);
        addModBox.getChildren().add(searchBox);

        subGrid = new GridPane();
        subGrid.setHgap(10);
        subGrid.setVgap(10);
        subGrid.setPadding(new Insets(10, 0, 0, 0));
        addModBox.getChildren().add(subGrid);
        // -------- END LEFT --------

        Separator separator = new Separator(Orientation.VERTICAL);

        // -------- RIGHT: mods.json viewer --------
        VBox modsJsonBox = new VBox(10);
        HBox.setHgrow(modsJsonBox, Priority.ALWAYS);

        HBox modsViewerBox = new HBox(10);
        modsViewerBox.setAlignment(Pos.CENTER_LEFT);
        Label modsJsonLabel = sectionTitle("Mods du modpack", Material2AL.LIST);
        HBox.setHgrow(modsJsonLabel, Priority.ALWAYS);
        modsViewerBox.getChildren().add(modsJsonLabel);

        Button btn = iconButton("Ouvrir le dossier", Material2AL.FOLDER_OPEN);
        modsViewerBox.getChildren().add(btn);
        btn.setOnAction(actionEvent -> {
            try {
                Desktop.getDesktop().open(modsJson.getParentFile());
            } catch (IOException e) {
                showError("Impossible d'ouvrir le dossier", e.getMessage());
            }
        });

        Button importBtn = iconButton("Importer un manifest", Material2AL.CLOUD_UPLOAD);
        modsViewerBox.getChildren().add(importBtn);
        importBtn.setOnAction(actionEvent -> onImportManifest(stage));

        modsJsonBox.getChildren().add(modsViewerBox);

        modsListPanel = new ModsListPanel(stage);
        VBox.setVgrow(modsListPanel, Priority.ALWAYS);
        modsJsonBox.getChildren().add(modsListPanel);
        modsListPanel.loadInitial(jsonContent);
        // -------- END RIGHT --------

        mainRow.getChildren().addAll(addModBox, separator, modsJsonBox);
        HBox.setHgrow(mainRow, Priority.ALWAYS);
        VBox.setVgrow(mainRow, Priority.ALWAYS);
        root.getChildren().add(mainRow);

        double width = PREFS.getDouble(PREF_WIDTH, 980);
        double height = PREFS.getDouble(PREF_HEIGHT, 620);
        Scene scene = new Scene(root, width, height);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();

        stage.widthProperty().addListener((obs, oldV, newV) -> PREFS.putDouble(PREF_WIDTH, newV.doubleValue()));
        stage.heightProperty().addListener((obs, oldV, newV) -> PREFS.putDouble(PREF_HEIGHT, newV.doubleValue()));

        // -------- EVENTS --------
        searchType.setOnAction(event -> {
            String value = searchType.getValue();
            if (value == null || value.equals(oldSearchValue)) return;
            selectSearchType(stage, value);
        });

        String lastType = PREFS.get(PREF_SEARCH_TYPE, SearchType.MODRINTH.toWord());
        if (!searchTypeWords.contains(lastType)) lastType = SearchType.MODRINTH.toWord();
        searchType.setValue(lastType);
        selectSearchType(stage, lastType);
        // -------- END EVENTS --------
    }

    private void selectSearchType(Stage stage, String value) {
        oldSearchValue = value;
        PREFS.put(PREF_SEARCH_TYPE, value);

        switch (SearchType.fromString(value)) {
            case URL -> new UrlContainer(stage, subGrid);
            case MODRINTH -> new ModrinthContainer(stage, subGrid);
            case CURSEFORGE -> new CurseforgeContainer(stage, subGrid);
        }
    }

    private static Label sectionTitle(String text, Ikon icon) {
        Label label = new Label(text, FontIcon.of(icon, 16));
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 1.05em;");
        return label;
    }

    private static Button iconButton(String text, Ikon icon) {
        Button button = new Button(text, FontIcon.of(icon, 14));
        return button;
    }

    private static ListCell<String> searchTypeCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(item);
                SearchType type = SearchType.fromString(item);
                setGraphic(FontIcon.of(type.toCategory().getIcon(), 14, type.toCategory().getColor()));
            }
        };
    }

    private void onImportManifest(Stage stage) {
        FileDialog fileChooser = new FileDialog((Frame) null);
        fileChooser.setTitle("Sélectionner un manifest.json");
        fileChooser.setFilenameFilter((dir, name) -> name.equals("manifest.json"));
        fileChooser.setVisible(true);
        String directory = fileChooser.getDirectory();
        String file = fileChooser.getFile();
        if (directory == null || file == null) return; // annulé par l'utilisateur

        File selectedFile = new File(directory, file);
        try {
            addCurseForgeManifest(selectedFile);
        } catch (IOException e) {
            showError("Erreur lors de l'importation du manifest", e.getMessage());
        }
    }

    private static void showError(String header, @Nullable String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(header);
        alert.setContentText(message != null ? message : "Erreur inconnue.");
        alert.showAndWait();
    }

    /**
     * Handle any uncaught exception (JavaFX thread or background thread) by showing
     * an alert instead of letting the whole application crash.
     */
    private static void handleUncaught(Thread thread, Throwable throwable) {
        System.err.println("Erreur non interceptée sur le thread " + thread.getName());
        throwable.printStackTrace(System.err);

        Runnable showAlert = () -> showError("Une erreur est survenue, mais l'application continue de fonctionner",
                throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName());

        if (Platform.isFxApplicationThread()) {
            showAlert.run();
        } else {
            Platform.runLater(showAlert);
        }
    }

    public static void main(String[] args) throws IOException {
        Thread.setDefaultUncaughtExceptionHandler(Main::handleUncaught);

        createJsonFile();
        jsonContent = getJsonContent();

        OptionParser parser = new OptionParser();
        parser.accepts("cfKey").withOptionalArg().ofType(String.class);
        OptionSet options = parser.parse(args);

        CF_API_KEY = Objects.requireNonNullElse(checkOptArg(options, "cfKey"), "$2a$10$pEf8ZqqpXN3mWm.nZgjA0.dvobnxeWxPeffkd9dHBEabweZQhvqKi"); // Sorry Flow, I had to do it
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

        if (modsListPanel != null) modsListPanel.shutdown();

        super.stop();
    }
}
