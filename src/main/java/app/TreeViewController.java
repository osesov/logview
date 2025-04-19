package app;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Pair;

public class TreeViewController {

    public static final record TreeElem (String nodeName, JsonNode node, String title) {}

    private TreeView<TreeElem> tree = new TreeView<>(new TreeItem<TreeElem>(null));
    private StringProperty currentSearch = new SimpleStringProperty();
    private List<TreeItem<TreeElem>> matches = new ArrayList<>();
    private int matchIndex = 0;

    public TreeViewController() {
        tree.setShowRoot(false);
        tree.setFixedCellSize(-1); // Allow variable row heights

        // tree.setCellFactory( tv -> new HighlightingTreeCell(currentSearch));

        tree.setCellFactory( tv -> new TreeCell<>() {
            private final TextField textField = new TextField();
            private final TextArea textArea = new TextArea();

            {
                // textField.setEditable(false);
                textField.setFocusTraversable(false);
                textField.setStyle("""
                    -fx-background-color: transparent;
                    -fx-border-color: transparent;
                    -fx-padding: 0;
                    -fx-font-size: 12px;
                    """);
                textField.setVisible(true);
                textField.setOpacity(1.0);
                textField.setMaxWidth(Double.MAX_VALUE);


                // TextArea
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setFocusTraversable(false);
                textArea.setStyle("""
                    -fx-background-color: transparent;
                    -fx-border-color: transparent;
                    // -fx-padding: 0;
                    -fx-font-size: 12px;

                    -fx-border-color: #888;
                    -fx-border-width: 1;
                    -fx-border-radius: 4;
                    -fx-padding: 4;
                """);
                // textArea.setMaxHeight(Double.MAX_VALUE); // allow growth
                textArea.setMaxWidth(Double.MAX_VALUE);
                textArea.setPrefRowCount(1); // for initial size
                textArea.setMinHeight(Region.USE_PREF_SIZE);
                textArea.setMaxHeight(Region.USE_COMPUTED_SIZE);

                // Copy by Right Click
                textField.setOnContextMenuRequested(e -> {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    TreeElem item = getItem();

                    String text = Optional.ofNullable(textField.getSelection())
                    .filter(it -> it.getLength() > 0)
                    .map(it -> textField.getSelectedText())
                    .orElseGet(() -> {
                        return item.node.toPrettyString();
                    });

                    if (text == null || text.isEmpty()) {
                        return;
                    }

                    content.putString(text);
                    clipboard.setContent(content);

                    if (text.length() > 200) {
                        text = text.substring(0, 200) + "...";
                    }

                    Toast.show("Copied to clipboard: " + text, 2000);
                });

                textField.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                        TreeItem<TreeElem> item = getTreeItem();
                        if (item != null && !item.isLeaf()) {
                            item.setExpanded(!item.isExpanded());
                            event.consume(); // prevent bubbling if needed
                        }
                    }
                });

                textField.setOnKeyPressed(event -> {
                    if (event.getCode().isArrowKey()) {
                        event.consume(); // prevent bubbling if needed
                    }
                });

                // textField.setAlignment(Pos.CENTER_LEFT);
                textField.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(textField, Priority.ALWAYS);
            }

            @Override
            protected void updateItem(TreeElem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(null);
                    if (item.title.contains("\n")) {
                        textArea.setText(item.title);
                        setGraphic(textArea);

                        int lineCount = (int) item.title.chars().filter(ch -> ch == '\n').count() + 1;
                        if (lineCount > 20)
                            lineCount = 20;

                        textArea.setPrefRowCount(lineCount);
                    } else {
                        textField.setText(item.title);
                        setGraphic(textField);
                    }

                    // textField.setItem(item.title);
                    // setGraphic(highlightMatch(item.title, "file"));
                }
            }
        });
    }

    private StackPane view;

    public StackPane getView() {
        if (view != null)
            return view;

        view = new StackPane();
        VBox box = new VBox(tree);
        VBox.setVgrow(tree, Priority.ALWAYS);  // make TreeView grow inside VBox
        box.setPadding(new Insets(5));
        view.getChildren().add(box);
        return view;
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

    public void onSearch(String searchString)
    {
        String query = searchString.toLowerCase();
        TreeItem<TreeElem> root = tree.getRoot();

        if (query == null || query.isEmpty()) {
            // collapseAll(root, true);
            currentSearch.set("");
            matches.clear();
            matchIndex = 0;
            return;
        }

        if (query.equals(currentSearch.get())) {
            // next match
            matchIndex = (matchIndex + 1) % matches.size(); // loop around
            TreeItem<TreeElem> next = matches.get(matchIndex);

            expandPathTo(next);
            tree.getSelectionModel().select(next);
            tree.scrollTo(tree.getRow(next));

            return;
        }

        // collapseAll(root, true);
        matches = new ArrayList<>();
        findMatches(root, query.toLowerCase(), matches);

        for (TreeItem<TreeElem> match : matches) {
            expandPathTo(match);
        }

        if (!matches.isEmpty()) {
            tree.getSelectionModel().select(matches.get(0));
            tree.scrollTo(tree.getRow(matches.get(0)));
        }

        currentSearch.set(query);
        matchIndex = 0;
    }

    private void collapseAll(TreeItem<?> item, boolean isRoot) {
        if (!isRoot)
            item.setExpanded(false);
        for (TreeItem<?> child : item.getChildren()) {
            System.err.println(child.getValue());
            collapseAll(child, false);
        }
    }

    private void expandPathTo(TreeItem<?> item) {
        TreeItem<?> parent = item.getParent();
        while (parent != null) {
            parent.setExpanded(true);
            parent = parent.getParent();
        }
    }

    private void findMatches(TreeItem<TreeElem> item, String query, List<TreeItem<TreeElem>> matches) {
        TreeElem value = item.getValue();
        if (value != null) {
            String s = value.node.toString();

            if (s.toLowerCase().contains(query)) {
                matches.add(item);
            }
        }

        for (TreeItem<TreeElem> child : item.getChildren()) {
            findMatches(child, query, matches);
        }
    }

    private TextFlow highlightMatch(String text, String query) {
        TextFlow flow = new TextFlow();

        if (query == null || query.isEmpty()) {
            flow.getChildren().add(new Text(text));
            return flow;
        }

        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();

        int matchIndex = lowerText.indexOf(lowerQuery);
        if (matchIndex < 0) {
            flow.getChildren().add(new Text(text));
            return flow;
        }

        Text before = new Text(text.substring(0, matchIndex));
        Text match = new Text(text.substring(matchIndex, matchIndex + query.length()));
        Text after = new Text(text.substring(matchIndex + query.length()));

        match.setStyle("-fx-fill: red; -fx-font-weight: bold;");

        flow.getChildren().addAll(before, match, after);
        return flow;
    }

    public void searchNext() {
        if (matches.isEmpty()) {
            return;
        }

        // next match
        matchIndex = (matchIndex + 1) % matches.size(); // loop around
        TreeItem<TreeElem> next = matches.get(matchIndex);

        expandPathTo(next);
        tree.getSelectionModel().select(next);
        tree.scrollTo(tree.getRow(next));
    }

    public void searchPrevious() {
        if (matches.isEmpty()) {
            return;
        }

        // previous match
        matchIndex = (matchIndex - 1 + matches.size()) % matches.size(); // loop around
        TreeItem<TreeElem> next = matches.get(matchIndex);

        expandPathTo(next);
        tree.getSelectionModel().select(next);
        tree.scrollTo(tree.getRow(next));
    }

}
