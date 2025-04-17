package app;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Insets;
import javafx.scene.control.IndexRange;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

public class TreeViewController {

    private static final record TreeElem (String nodeName, JsonNode node, String title) {}

    private TreeView<TreeElem> tree = new TreeView<>(new TreeItem<TreeElem>(null));

    public TreeViewController() {
        tree.setShowRoot(false);

        tree.setCellFactory( tv -> new TreeCell<>() {
            private final TextField textField = new TextField();
            {
                textField.setEditable(false);
                textField.setFocusTraversable(false);
                textField.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

                // Copy by Right Click
                textField.setOnContextMenuRequested(e -> {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    IndexRange selection = textField.getSelection();
                    TreeElem item = getItem();
                    String text = selection.getLength() == 0 ? item.node.toPrettyString() : textField.getSelectedText();
                    content.putString(text);
                    clipboard.setContent(content);

                    if (text.length() > 200) {
                        text = text.substring(0, 200) + "...";
                    }

                    Toast.show("Copied to clipboard: " + text, 2000);
                });

            }

            @Override
            protected void updateItem(TreeElem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    textField.setText(item.title);
                    setText(null);
                    setGraphic(textField);
                }
            }

        });
    }

    public VBox getView() {
        VBox box = new VBox(tree); // or tree
        VBox.setVgrow(tree, Priority.ALWAYS);  // make TreeView grow inside VBox
        box.setPadding(new Insets(5));
        return box;
    }

    public void setObject(JsonNode jsonObject) {
        try {
            tree.getRoot().getChildren().clear();

            if (jsonObject == null) {
                System.err.println("Invalid JSON: " + jsonObject);
                return;
            }

            this.addJsonToTree(jsonObject, tree.getRoot());
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
            } else if (s.length() > 200) {
                title = s.substring(0, 200) + "...";
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
            } else if (text.length() > 200) {
                title = text.substring(0, 200) + "...";
                truncated = true;
            } else {
                title = text;
            }
        }

        return new Pair<>(title, truncated);
    }

    private void addJsonToTree(JsonNode node, TreeItem<TreeElem> parent) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                TreeItem<TreeElem> child;
                var result = this.getTitle(entry.getValue());
                var title = result.getKey();
                var truncated = result.getValue();

                var elem = new TreeElem(entry.getKey(), entry.getValue(), entry.getKey() + ": " + title);

                if (entry.getValue().isObject()) {
                    child = new TreeItem<>(elem);
                    addJsonToTree(entry.getValue(), child);
                } else if (entry.getValue().isArray()) {
                    child = new TreeItem<>(elem);
                    addJsonToTree(entry.getValue(), child);
                } else {
                    if (truncated) { // truncated
                        child = new TreeItem<>(elem);
                        var childElem = new TreeElem(entry.getKey(), entry.getValue(), entry.getValue().asText());

                        var rest = new TreeItem<>(childElem);
                        child.getChildren().add(rest);
                    }
                    else {
                        child = new TreeItem<>(elem);
                    }
                }
                parent.getChildren().add(child);
            });
        } else if (node.isArray()) {
            int index = 0;
            for (var element : node) {
                TreeItem<TreeElem> child;
                var result = this.getTitle(element);
                var title = result.getKey();
                var truncated = result.getValue();
                var elem = new TreeElem(null, element, "[" + index + "]: " + title);

                if (element.isObject() || element.isArray()) {
                    child = new TreeItem<>(elem);
                    addJsonToTree(element, child);
                } else {
                    if (truncated) {
                        child = new TreeItem<>(elem);
                        var childElem = new TreeElem(null, element, element.asText());

                        var rest = new TreeItem<>(childElem);
                        child.getChildren().add(rest);
                    }
                    else {
                        child = new TreeItem<>(elem);
                    }
                }
                parent.getChildren().add(child);
                index++;
            }
        }
    }
}
