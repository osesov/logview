package app;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public class FilterViewController {
    public enum ActionType { INCLUDE, EXCLUDE, HIGHLIGHT }
    public enum MatchType { REGEX, PLAIN, JSONPATH }

    public static class FilterRule {
        public final ObjectProperty<ActionType> action = new SimpleObjectProperty<>(ActionType.INCLUDE);
        public final ObjectProperty<MatchType> type = new SimpleObjectProperty<>(MatchType.PLAIN);
        public final StringProperty expression = new SimpleStringProperty("");
        public final ObjectProperty<Color> color = new SimpleObjectProperty<>(Color.YELLOW);
        public final BooleanProperty enabled = new SimpleBooleanProperty(true);

        public Map<String, String> toSerializable() {
            Map<String, String> map = new HashMap<>();
            map.put("action", action.get().name());
            map.put("type", type.get().name());
            map.put("expression", expression.get());
            map.put("color", color.get().toString());
            map.put("enabled", String.valueOf(enabled.get()));
            return map;
        }

        public static FilterRule fromSerializable(Map<String, String> map) {
            FilterRule rule = new FilterRule();
            rule.action.set(ActionType.valueOf(map.getOrDefault("action", "INCLUDE")));
            rule.type.set(MatchType.valueOf(map.getOrDefault("type", "PLAIN")));
            rule.expression.set(map.getOrDefault("expression", ""));
            rule.color.set(Color.web(map.getOrDefault("color", "#ffff00")));
            rule.enabled.set(Boolean.parseBoolean(map.getOrDefault("enabled", "true")));
            return rule;
        }

        public boolean matches(String row, ObjectMapper mapper) {
            String exprText = expression.get();
            return switch (type.get()) {
                case PLAIN -> row.contains(exprText);

                case REGEX -> {
                    Pattern pattern = Pattern.compile(exprText, Pattern.CASE_INSENSITIVE);
                    yield pattern.matcher(row).find();
                }

                case JSONPATH -> {
                    try {
                        String json = mapper.writeValueAsString(row);
                        Object result = JsonPath.read(json, exprText);
                        yield result != null;
                    } catch (Exception e) {
                        yield false;
                    }
                }
            };
        }
    }

    private final TableView<FilterRule> table = new TableView<>();
    private final ObservableList<FilterRule> rules = FXCollections.observableArrayList();
    private Runnable onRulesChanged = () -> {};

    public FilterViewController() {
        TableColumn<FilterRule, Boolean> enabledCol = new TableColumn<>("✔");
        enabledCol.setCellValueFactory(data -> data.getValue().enabled);
        enabledCol.setCellFactory(CheckBoxTableCell.forTableColumn(enabledCol));

        TableColumn<FilterRule, ActionType> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(data -> data.getValue().action);
        actionCol.setCellFactory(ComboBoxTableCell.forTableColumn(ActionType.values()));

        TableColumn<FilterRule, MatchType> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> data.getValue().type);
        typeCol.setCellFactory(ComboBoxTableCell.forTableColumn(MatchType.values()));

        TableColumn<FilterRule, String> exprCol = new TableColumn<>("Expression");
        exprCol.setCellValueFactory(data -> data.getValue().expression);
        exprCol.setCellFactory(TextFieldTableCell.forTableColumn());

        TableColumn<FilterRule, Color> colorCol = new TableColumn<>("Color");
        colorCol.setCellValueFactory(data -> data.getValue().color);
        // colorCol.setCellFactory(column -> new TableCell<>() {
        //     private final ColorPicker picker = new ColorPicker();
        //     {
        //         picker.setOnAction(e -> getTableView().getItems().get(getIndex()).color.set(picker.getValue()));
        //     }

        //     @Override
        //     protected void updateItem(Color color, boolean empty) {
        //         super.updateItem(color, empty);
        //         if (empty || color == null) {
        //             setGraphic(null);
        //         } else {
        //             picker.setValue(color);
        //             setGraphic(picker);
        //         }
        //     }
        // });

        // avoid color name
        colorCol.setCellFactory(column -> new TableCell<>() {
            private final ColorPicker picker = new ColorPicker();

            {
                picker.setOnAction(e -> {
                    FilterRule rule = getTableView().getItems().get(getIndex());
                    if (rule != null) {
                        rule.color.set(picker.getValue());
                    }
                });
                picker.setStyle("-fx-color-label-visible: false;"); // <--- key line!
            }

            @Override
            protected void updateItem(Color color, boolean empty) {
                super.updateItem(color, empty);
                if (empty || color == null) {
                    setGraphic(null);
                } else {
                    picker.setValue(color);
                    setGraphic(picker);
                }
            }
        });


        table.getColumns().addAll(enabledCol, actionCol, typeCol, exprCol, colorCol);
        table.setItems(rules);
        table.setEditable(true);

        // column widths
        enabledCol.setPrefWidth(40);
        enabledCol.setMaxWidth(40);
        enabledCol.setMinWidth(10);

        actionCol.setPrefWidth(100);
        actionCol.setMaxWidth(120);
        actionCol.setMinWidth(10);

        typeCol.setPrefWidth(120);
        typeCol.setMaxWidth(140);
        typeCol.setMinWidth(10);

        colorCol.setPrefWidth(60);
        colorCol.setMaxWidth(60);
        colorCol.setMinWidth(10);

        // expressionCol takes remaining space
        exprCol.setMinWidth(10);
        exprCol.setPrefWidth(500);
        exprCol.setResizable(true);
        // this does not work with column width loading. loadColumnWidths()
        // table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        actionCol.setCellFactory(col -> {
            ComboBoxTableCell<FilterRule, FilterViewController.ActionType> cell =
                new ComboBoxTableCell<>(FilterViewController.ActionType.values());
            cell.setPadding(new Insets(2));
            cell.setAlignment(Pos.CENTER_LEFT);
            return cell;
        });

        typeCol.setCellFactory(col -> {
            ComboBoxTableCell<FilterRule, FilterViewController.MatchType> cell =
                new ComboBoxTableCell<>(FilterViewController.MatchType.values());
            cell.setPadding(new Insets(2));
            cell.setAlignment(Pos.CENTER_LEFT);
            return cell;
        });

        // ACTIONS
        // remove rows
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.INSERT) {
                rules.add(new FilterRule());
            }
            else if (event.getCode() == KeyCode.DELETE) {
                FilterRule selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    rules.remove(selected);
                }
            }
        });

        // context menu
        table.setRowFactory(tv -> {
            TableRow<FilterRule> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();

            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setOnAction(e -> {
                FilterRule item = row.getItem();
                if (item != null) {
                    rules.remove(item);
                }
            });

            contextMenu.getItems().add(deleteItem);
            row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(contextMenu)
            );

            return row;
        });

        // listen for rules changes to watch cells
        rules.addListener((ListChangeListener<FilterRule>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (FilterRule rule : change.getAddedSubList()) {
                        observeRule(rule, () -> onRulesChanged.run());
                    }
                }
            }
        });

    }

    public VBox getView() {
        Button addButton = new Button("+ Add Filter");
        addButton.setOnAction(e -> rules.add(new FilterRule()));

        HBox topBar = new HBox(addButton);
        topBar.setPadding(new Insets(5));

        VBox box = new VBox(topBar, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    public void addDefaultRule() {
        rules.add(new FilterRule());
    }

    public ObservableList<FilterRule> getRules() {
        return rules;
    }

    public void clear() {
        rules.clear();
    }

    public void saveRulesToPreferences(ObjectMapper mapper) {
        try {
            List<Map<String, String>> list = rules.stream()
                .map(FilterRule::toSerializable)
                .toList();

            String json = mapper.writeValueAsString(list);
            AppSettings.saveFilterRules(json);
        } catch (Exception e) {
            System.err.println("Failed to save filter rules: " + e.getMessage());
        }
    }

    public void loadRulesFromPreferences(ObjectMapper mapper) {
        String json = AppSettings.loadFilterRules();
        if (json != null && !json.isEmpty()) {
            try {
                List<Map<String, String>> list = mapper.readValue(
                    json,
                    new TypeReference<>() {}
                );
                rules.clear();
                for (Map<String, String> map : list) {
                    rules.add(FilterRule.fromSerializable(map));
                }
            } catch (Exception e) {
                System.err.println("Failed to load filter rules: " + e.getMessage());
            }
        }
    }

    private void observeRule(FilterRule rule, Runnable onChange) {
        rule.action.addListener((obs, oldVal, newVal) -> onChange.run());
        rule.type.addListener((obs, oldVal, newVal) -> onChange.run());
        rule.expression.addListener((obs, oldVal, newVal) -> onChange.run());
        rule.color.addListener((obs, oldVal, newVal) -> onChange.run());
        rule.enabled.addListener((obs, oldVal, newVal) -> onChange.run());
    }

    public void setOnRulesChanged(Runnable callback) {
        this.onRulesChanged = callback;
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
            AppSettings.saveFilterColumnWidths(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadColumnWidths(ObjectMapper mapper) {
        String json = AppSettings.loadFilterColumnWidths();
        if (json == null || json.isEmpty()) return;

        try {
            Map<String, Double> widths = mapper.readValue(json, new TypeReference<>() {});
            for (TableColumn<?, ?> col : table.getColumns()) {
                Double w = widths.get(col.getText());
                if (w == null) continue;
                if (col.prefWidthProperty().isBound())
                    continue;
                col.setPrefWidth(w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
