package net.paulem.fjc.gui.components;

import com.nativejavafx.taskbar.TaskbarProgressbar;
import net.paulem.fjc.threads.NotifierThread;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadProgressBar {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 70;

    private Stage popupStage;
    private ProgressBar progressBar;
    private Label percentLabel;
    private final String downloadUrl;
    private final File file;
    private final Runnable onFinish;
    private volatile IOException downloadError;

    public DownloadProgressBar(String downloadUrl, File file, Runnable onFinish) {
        this.downloadUrl = downloadUrl;
        this.file = file;
        this.onFinish = onFinish;
        showPopup();
    }

    public void showPopup() {
        popupStage = new Stage();
        popupStage.initModality(Modality.NONE);

        progressBar = new ProgressBar(0);
        progressBar.setMinWidth(WIDTH - 20);

        percentLabel = new Label("Démarrage du téléchargement...");
        percentLabel.getStyleClass().add("text-muted");

        HBox titleBox = new HBox(8, FontIcon.of(Material2AL.CLOUD_DOWNLOAD, 16), new Label("Téléchargement du mod"));
        titleBox.setAlignment(Pos.CENTER_LEFT);

        VBox vbox = new VBox(8, titleBox, progressBar, percentLabel);
        vbox.setPadding(new Insets(12));
        Scene scene = new Scene(vbox, WIDTH, HEIGHT);

        popupStage.setScene(scene);
        popupStage.setTitle("Téléchargement...");
        popupStage.show();

        NotifierThread downloadThread = new NotifierThread() {
            @Override
            public void doRun() {
                downloadFile();
            }
        };
        downloadThread.addListener(() -> {
            Platform.runLater(() -> {
                if(TaskbarProgressbar.isSupported())
                    TaskbarProgressbar.stopProgress(popupStage);
                popupStage.close();

                if (downloadError != null) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Erreur");
                    alert.setHeaderText("Le téléchargement a échoué");
                    alert.setContentText(downloadError.getMessage());
                    alert.showAndWait();
                } else {
                    onFinish.run();
                }
            });
        });
        downloadThread.start();
    }

    private void downloadFile() {
        try {
            URL url = new URL(downloadUrl);
            HttpURLConnection httpConnection = (HttpURLConnection) (url.openConnection());
            long completeFileSize = httpConnection.getContentLength();

            java.io.BufferedInputStream in = new java.io.BufferedInputStream(httpConnection.getInputStream());
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            java.io.BufferedOutputStream bout = new BufferedOutputStream(fos, 1024);
            byte[] data = new byte[1024];
            long downloadedFileSize = 0;
            int x;

            boolean knownSize = completeFileSize > 0;
            AtomicInteger progressBarUpdate = new AtomicInteger();
            while ((x = in.read(data, 0, 1024)) >= 0) {
                downloadedFileSize += x;
                final long downloadedSoFar = downloadedFileSize;

                // calculate progress
                final double currentProgress = knownSize ? (double) downloadedFileSize / completeFileSize : -1;

                // update progress bar
                Platform.runLater(() -> {
                    progressBar.setProgress(currentProgress);
                    percentLabel.setText(knownSize
                            ? String.format("%.0f %% (%s / %s)", currentProgress * 100, formatSize(downloadedSoFar), formatSize(completeFileSize))
                            : formatSize(downloadedSoFar) + " téléchargés...");

                    progressBarUpdate.getAndIncrement();
                    if(progressBarUpdate.get() == 5 && TaskbarProgressbar.isSupported()) {
                        TaskbarProgressbar.showCustomProgress(popupStage, Math.max(currentProgress, 0), TaskbarProgressbar.Type.NORMAL);
                        progressBarUpdate.set(0);
                    }
                });

                bout.write(data, 0, x);
            }
            bout.close();
            in.close();
        } catch (IOException e) {
            downloadError = e;
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " o";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f Ko", kb);
        return String.format("%.1f Mo", kb / 1024.0);
    }
}