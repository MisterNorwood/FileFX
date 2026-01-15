package org.example;


import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Stream;

public class FileExplorerApp extends Application {

    @Getter
    @Setter
    private static boolean showHidden = false;
    private final StackPane contentArea = new StackPane();
    private final Map<String, FileViewer> viewerRegistry = new HashMap<>();
    private MediaPlayer currentAudioPlayer;

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        registerViewers();

        var root = new SplitPane();
        var treeView = new TreeView<File>();
        var rootItem = createTreeItem(new File(System.getProperty("user.home")));

        treeView.setRoot(rootItem);
        treeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String name = item.getName().isEmpty() ? item.getPath() : item.getName();
                    setText(name);

                    FontIcon icon = new FontIcon(item.isDirectory() ? FontAwesomeSolid.FOLDER : FontAwesomeSolid.FILE);
                    icon.setIconColor(javafx.scene.paint.Color.web(item.isDirectory() ? "#fabd2f" : "#83a598"));
                    setGraphic(icon);
                }
            }
        });
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && newVal.getValue().isFile()) {
                updatePreview(newVal.getValue());
            }
        });

        contentArea.getStyleClass().add("content-area");
        root.getItems().addAll(treeView, contentArea);
        root.setDividerPositions(0.3);

        var scene = new Scene(root, 1100, 750);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());
        scene.setOnKeyPressed(event -> {
            var combo = new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN);
            if (combo.match(event)) {
                showHidden = !showHidden;

                refreshTree(treeView.getRoot());

                System.out.println("Hidden files: " + (showHidden ? "Shown" : "Hidden"));
            }
        });

        stage.setTitle("Gruvbox Explorer");
        stage.setScene(scene);
        stage.show();
    }

    private void refreshTree(TreeItem<File> item) {
        if (item == null) return;

        if (!item.isLeaf()) {
            File dir = item.getValue();
            File[] files = dir.listFiles();
            if (files != null) {
                var newChildren = Stream.of(files)
                        .filter(f -> showHidden || !f.isHidden())
                        .sorted(Comparator.comparing(File::isDirectory).reversed()
                                .thenComparing(f -> f.getName().toLowerCase()))
                        .map(this::createTreeItem)
                        .toList();
                item.getChildren().setAll(newChildren);
            }

            for (TreeItem<File> child : item.getChildren()) {
                if (child.isExpanded()) {
                    refreshTree(child);
                }
            }
        }
    }

    private void registerViewers() {
        FileViewer textViewer = file -> {
            var area = new TextArea("Loading...");
            area.setEditable(false);
            Thread.ofVirtual().start(() -> {
                try {
                    String content = Files.readString(file.toPath());
                    Platform.runLater(() -> area.setText(content));
                } catch (Exception e) {
                    Platform.runLater(() -> area.setText("Error: " + e.getMessage()));
                }
            });
            return area;
        };

        FileViewer imgViewer = file -> {
            var iv = new ImageView(new Image(file.toURI().toString(), true));
            iv.setPreserveRatio(true);
            iv.fitWidthProperty().bind(contentArea.widthProperty().subtract(20));
            iv.fitHeightProperty().bind(contentArea.heightProperty().subtract(20));
            return new ScrollPane(new StackPane(iv));
        };

        FileViewer audioViewer = this::createAudioUI;

        FileViewer videoViewer = file -> {
            var media = new Media(file.toURI().toString());
            currentAudioPlayer = new MediaPlayer(media);
            var mediaView = new MediaView(currentAudioPlayer);

            mediaView.fitWidthProperty().bind(contentArea.widthProperty().subtract(40));
            mediaView.setPreserveRatio(true);

            var playBtn = new Button("Play");
            playBtn.setOnAction(e -> currentAudioPlayer.play());

            var pauseBtn = new Button("Pause");
            pauseBtn.setOnAction(e -> currentAudioPlayer.pause());

            var controls = new HBox(10, playBtn, pauseBtn);
            controls.setAlignment(Pos.CENTER);

            var layout = new VBox(15, mediaView, controls);
            layout.setAlignment(Pos.CENTER);

            currentAudioPlayer.play();
            return layout;
        };

        Set.of("mp4", "m4v", "flv").forEach(ext -> viewerRegistry.put(ext, videoViewer));
        Set.of("txt", "java", "gradle", "md", "css", "py", "json", "xml", "kt", "js", "log")
                .forEach(ext -> viewerRegistry.put(ext, textViewer));
        Set.of("png", "jpg", "jpeg", "gif", "bmp")
                .forEach(ext -> viewerRegistry.put(ext, imgViewer));
        Set.of("mp3", "wav", "m4a", "aac")
                .forEach(ext -> viewerRegistry.put(ext, audioViewer));
    }

    private void updatePreview(File file) {
        stopAudio();
        contentArea.getChildren().clear();

        String ext = getExtension(file.getName());
        FileViewer viewer = viewerRegistry.getOrDefault(ext, f -> new Label("No preview available for ." + ext));

        contentArea.getChildren().add(viewer.createView(file));
    }

    private TreeItem<File> createTreeItem(File file) {
        var item = new FileTreeItem(file);

        FontIcon icon = new FontIcon(file.isDirectory() ? FontAwesomeSolid.FOLDER : FontAwesomeSolid.FILE);
        icon.setIconColor(javafx.scene.paint.Color.web(file.isDirectory() ? "#fabd2f" : "#83a598"));
        item.setGraphic(icon);

        return item;
    }

    private Node createAudioUI(File file) {
        currentAudioPlayer = new MediaPlayer(new Media(file.toURI().toString()));
        var playBtn = new Button("Play");
        playBtn.setOnAction(e -> currentAudioPlayer.play());
        var box = new VBox(15, new Label("Audio: " + file.getName()), playBtn);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private void stopAudio() {
        if (currentAudioPlayer != null) {
            currentAudioPlayer.stop();
            currentAudioPlayer.dispose();
            currentAudioPlayer = null;
        }
    }

    private String getExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i > 0 ? fileName.substring(i + 1).toLowerCase() : "";
    }

    @FunctionalInterface
    interface FileViewer {
        Node createView(File file);
    }

}
