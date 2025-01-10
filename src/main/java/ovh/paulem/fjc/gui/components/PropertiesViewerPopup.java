package ovh.paulem.fjc.gui.components;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

public class PropertiesViewerPopup extends Stage {
    private final VBox dialogVbox;

    public PropertiesViewerPopup(Stage owner) {
        super();
        this.initModality(Modality.APPLICATION_MODAL);
        this.initOwner(owner);
        dialogVbox = new VBox(10);
        dialogVbox.setPadding(new Insets(10));

        Scene dialogScene = new Scene(dialogVbox, 500, 140);
        this.setScene(dialogScene);
    }

    public void showPopup(@NotNull Object object) {
        for (Field field : object.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                dialogVbox.getChildren().add(new Text(field.getName() + " : " + field.get(object)));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        show();
    }
}
