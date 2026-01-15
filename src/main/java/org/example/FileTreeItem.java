package org.example;

import javafx.scene.control.TreeItem;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.Comparator;
import java.util.stream.Stream;

public class FileTreeItem extends TreeItem<File> {
    private boolean isFirst;

    public FileTreeItem(File file) {
        super(file);
        isFirst = true;
    }

    private TreeItem<File> createTreeItem(File file) {
        var item = new TreeItem<>(file) {
            private boolean isFirst = true;

            @Override
            public boolean isLeaf() {
                return !getValue().isDirectory();
            }

            @Override
            public javafx.collections.ObservableList<TreeItem<File>> getChildren() {
                if (isFirst && getValue().isDirectory()) {
                    isFirst = false;
                    syncChildren(this);
                }
                return super.getChildren();
            }
        };

        FontIcon icon = new FontIcon(file.isDirectory() ? FontAwesomeSolid.FOLDER : FontAwesomeSolid.FILE);
        icon.setIconColor(javafx.scene.paint.Color.web(file.isDirectory() ? "#fabd2f" : "#83a598"));
        item.setGraphic(icon);

        return item;
    }

    @Override
    public boolean isLeaf() {
        return !getValue().isDirectory();
    }

    @Override
    public javafx.collections.ObservableList<TreeItem<File>> getChildren() {
        if (isFirst) {
            isFirst = false;
            File[] files = getValue().listFiles();
            if (files != null) {
                for (File f : files) super.getChildren().add(createTreeItem(f));
            }
        }
        return super.getChildren();
    }

    private void syncChildren(TreeItem<File> item) {
        File dir = item.getValue();
        if (dir == null || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        var newChildren = Stream.of(files)
                .filter(f -> FileExplorerApp.isShowHidden() || !f.isHidden())
                .sorted(Comparator.comparing(File::isDirectory).reversed()
                        .thenComparing(f -> f.getName().toLowerCase()))
                .map(this::createTreeItem)
                .toList();

        item.getChildren().setAll(newChildren);
    }
}
