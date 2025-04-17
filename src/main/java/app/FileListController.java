package app;

import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;


public class FileListController
{

    public static class FileElem
    {
        public final BooleanProperty enabled = new SimpleBooleanProperty(true);
        public final StringProperty fileName = new SimpleStringProperty("");
        public final String filePath;
        public final int fileId;

        public FileElem(boolean enabled, String fileName, int fileId)
        {
            this.enabled.set(enabled);
            this.filePath = fileName;
            this.fileId = fileId;
            this.fileName.set(Paths.get(fileName).getFileName().toString());
        }
    }

    private final TableView<FileElem> table = new TableView<>();
    private final ObservableList<FileElem> files = FXCollections.observableArrayList();
    private Runnable onFileOpen = () -> {};
    private Consumer<String> onFileClose = (String fileName) -> {};

    public FileListController(TableViewController tableController)
    {
        TableColumn<FileElem, Boolean> enabledCol = new TableColumn<>("✔");
        enabledCol.setCellValueFactory(data -> data.getValue().enabled);
        enabledCol.setCellFactory(CheckBoxTableCell.forTableColumn(enabledCol));
        enabledCol.setEditable(true);
        table.getColumns().add(enabledCol);

        TableColumn<FileElem, String> fileNameColumn = new TableColumn<>("File Name");
        fileNameColumn.setCellValueFactory(param -> param.getValue().fileName);
        fileNameColumn.prefWidthProperty().bind(table.widthProperty().subtract(2));
        fileNameColumn.setEditable(false);
        table.getColumns().add(fileNameColumn);

        table.setPrefWidth(200);
        table.setEditable(true);

        files.addListener((ListChangeListener<FileElem>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (FileElem file : change.getAddedSubList()) {
                        file.enabled.addListener((obs, oldValue, newValue) -> {
                            tableController.setFileEnabled(file.fileId, newValue);
                        });
                    }
                }
            }
        });

        // ACTIONS
        // 1. remove rows
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.INSERT) {
                onFileOpen.run();
            }
            else if (event.getCode() == KeyCode.DELETE) {
                FileElem selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    files.remove(selected);
                }
            }
        });

        // context menu
        table.setRowFactory(tv -> {
            TableRow<FileElem> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();

            MenuItem deleteItem = new MenuItem("Close");
            deleteItem.setOnAction(e -> {
                FileElem item = row.getItem();
                if (item != null) {
                    files.remove(item);
                }
            });

            contextMenu.getItems().add(deleteItem);
            row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(contextMenu)
            );

            return row;
        });

        files.addListener((ListChangeListener<FileElem>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    for (FileElem file : change.getRemoved()) {
                        onFileClose.accept(file.filePath);
                    }
                }
            }
        });
    }

    private VBox view;
    public VBox getView() {
        if (view == null) {

            Button addButton = new Button("+ Add File");
            addButton.setOnAction(e -> onFileOpen.run());

            HBox topBar = new HBox(addButton);
            topBar.setPadding(new Insets(5));

            VBox box = new VBox(topBar, table);
            VBox.setVgrow(table, Priority.ALWAYS);
            // return box;

            // VBox box = new VBox(table);
            // VBox.setVgrow(table, Priority.ALWAYS);  // make TableView grow inside VBox
            // box.setPadding(new Insets(5));
            view = box;
        }

        return view;
    }

    public void addFile(String fileName, int fileId)
    {
        files.add(new FileElem(true, fileName, fileId));
        table.setItems(files);
    }

    public List<String> getOpenFiles()
    {
        return this.files.stream()
            .map(file -> file.filePath)
            .toList();
    }

    public void setOnFileOpen(Runnable onFileOpen)
    {
        this.onFileOpen = onFileOpen;
    }

    public void setOnFileClose(Consumer<String> object) {
        this.onFileClose = object;
    }
}
