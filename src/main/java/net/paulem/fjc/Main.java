package net.paulem.fjc;

import atlantafx.base.theme.PrimerDark;
import io.github.matyrobbrt.curseforgeapi.CurseForgeAPI;
import javafx.scene.image.Image;
import net.paulem.fjc.gui.browse.BrowsePanel;
import net.paulem.fjc.gui.content.ModsListPanel;
import net.paulem.fjc.flow.ModsJson;
import net.paulem.fjc.utils.JsonUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
import org.kordamp.ikonli.material2.Material2MZ;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.io.File;
import java.io.IOException;
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
    private static final String PREF_TAB = "lastTab";

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

        // -------- TAB 1: search (Modrinth + CurseForge, full width) --------
        BrowsePanel browsePanel = new BrowsePanel(stage);
        Tab searchTab = new Tab("Recherche", FontIcon.of(Material2MZ.SEARCH, 16));
        searchTab.setClosable(false);
        searchTab.setContent(browsePanel);

        // -------- TAB 2: mods.json viewer --------
        VBox modsJsonBox = new VBox(10);
        modsJsonBox.setPadding(new Insets(12, 0, 0, 0));

        HBox modsViewerBox = new HBox(10);
        modsViewerBox.setAlignment(Pos.CENTER_LEFT);
        Label modsJsonLabel = sectionTitle("Mods du modpack", Material2AL.LIST);
        HBox.setHgrow(modsJsonLabel, Priority.ALWAYS);
        modsJsonLabel.setMaxWidth(Double.MAX_VALUE);
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

        Tab modsTab = new Tab("Mods du modpack", FontIcon.of(Material2AL.LIST, 16));
        modsTab.setClosable(false);
        modsTab.setContent(modsJsonBox);

        TabPane tabs = new TabPane(searchTab, modsTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().select(PREFS.getInt(PREF_TAB, 0) == 1 ? modsTab : searchTab);
        tabs.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> PREFS.putInt(PREF_TAB, newV.intValue()));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.getChildren().add(tabs);

        double width = PREFS.getDouble(PREF_WIDTH, 1180);
        double height = PREFS.getDouble(PREF_HEIGHT, 760);
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(Objects.requireNonNull(Main.class.getResource("/assets/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();

        stage.widthProperty().addListener((obs, oldV, newV) -> PREFS.putDouble(PREF_WIDTH, newV.doubleValue()));
        stage.heightProperty().addListener((obs, oldV, newV) -> PREFS.putDouble(PREF_HEIGHT, newV.doubleValue()));
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
