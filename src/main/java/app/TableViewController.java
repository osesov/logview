package app;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import app.FilterViewController.FilterRule;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class TableViewController {
    private TableView<LineBounds> table = new TableView<>();
    private JsonLineReader jsonLineReader = null;

    private final Map<LineBounds, Color> highlightMap = new HashMap<>();
    private ObservableList<LineBounds> allEntries = FXCollections.observableArrayList();
    private final FilteredList<LineBounds> filteredEntries = new FilteredList<>(allEntries, e -> true);

    private ObjectMapper mapper;
    private TreeViewController treeController;
    private DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("u-MM-dd hh:mm:ss");
    private String searchTerm = null;

    public TableViewController(ObjectMapper mapper, TreeViewController treeController) {
        this.mapper = mapper;
        this.treeController = treeController;

        // table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<LineBounds, Long> numberColumn = new TableColumn<>("#");
        numberColumn.setCellValueFactory(param -> new ReadOnlyLongWrapper(param.getValue().getIndex() + 1).asObject());
        // levelColumn.prefWidthProperty().bind(table.widthProperty().subtract(2));
        table.getColumns().add(numberColumn);

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

        argsColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    LineBounds row = table.getItems().get(getIndex());
                    Color color = highlightMap.get(row);

                    if (searchTerm != null && !searchTerm.isEmpty() && item.toLowerCase().contains(searchTerm.toLowerCase()))
                        color = Color.YELLOW;

                    boolean selected = getTableRow().isSelected();
                    if (selected) {
                    }
                    else if (color != null) {
                        String textColor = getInverseTextColor(color);
                        setStyle(String.format(
                            "-fx-background-color: %s; -fx-text-fill: %s;",
                            toRgbString(color), textColor
                        ));

                    } else if (highlightMap.isEmpty()) {
                        setStyle("");
                    } else {
                        setStyle("-fx-background-color: transparent");
                    }
                }
            }
        });

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            table.refresh();  // force colors re-evaluation
            if (newVal != null) {
                String rowData = this.getString(newVal);
                treeController.addObject(rowData);
            }
        });

        // table.setItems(entries);
        table.setItems(filteredEntries);

        // table.setRowFactory(tv -> {
        //     TableRow<LineBounds> row = new TableRow<>();
        //     row.setOnMouseClicked(event -> {
        //         if (!row.isEmpty()) {
        //             String rowData = this.getString(row.getItem());
        //             treeController.addObject(rowData);
        //         }
        //     });
        //     return row;
        // });
    }

    public void setSearchString(String query) {
        // Predicate<LineBounds> predicate;

        searchTerm = query;

        // if (query.startsWith("re:")) {
        //     Pattern pattern = Pattern.compile(query.substring(3), Pattern.CASE_INSENSITIVE);
        //     predicate = entry -> pattern.matcher(this.getString(entry)).find();
        // } else {
        //     String lower = query.toLowerCase();
        //     predicate = entry -> this.getString(entry).toLowerCase().contains(lower);
        // }

        // filteredEntries.setPredicate(predicate);
        table.refresh();
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

    public void applyFilters(List<FilterRule> rules) {
        highlightMap.clear();
        boolean hasInclude = false;
        boolean hasExclude = false;
        int aliveRuleCount = 0;

        for (FilterRule rule : rules) {
            if (!rule.enabled.get()) {
                continue;
            }
            hasInclude = hasInclude || rule.action.get() == FilterViewController.ActionType.INCLUDE;
            hasExclude = hasExclude || rule.action.get() == FilterViewController.ActionType.EXCLUDE;
            aliveRuleCount++;
        }

        boolean defaultVisibility
            = hasInclude && hasExclude ? false
            : !hasInclude && !hasExclude ? true
            : hasInclude ? false
            : true;
        boolean hasRules = aliveRuleCount > 0;

        filteredEntries.setPredicate(row -> {
            FilterRule lastMatch = null;
            // if any include/exclude rule exists then default is to hide. If none, show all
            String json = this.getString(row);
            if (!hasRules) {
                return true;
            }

            for (FilterRule rule : rules) {
                if (!rule.enabled.get()) {
                    continue;
                }
                if (rule.matches(json, mapper)) {
                    lastMatch = rule;
                }
            }

            if (lastMatch == null)
                return defaultVisibility;

            if (lastMatch.action.get() == FilterViewController.ActionType.HIGHLIGHT) {
                highlightMap.put(row, lastMatch.color.get());
                return true;
            }

            return lastMatch.action.get() == FilterViewController.ActionType.INCLUDE;
        });

        table.refresh(); // trigger UI update
    }

    private String toRgbString(Color color) {
        return String.format("rgba(%d,%d,%d,%.2f)",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255),
            color.getOpacity());
    }

    private String getInverseTextColor(Color bg) {
        double brightness = 0.2126 * bg.getRed() + 0.7152 * bg.getGreen() + 0.0722 * bg.getBlue();
        return brightness < 0.5 ? "white" : "black";
    }

    private Color getInverseColor(Color color) {
        return Color.color(1.0 - color.getRed(), 1.0 - color.getGreen(), 1.0 - color.getBlue(), color.getOpacity());
    }

    public void saveColumnWidths(ObjectMapper mapper) {
        Map<String, Double> widths = new HashMap<>();
        for (TableColumn<?, ?> col : table.getColumns()) {
            if (col.prefWidthProperty().isBound())
                continue;

            widths.put(col.getText(), col.getWidth());
        }

        try {
            String json = mapper.writeValueAsString(widths);
            AppSettings.saveTableColumnWidths(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadColumnWidths(ObjectMapper mapper) {
        String json = AppSettings.loadTableColumnWidths();
        if (json == null || json.isEmpty()) return;

        try {
            Map<String, Double> widths = mapper.readValue(json, new TypeReference<>() {});
            for (TableColumn<?, ?> col : table.getColumns()) {
                Double w = widths.get(col.getText());
                if (w == null)
                    continue;
                if (col.prefWidthProperty().isBound())
                    continue;

                col.setPrefWidth(w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void selectNextMatch() {

        if (searchTerm == null || searchTerm.isEmpty())
            return;

        int startIndex = table.getSelectionModel().getSelectedIndex();
        int rowCount = table.getItems().size();

        for (int i = 1; i <= rowCount; i++) {
            int currentIndex = (startIndex + i) % rowCount;
            LineBounds row = table.getItems().get(currentIndex);
            if (row == null) continue;

            String rowData = this.getString(row);
            if (rowData.toLowerCase().contains(searchTerm.toLowerCase())) {
                table.getSelectionModel().clearAndSelect(currentIndex);
                table.scrollTo(currentIndex);
                // table.requestFocus();
                break;
            }
        }
    }
}
