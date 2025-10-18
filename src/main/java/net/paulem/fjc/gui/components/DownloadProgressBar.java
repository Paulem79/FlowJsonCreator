package net.paulem.fjc.gui.components;

import com.nativejavafx.taskbar.TaskbarProgressbar;
import net.paulem.fjc.threads.NotifierThread;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadProgressBar {
    private static final int WIDTH = 280;
    private static final int HEIGHT = 30;

    private Stage popupStage;
    private ProgressBar progressBar;
    private final String downloadUrl;
    private final File file;
    private final Runnable onFinish;

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
        progressBar.setMinWidth(WIDTH);
        progressBar.setMinHeight(HEIGHT);
        VBox vbox = new VBox(progressBar);
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
            onFinish.run();
            Platform.runLater(() -> {
                if(TaskbarProgressbar.isSupported())
                    TaskbarProgressbar.stopProgress(popupStage);
                popupStage.close();
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

            AtomicInteger progressBarUpdate = new AtomicInteger();
            while ((x = in.read(data, 0, 1024)) >= 0) {
                downloadedFileSize += x;

                // calculate progress
                final double currentProgress = (double) downloadedFileSize / completeFileSize;

                // update progress bar
                Platform.runLater(() -> {
                    progressBar.setProgress(currentProgress);

                    progressBarUpdate.getAndIncrement();
                    if(progressBarUpdate.get() == 5 && TaskbarProgressbar.isSupported()) {
                        TaskbarProgressbar.showCustomProgress(popupStage, currentProgress, TaskbarProgressbar.Type.NORMAL);
                        progressBarUpdate.set(0);
                    }
                });

                bout.write(data, 0, x);
            }
            bout.close();
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}