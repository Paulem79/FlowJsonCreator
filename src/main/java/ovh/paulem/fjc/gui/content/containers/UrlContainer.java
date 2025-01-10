package ovh.paulem.fjc.gui.content.containers;

import ovh.paulem.fjc.utils.JsonUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

import static ovh.paulem.fjc.utils.FileUtils.getDownloadedMod;

/**
 * A concrete implementation of the {@link SearchContainer} class that provides functionality for adding
 * a mod by specifying a URL.
 */
public class UrlContainer extends SearchContainer {
    private TextField urlField;

    public UrlContainer(Stage stage, GridPane subGrid) {
        super(stage, subGrid);
    }

    @Override
    public void show() {
        Label urlLabel = new Label("URL du fichier jar :");
        getGrid().add(urlLabel, 0, 0);

        urlField = new TextField();
        getHbBtn().getChildren().add(urlField);

        addFinishButton(0, 1, 2, 1, Pos.CENTER, 10);
    }

    @Override
    protected void finishButtonAction(ActionEvent event) {
        String jarUrl = urlField.getText();
        String[] split = jarUrl.split("/");

        try {
            String fileName = URLDecoder.decode(split[split.length-1].replace("+", "%2B"), StandardCharsets.UTF_8)
                    .replace("%2B", "+");
            getDownloadedMod(jarUrl, fileName, mod -> {
                JsonUtils.addMod(mod);
                Platform.runLater(() -> urlField.clear());
            });
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
