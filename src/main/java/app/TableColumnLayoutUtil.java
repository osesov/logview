package app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class TableColumnLayoutUtil
{

    public static <T> void saveColumnLayout(TableView<T> table, String key) {
        List<Map<String, Object>> layout = new ArrayList<>();
        for (TableColumn<T, ?> col : table.getColumns()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", col.getText());
            entry.put("width", col.getWidth());
            layout.add(entry);
        }

        try {
            String json = AppSettings.getMapper().writeValueAsString(layout);
            AppSettings.saveColumnLayout(key, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static <T> void loadColumnLayout(TableView<T> table, String key) {
        String json = AppSettings.loadColumnLayout(key);
        if (json == null || json.isEmpty()) return;

        try {
            List<Map<String, Object>> layout = AppSettings.getMapper().readValue(json, new TypeReference<>() {});
            List<TableColumn<T, ?>> current = new ArrayList<>(table.getColumns());
            List<TableColumn<T, ?>> ordered = new ArrayList<>();

            for (Map<String, Object> item : layout) {
                String name = (String) item.get("name");
                Double width = (Double) item.get("width");

                for (TableColumn<T, ?> col : current) {
                    if (col.getText().equals(name)) {
                        if (!col.prefWidthProperty().isBound()) {
                            col.setPrefWidth(width);
                        }
                        ordered.add(col);
                        break;
                    }
                }
            }

            for (TableColumn<T, ?> col : current) {
                if (!ordered.contains(col)) {
                    ordered.add(col);
                }
            }

            table.getColumns().setAll(ordered);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
