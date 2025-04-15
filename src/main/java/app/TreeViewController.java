package app;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TreeViewController {
    private TreeView<String> tree = new TreeView<>(new TreeItem<>("Root"));
    private ObjectMapper mapper;

    public TreeViewController(ObjectMapper mapper) {
        this.mapper = mapper;
        this.tree.setShowRoot(false);
    }

    public VBox getView() {
        VBox box = new VBox(tree); // or tree
        VBox.setVgrow(tree, Priority.ALWAYS);  // make TreeView grow inside VBox
        box.setPadding(new Insets(5));
        return box;
    }

    public void addObject(String jsonObject) {
        try {
            tree.getRoot().getChildren().clear();
            var node = mapper.readTree(jsonObject);

            if (node == null) {
                System.err.println("Invalid JSON: " + jsonObject);
                return;
            }

            this.addJsonToTree(node, tree.getRoot());
            // if (node.isObject()) {
            // }


            // TreeItem<String> parent = new TreeItem<>("Object");
            // this.addJsonToTree(node, parent);
            // tree.getRoot().getChildren().add(parent);
        }
        catch (Exception e) {
            System.err.println("Invalid JSON: " + e.getMessage());
            e.printStackTrace();
            System.err.println(jsonObject);
        }
    }

    private Pair<String, Boolean> getTitle(JsonNode node) {
        String title;
        boolean truncated = false;

        if (node.isObject() || node.isArray()) {
            String s = node.toString();
            var newLine = s.indexOf("\n");
            if (newLine >= 0 && s.length() > newLine + 1) {
                title = s.substring(0, newLine) + "...";
                truncated = true;
            } else if (s.length() > 256) {
                title = s.substring(0, 256) + "...";
                truncated = true;
            } else {
                title = s;
            }
        } else {
            var text = node.asText();
            var newLine = text.indexOf('\n');

            if (newLine >= 0 && text.length() > newLine + 1) {
                var maxLength = Math.min(256, text.length());
                title = text.substring(0, maxLength).replaceAll("\n", "\\\\n") + (text.length() > maxLength ? "..." : "");
                truncated = true;
            } else if (text.length() > 256) {
                title = text.substring(0, 255) + "...";
                truncated = true;
            } else {
                title = text;
            }
        }

        return new Pair<>(title, truncated);
    }

    private void addJsonToTree(JsonNode node, TreeItem<String> parent) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                TreeItem<String> child;
                if (entry.getValue().isObject()) {
                    child = new TreeItem<>(entry.getKey() + ": " + this.getTitle(entry.getValue()));
                    addJsonToTree(entry.getValue(), child);
                } else if (entry.getValue().isArray()) {
                    child = new TreeItem<>(entry.getKey() + ": " + this.getTitle(entry.getValue()));
                    addJsonToTree(entry.getValue(), child);
                } else {
                    var result = this.getTitle(entry.getValue());
                    var title = result.getKey();
                    var truncated = result.getValue();

                    if (truncated) { // truncated
                        child = new TreeItem<>(entry.getKey() + ": " + title);
                        var rest = new TreeItem<>(entry.getValue().asText());
                        child.getChildren().add(rest);
                    }
                    else {
                        child = new TreeItem<>(entry.getKey() + ": " + title);
                    }
                }
                parent.getChildren().add(child);
            });
        } else if (node.isArray()) {
            int index = 0;
            for (var element : node) {
                TreeItem<String> child;
                if (element.isObject() || element.isArray()) {
                    child = new TreeItem<>("[" + index + "]: " + this.getTitle(element));
                    addJsonToTree(element, child);
                } else {
                    var result = this.getTitle(element);
                    var title = result.getKey();
                    var truncated = result.getValue();

                    if (truncated) { // truncated
                        child = new TreeItem<>("[" + index + "]: " + title);
                        var rest = new TreeItem<>(element.asText());
                        child.getChildren().add(rest);
                    }
                    else {
                        child = new TreeItem<>("[" + index + "]: " + title);
                    }
                }
                parent.getChildren().add(child);
                index++;
            }
        }

        // if (jsonObject instanceof Map<?, ?> map) {
        //     TreeItem<String> parent = new TreeItem<>("Object");
        //     map.forEach((k, v) -> {
        //         TreeItem<String> child = new TreeItem<>(k + ": " + (v instanceof Map ? "[Object]" : v.toString()));
        //         parent.getChildren().add(child);
        //     });
        //     tree.getRoot().getChildren().add(parent);
        // }
    }
}
