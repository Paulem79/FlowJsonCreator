package ovh.paulem.fjc.gui.content.containers;

import io.github.matyrobbrt.curseforgeapi.request.Response;
import io.github.matyrobbrt.curseforgeapi.request.query.ModSearchQuery;
import io.github.matyrobbrt.curseforgeapi.schemas.file.File;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.Mod;
import io.github.matyrobbrt.curseforgeapi.schemas.mod.ModLoaderType;
import io.github.matyrobbrt.curseforgeapi.util.Constants;
import io.github.matyrobbrt.curseforgeapi.util.CurseForgeException;
import ovh.paulem.fjc.flow.SelectState;
import ovh.paulem.fjc.gui.Main;
import ovh.paulem.fjc.gui.components.PropertiesViewerPopup;
import ovh.paulem.fjc.flow.CurseForgeMod;
import ovh.paulem.fjc.utils.CFUtils;
import ovh.paulem.fjc.utils.JsonUtils;
import ovh.paulem.fjc.utils.ManipulationUtils;
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

import java.util.Arrays;
import java.util.List;

public class CurseforgeContainer extends SearchContainer {

    private TextField modNameField;
    private TextField versionField;
    private ComboBox<String> loaderComboBox;
    private ListView<String> list;

    private SelectState selectState;
    private List<File> modFiles;

    public ComboBox<String> versionSelectionComboBox;

    public CurseforgeContainer(Stage stage, GridPane subGrid) {
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
            if(Main.cfApi == null) return;

            @Nullable String selectedItem = list.getSelectionModel().getSelectedItem();
            if(selectedItem == null || selectedItem.equals("Aucun résultat")) return;

            if (mouseEvent.getButton() == MouseButton.PRIMARY && mouseEvent.getClickCount() == 2) {

                if(selectState == SelectState.MOD) {
                    String modId = selectedItem.split(" - ")[1];

                    selectState = SelectState.FILES;

                    try {
                        Response<List<File>> filesResponse = Main.cfApi.getHelper().getModFiles(Integer.parseInt(modId));
                        if(filesResponse.isEmpty() || filesResponse.get().isEmpty()) {
                            list.setItems(FXCollections.observableArrayList("Aucun résultat"));
                        } else {
                            List<File> files = filesResponse.get();
                            files = files.stream()
                                    .filter(version -> {
                                        boolean matchGameVersion = versionField.getText().isEmpty() || version.gameVersions().contains(versionField.getText());
                                        boolean matchLoader = (loaderComboBox.getValue() == null || loaderComboBox.getValue().equalsIgnoreCase("any")) || version.gameVersions().contains(ManipulationUtils.capitalize(loaderComboBox.getValue()));

                                        return matchGameVersion && matchLoader;
                                    })
                                    .toList();
                            modFiles = files;
                            ObservableList<String> items = FXCollections.observableArrayList();

                            for (File file : files) {
                                items.add(file.displayName() + " - " + file.id());
                            }

                            list.setItems(items);
                        }
                    } catch (CurseForgeException e) {
                        throw new RuntimeException(e);
                    }
                } else if(selectState == SelectState.FILES) {
                    String fileId = selectedItem.split(" - ")[1];

                    modFiles.stream()
                            .filter(file -> file.id() == Integer.parseInt(fileId))
                            .findFirst()
                            .ifPresent(file -> {
                                JsonUtils.addMod(new CurseForgeMod(file.modId(), file.id()));
                            });
                }
            } else if(mouseEvent.getButton().equals(MouseButton.SECONDARY)) {

                @Nullable Object object = null;
                if(selectState == SelectState.MOD) {
                    String modId = selectedItem.split(" - ")[1];
                    object = CFUtils.getModFromId(Integer.parseInt(modId));
                } else if(selectState == SelectState.FILES) {
                    object = modFiles.stream()
                            .filter(file -> file.displayName().equals(selectedItem.split(" - ")[0]))
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
        if(Main.cfApi == null) return;

        String modName = modNameField.getText();

        selectState = SelectState.MOD;

        try {
            // Try to get the mod because it's an id
            Mod modFromId = CFUtils.getModFromId(Integer.parseInt(modName));

            if(modFromId == null) {
                searchMod(modName);
                return;
            }

            ObservableList<String> items = FXCollections.observableArrayList();
            items.add(modFromId.name() + " - " + modFromId.id());

            list.setItems(items);
        } catch (NumberFormatException err) {
            searchMod(modName);
        }
    }

    private void searchMod(String modName) {
        if(Main.cfApi == null) return;

        // Search the mod because it's not an id
        ModSearchQuery modSearchQuery = ModSearchQuery.of(Constants.GameIDs.MINECRAFT);
        modSearchQuery.pageSize(50);
        modSearchQuery.searchFilter(modName);
        modSearchQuery.index(0);
        modSearchQuery.classId(6); // Only mods
        modSearchQuery.sortField(ModSearchQuery.SortField.FEATURED);

        String versionFieldText = versionField.getText();
        if(!versionFieldText.isEmpty()) {
            modSearchQuery.gameVersion(versionFieldText);
        }

        String selectedLoader = loaderComboBox.getValue();
        if(selectedLoader != null && !selectedLoader.equalsIgnoreCase("any")) {
            Arrays.stream(ModLoaderType.values())
                    .filter(modLoaderType -> modLoaderType.toString().equals(selectedLoader))
                    .findFirst()
                    .ifPresent(modLoader -> {
                        modSearchQuery.modLoaderTypes(List.of(modLoader));
                    });
        }

        try {
            Response<List<Mod>> searchResponse = Main.cfApi.getHelper().searchMods(modSearchQuery);

            if(searchResponse.isEmpty() || searchResponse.get().isEmpty()) {
                list.setItems(FXCollections.observableArrayList("Aucun résultat"));
            } else {
                List<Mod> mods = searchResponse.get();

                ObservableList<String> items = FXCollections.observableArrayList();

                for (Mod mod : mods) {
                    items.add(mod.name() + " - " + mod.id());
                }

                list.setItems(items);
            }
        } catch (CurseForgeException e) {
            throw new RuntimeException(e);
        }
    }
}