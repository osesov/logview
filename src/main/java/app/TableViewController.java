package app;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TableViewController {
    private TableView<LineBounds> table = new TableView<>();
    private JsonLineReader jsonLineReader = null;
    private ObservableList<LineBounds> allEntries = FXCollections.observableArrayList();
    private final FilteredList<LineBounds> filteredEntries = new FilteredList<>(allEntries, e -> true);

    private ObjectMapper mapper;
    private TreeViewController treeController;
    private DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("u-MM-dd hh:mm:ss");
    private String searchTerm = null;

    public TableViewController(ObjectMapper mapper, TreeViewController treeController) {
        this.mapper = mapper;
        this.treeController = treeController;

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // TableColumn<String, String> keyColumn = new TableColumn<>("Key");
        // keyColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getKey()));
        // table.getColumns().add(keyColumn);

        TableColumn<LineBounds, String> levelColumn = new TableColumn<>("Level");
        levelColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(this.getField(param.getValue(), "level")));
        // levelColumn.prefWidthProperty().bind(table.widthProperty().subtract(2));
        table.getColumns().add(levelColumn);

        TableColumn<LineBounds, String> timeColumn = new TableColumn<>("Time");
        timeColumn.setCellValueFactory(param -> {
            var timestamp = this.getNode(param.getValue(), "timestamp").asLong(0);
            var instance = java.time.Instant.ofEpochMilli(timestamp);
            var zoneId = java.time.ZoneId.systemDefault();
            var localDateTime = java.time.LocalDateTime.ofInstant(instance, zoneId);
            var string = localDateTime.format(formatter);
            return new ReadOnlyStringWrapper(string);
        });
        // timeColumn.prefWidthProperty().bind(table.widthProperty().subtract(2));
        table.getColumns().add(timeColumn);

        TableColumn<LineBounds, String> messageColumn = new TableColumn<>("Message");
        messageColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(this.getField(param.getValue(), "message")));
        // timeColumn.prefWidthProperty().bind(table.widthProperty().subtract(2));
        table.getColumns().add(messageColumn);

        TableColumn<LineBounds, String> argsColumn = new TableColumn<>("Args");
        argsColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(this.getField(param.getValue(), "args")));
        argsColumn.prefWidthProperty().bind(table.widthProperty()
            .subtract(table.getColumns().stream()
                .filter(column -> column != argsColumn)
                .mapToDouble(TableColumn::getWidth)
                .sum() + 2));
        // timeColumn.prefWidthProperty().bind(table.widthProperty().subtract(2));
        table.getColumns().add(argsColumn);

        for (TableColumn<LineBounds, ?> column : table.getColumns()) {
            column.setResizable(true);
            column.setReorderable(true);

        }
        argsColumn.setCellFactory(tableColumn -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (searchTerm != null && !searchTerm.isEmpty() && item.toLowerCase().contains(searchTerm.toLowerCase())) {
                        setStyle("-fx-background-color: lightyellow");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        table.setItems(filteredEntries);
        // table.setItems(entries);

        table.setRowFactory(tv -> {
            TableRow<LineBounds> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) {
                    String rowData = this.getString(row.getItem());
                    treeController.addObject(rowData);
                }
            });
            return row;
        });
    }

    public void setFilter(String query) {
        Predicate<LineBounds> predicate;

        searchTerm = query;

        if (query.startsWith("re:")) {
            Pattern pattern = Pattern.compile(query.substring(3), Pattern.CASE_INSENSITIVE);
            predicate = entry -> pattern.matcher(this.getString(entry)).find();
        } else {
            String lower = query.toLowerCase();
            predicate = entry -> this.getString(entry).toLowerCase().contains(lower);
        }

        filteredEntries.setPredicate(predicate);
    }

    private String getString(LineBounds bounds) {
        return this.jsonLineReader.getString(bounds);
    }

    private String getField(LineBounds bounds, String field) {
        var node = this.getNode(bounds, field);
        if (node == null) {
            return "";
        }

        if (node.isTextual()) {
            return node.asText();
        }

        return node.toString();
    }

    private JsonNode getNode(LineBounds bounds, String field) {
        try {
            String json = jsonLineReader.getString(bounds);
            JsonNode jsonNode = mapper.readTree(json);
            return jsonNode.findValue(field);
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public VBox getView() {
        VBox box = new VBox(table); // or tree
        VBox.setVgrow(table, Priority.ALWAYS);  // make TableView grow inside VBox
        box.setPadding(new Insets(5));
        return box;
    }

    public void reset(JsonLineReader jsonLineReader) {
        this.jsonLineReader = jsonLineReader;
        allEntries.clear();
        filteredEntries.setPredicate(e -> true);
    }

    public void addObject(LineBounds jsonObject) {
        allEntries.add( jsonObject );
    }

    public void scrollToRow(int index) {
        if (index >= 0 && index < table.getItems().size()) {
            table.scrollTo(index);
            table.getSelectionModel().clearAndSelect(index);
            table.requestFocus();
        }
    }

    public void focus() {
        table.requestFocus();
    }
}
