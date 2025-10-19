package net.paulem.fjc.gui.content.containers;

import io.github.matyrobbrt.curseforgeapi.util.Utils;
import net.paulem.fjc.flow.mod.ModrinthMod;
import net.paulem.fjc.flow.SelectState;
import net.paulem.fjc.Main;
import net.paulem.fjc.gui.components.PropertiesViewerPopup;
import net.paulem.fjc.utils.JsonUtils;
import net.paulem.fjc.utils.ManipulationUtils;
import net.paulem.fjc.utils.ModrinthUtils;
import ovh.paulem.modrinthapi.types.project.Project;
import ovh.paulem.modrinthapi.types.project.ProjectResult;
import ovh.paulem.modrinthapi.types.project.SearchProject;
import ovh.paulem.modrinthapi.types.version.ListVersions;
import ovh.paulem.modrinthapi.types.version.Version;
import ovh.paulem.modrinthapi.types.version.VersionFile;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

public class ModrinthContainer extends SearchContainer {
    private TextField modNameField;
    private TextField versionField;
    private ComboBox<String> loaderComboBox;
    private ListView<String> list;

    private SelectState selectState;
    private List<Version> modFiles;

    public ComboBox<String> versionSelectionComboBox;

    public ModrinthContainer(Stage stage, GridPane subGrid) {
        super(stage, subGrid);
    }

    @Override
    public void show() {
        // ------- NAME -------
        HBox modNameBox = new HBox(10);
        modNameBox.setPadding(new Insets(0, 0, 10, 0));
        getGrid().add(modNameBox, 0, 0, 2, 1);

        Label modNameLabel = new Label("Nom :");
        modNameLabel.setTranslateY(5);
        modNameBox.getChildren().add(modNameLabel);

        modNameField = new TextField();
        modNameBox.getChildren().add(modNameField);
        // ------- END NAME -------

        // ------- VERSION -------
        HBox versionBox = new HBox(10);
        versionBox.setPadding(new Insets(0, 0, 10, 0));
        getGrid().add(versionBox, 0, 1, 2, 1);

        Label versionLabel = new Label("Version :");
        versionLabel.setTranslateY(5);
        versionBox.getChildren().add(versionLabel);

        versionField = new TextField();
        versionBox.getChildren().add(versionField);
        // ------- END VERSION -------

        // ------- LOADER -------
        HBox loaderBox = new HBox(10);
        loaderBox.setPadding(new Insets(0, 0, 10, 0));
        getGrid().add(loaderBox, 0, 2, 2, 1);

        Label loaderLabel = new Label("Loader :");
        loaderLabel.setTranslateY(5);
        loaderBox.getChildren().add(loaderLabel);

        loaderComboBox = new ComboBox<>();

        loaderComboBox.getItems().addAll(ManipulationUtils.getModLoaders());
        loaderBox.getChildren().add(loaderComboBox);
        // ------- END LOADER -------

        list = new ListView<>();
        getGrid().add(list, 0, 4);

        addSearchButton(0, 3);

        list.setOnMouseClicked(mouseEvent -> {
            @Nullable String selectedItem = list.getSelectionModel().getSelectedItem();
            if(selectedItem == null) return;
            String[] split = selectedItem.split(" - ");

            if (mouseEvent.getButton() == MouseButton.PRIMARY && mouseEvent.getClickCount() == 2) {

                if(selectState == SelectState.MOD) {
                    String modId = split[split.length - 1];

                    selectState = SelectState.FILES;

                    try {
                        ListVersions listVersions = Main.MODRINTH.listVersions(modId);

                        List<Version> versions = listVersions.versions()
                                .stream()
                                .filter(version -> {
                                    boolean matchGameVersion = versionField.getText().isEmpty() || version.gameVersions().contains(versionField.getText());
                                    boolean matchLoader = (loaderComboBox.getValue() == null || loaderComboBox.getValue().equalsIgnoreCase("any")) || version.loaders().contains(loaderComboBox.getValue().toLowerCase());

                                    return matchGameVersion && matchLoader;
                                })
                                .toList();
                        modFiles = versions;
                        ObservableList<String> items = FXCollections.observableArrayList();

                        for (Version version : versions) {
                            VersionFile primaryFile = version.files()
                                    .stream().filter(VersionFile::primary).findFirst()
                                    .orElse(version.files().get(0));
                            items.add(primaryFile.filename() + " - " + version.id());
                        }

                        list.setItems(items);
                    } catch (URISyntaxException | IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if(selectState == SelectState.FILES) {
                    String versionId = split[split.length - 1];

                    modFiles.stream()
                            .filter(version -> version.id().equals(versionId))
                            .findFirst()
                            .ifPresent(version -> {
                                JsonUtils.addMod(new ModrinthMod(version.projectId(), version.versionNumber(), version.id()));
                            });
                }
            } else if(mouseEvent.getButton().equals(MouseButton.SECONDARY)) {

                @Nullable Object object = null;
                if(selectState == SelectState.MOD) {
                    String modSlug = split[split.length - 1];
                    object = ModrinthUtils.getModFromSlug(modSlug);
                } else if(selectState == SelectState.FILES) {
                    object = modFiles.stream()
                            .filter(version -> {
                                VersionFile primaryFile = version.files().stream().filter(VersionFile::primary).findFirst().orElse(version.files().get(0));
                                // All except the last part
                                return primaryFile.filename().equals(String.join(" - ", Arrays.copyOf(split, split.length - 1)));
                            })
                            .findFirst()
                            .orElse(null);
                }

                if(object == null) return;
                new PropertiesViewerPopup(getStage())
                        .showPopup(object);
            }
        });
    }

    @Override
    protected void finishButtonAction(ActionEvent event) {
        String modName = modNameField.getText();

        selectState = SelectState.MOD;

        try {
            // Try to get the mod because it's an id
            Project mod = ModrinthUtils.getModFromSlug(modName);

            if(mod == null) {
                searchMod(modName);
                return;
            }

            ObservableList<String> items = FXCollections.observableArrayList();
            items.add(mod.title() + " - " + mod.id());

            list.setItems(items);
        } catch (NumberFormatException err) {
            searchMod(modName);
        }
    }

    private void searchMod(String modName) {
        String facet = "[[\"project_type:mod\"]";

        String versionFieldText = versionField.getText();
        if(!versionFieldText.isEmpty()) {
            facet += ",[\"versions:" + versionFieldText + "\"]";
        }

        String selectedLoader = loaderComboBox.getValue();
        if(selectedLoader != null && !selectedLoader.equalsIgnoreCase("any")) {
            facet += ",[\"categories:" + selectedLoader.toLowerCase() + "\"]";
        }

        facet += "]";

        try {
            SearchProject searchProject = Main.MODRINTH.searchProject(Utils.encodeURL(modName), Utils.encodeURL(facet), "relevance", 0, 100);

            ObservableList<String> items = FXCollections.observableArrayList();

            for (ProjectResult mod : searchProject.hits()) {
                items.add(mod.title() + " - " + mod.projectId());
            }

            list.setItems(items);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}