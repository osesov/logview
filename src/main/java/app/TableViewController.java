package app;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

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

    private TreeViewController treeController;
    private DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("u-MM-dd hh:mm:ss");
    private String searchTerm = null;
    private Map<String, TableColumn<LineBounds, ?>> columnMap = new HashMap<>();
    private TableColumn<LineBounds, String> valueColumn;
    private TableColumn<LineBounds, Long> numberColumn;
    private Set<Integer> disabledFiles = new HashSet<>();
    private List<FilterRule> filterRules = null;

    public TableViewController(TreeViewController treeController) {
        this.treeController = treeController;

        // table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        numberColumn = new TableColumn<>("#");
        numberColumn.setCellValueFactory(param -> new ReadOnlyLongWrapper(param.getValue().objIndex() + 1).asObject());
        // levelColumn.prefWidthProperty().bind(table.widthProperty().subtract(2));
        table.getColumns().add(numberColumn);

        valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(param -> {
            var node = this.getString(param.getValue());
            if (node == null) {
                return new ReadOnlyStringWrapper("");
            }
            return new ReadOnlyStringWrapper(node.toString());
        });

        setupColumn(valueColumn, null);
        table.getColumns().add(valueColumn);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            table.refresh();  // force colors re-evaluation
            if (newVal != null) {
                String rowData = this.getString(newVal);
                treeController.addObject(rowData);
            }
        });

        table.setItems(filteredEntries);
        filteredEntries.setPredicate(row -> this.filterPredicate(row));
    }

    private void forceFilterUpdate()
    {
        filteredEntries.setPredicate(row -> this.filterPredicate(row));
    }

    public void setSearchString(String query) {
        searchTerm = query;
        forceFilterUpdate();
    }

    private String getString(LineBounds bounds) {
        return this.jsonLineReader.getString(bounds);
    }

    private JsonNode getNode(LineBounds bounds, String field) {
        try {
            String json = jsonLineReader.getString(bounds);
            JsonNode jsonNode = AppSettings.getMapper().readTree(json);
            return field == null ? jsonNode : jsonNode.findValue(field);
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public VBox getView() {
        VBox box = new VBox(table);
        VBox.setVgrow(table, Priority.ALWAYS);  // make TableView grow inside VBox
        box.setPadding(new Insets(5));
        return box;
    }

    public void reset(JsonLineReader jsonLineReader) {
        this.jsonLineReader = jsonLineReader;
        allEntries.clear();
        columnMap.clear();
        table.getColumns().clear();
        table.getColumns().add(numberColumn);
        table.getColumns().add(valueColumn);
    }

    public void addObject(LineBounds jsonObject) {
        allEntries.add( jsonObject );

        // load top-level properties

        try {
            String str = this.getString(jsonObject);
            JsonNode node = AppSettings.getMapper().readTree(str);

            if (!node.isObject())
                return;

            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                String key = field.getKey();

                if (columnMap.containsKey(key)) {
                    continue;
                }
                TableColumn<LineBounds, String> column = new TableColumn<>(key);
                setupColumn(column, key);
                table.getColumns().add(table.getColumns().size() - 1, column);
                columnMap.put(key, column);
            }
        }
        catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupColumn(TableColumn<LineBounds, String> column, String key)
    {

        column.setCellValueFactory(param -> {
            JsonNode jsonNode = this.getNode(param.getValue(), key);
            if (jsonNode == null) {
                return new ReadOnlyStringWrapper("");
            }
            return new ReadOnlyStringWrapper(jsonNode.toString());
        });
        column.setPrefWidth(100);
        column.setResizable(true);
        column.setReorderable(true);

        column.setCellFactory(col -> new TableCell<>() {
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

    private boolean filterPredicate(LineBounds row)
    {
        boolean hasInclude = false;
        boolean hasExclude = false;
        int aliveRuleCount = 0;

        if (this.disabledFiles.contains(row.fileId()))
            return false;

        if (filterRules != null) {
            for (FilterRule rule : filterRules) {
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

            FilterRule lastMatch = null;

            // if any include/exclude rule exists then default is to hide. If none, show all
            String json = this.getString(row);
            if (!hasRules) {
                return true;
            }

            for (FilterRule rule : filterRules) {
                if (!rule.enabled.get()) {
                    continue;
                }
                if (rule.matches(json)) {
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
        }

        return true;
    }

    public void applyFilters(List<FilterRule> rules) {
        highlightMap.clear();
        this.filterRules = rules;
        forceFilterUpdate();
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

    public void saveColumnLayout() {
        TableColumnLayoutUtil.saveColumnLayout(table, "tableColumns");
    }

    public void loadColumnLayout() {
        TableColumnLayoutUtil.loadColumnLayout(table, "tableColumns");
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

    public void setFileEnabled(int fileId, boolean enabled) {
        if (enabled) {
            disabledFiles.remove(fileId);
        } else {
            disabledFiles.add(fileId);
        }

        forceFilterUpdate();
    }

    public void removeFile(String fileName, int fileId) {
        allEntries.removeIf(line -> line.fileId() == fileId);
    }
}
