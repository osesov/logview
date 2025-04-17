package app;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;

import app.FilterViewController.FilterRule;
import app.debug.ArgBuilder;
import app.debug.Trace;
import app.debug.TraceScope;
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
    private static enum StringType
    {
        FULL, TITLE,
    }

    private static record CacheItem(JsonNode node, String string) {}

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
    private final Map<LineBounds, CacheItem> cache = new HashMap<>();
    private int maxStringLength = 256;

    public TableViewController(TreeViewController treeController) {
        this.treeController = treeController;

        // table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        numberColumn = new TableColumn<>("#");
        numberColumn.setCellValueFactory(param -> new ReadOnlyLongWrapper(param.getValue().objIndex() + 1).asObject());
        table.getColumns().add(numberColumn);

        valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(param -> {
            var node = this.getString(param.getValue(), null, StringType.TITLE);
            if (node.isEmpty()) {
                return new ReadOnlyStringWrapper("");
            }
            return new ReadOnlyStringWrapper(node.get());
        });
        // valueColumn.prefWidthProperty().bind(table.widthProperty().subtract(2));

        setupColumn(valueColumn, null, 400);
        table.getColumns().add(valueColumn);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            try (TraceScope ignored = new TraceScope("tableSelectionChanged",
                ArgBuilder.of().putLong("obj", newVal != null ? newVal.objIndex() : -1).build()))
            {
                // table.refresh();  // force colors re-evaluation
                if (newVal != null) {
                    this.getNode(newVal, null).ifPresent(treeController::setObject);
                }
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

    @Trace
    private Optional<CacheItem> getCacheItem(LineBounds bounds) {
        // return Optional.empty();
        return Optional.ofNullable(cache.computeIfAbsent(bounds, b -> {
            try ( TraceScope ignoredInner = new TraceScope("getCacheItemInner",
                ArgBuilder.of().putLong("obj", bounds.objIndex()).build()))
            {
                String str = this.jsonLineReader.getString(b);
                if (b == null || str == null || str.isEmpty()) {
                    return null;
                }

                try {
                    JsonNode jsonNode = AppSettings.getMapper().readTree(str);
                    return new CacheItem(jsonNode, str);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }));
    }

    @Trace
    private Optional<String> getString(LineBounds bounds, String field, StringType type) {
        Optional<CacheItem> node = this.getCacheItem(bounds);
        Function<String, String> cutString = (it) -> {
            if (type == StringType.FULL) {
                return it;
            }
            else {
                boolean cut = it.length() > maxStringLength;
                return (cut ? it.substring(0, maxStringLength).replace("\n", "\\n") + "..." : it);
            }
        };

        if (field != null) {
            return node
                .filter(it -> it.node() != null)
                .map(it -> it.node().findValue(field))
                .map(JsonNode::asText)
                .map(cutString);
        }

        return node
            .map(it -> it.string())
            .map(cutString);
    }

    @Trace
    private Optional<JsonNode> getNode(LineBounds bounds, String field) {
        Optional<JsonNode> node = this.getCacheItem(bounds)
            .filter(it -> it.node() != null)
            .map(it -> it.node())
            .filter(it -> it != null)
            ;



        if (field != null) {
            return node.map(it -> it.findValue(field))
                .filter(it -> it != null)
                ;
        }

        return node;
    }

    public VBox getView() {
        VBox box = new VBox(table);
        VBox.setVgrow(table, Priority.ALWAYS);  // make TableView grow inside VBox
        box.setPadding(new Insets(5));
        return box;
    }

    @Trace
    public void reset(JsonLineReader jsonLineReader) {
        this.jsonLineReader = jsonLineReader;
        allEntries.clear();
        columnMap.clear();
        table.getColumns().clear();
        table.getColumns().add(numberColumn);
        table.getColumns().add(valueColumn);
    }

    @Trace
    public void addObject(LineBounds jsonObject) {
        allEntries.add( jsonObject );

        // load top-level properties
        // TODO: delay loading of columns?

        JsonNode node = this.getNode(jsonObject, null).orElse(null);
        if (node == null)
            return;

        if (!node.isObject())
            return;

        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            String key = field.getKey();

            if (columnMap.containsKey(key)) {
                continue;
            }
            TableColumn<LineBounds, String> column = new TableColumn<>(key);
            setupColumn(column, key, 0);
            table.getColumns().add(table.getColumns().size() - 1, column);
            columnMap.put(key, column);
        }
    }

    @Trace
    private void setupColumn(TableColumn<LineBounds, String> column, String key, int width)
    {
        column.setCellValueFactory(param -> {
            Optional<String> str = this.getString(param.getValue(), key, StringType.TITLE);
            if (str.isEmpty()) {
                return new ReadOnlyStringWrapper("");
            }
            return new ReadOnlyStringWrapper(str.get());
        });

        if (!column.prefWidthProperty().isBound()) {
            column.setPrefWidth(width > 0 ? width : 100);
        }
        column.setResizable(true);
        column.setReorderable(true);

        column.setCellFactory(col -> new TableCell<>() {
            @Trace
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

    @Trace
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
            String json = this.getString(row, null, StringType.FULL).orElse(null);
            if (!hasRules) {
                return true;
            }

            if (json == null || json.isEmpty())
                return true;

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

    @Trace
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


    @Trace
    public void selectNextMatch() {

        if (searchTerm == null || searchTerm.isEmpty())
            return;

        int startIndex = table.getSelectionModel().getSelectedIndex();
        int rowCount = table.getItems().size();

        for (int i = 1; i <= rowCount; i++) {
            int currentIndex = (startIndex + i) % rowCount;
            LineBounds row = table.getItems().get(currentIndex);
            if (row == null)
                continue;

            String rowData = this.getString(row, null, StringType.FULL).orElse(null);
            if (rowData == null)
                continue;

            if (rowData.toLowerCase().contains(searchTerm.toLowerCase())) {
                table.getSelectionModel().clearAndSelect(currentIndex);
                table.scrollTo(currentIndex);
                // table.requestFocus();
                break;
            }
        }
    }

    @Trace
    public void setFileEnabled(int fileId, boolean enabled) {
        if (enabled) {
            disabledFiles.remove(fileId);
        } else {
            disabledFiles.add(fileId);
        }

        forceFilterUpdate();
    }

    @Trace
    public void removeFile(String fileName, int fileId) {
        allEntries.removeIf(line -> line.fileId() == fileId);
    }

    public void setMaxStringLength(int maxStringLength) {
        this.maxStringLength = maxStringLength;
    }
}
