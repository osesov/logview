package app;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

public class TreeViewController {

    public static final record TreeElem (
        String nodeName,
        JsonNode node,
        String title,
        Integer elements,
        Integer byteSize
    ) {}

    private TreeView<TreeElem> tree = new TreeView<>(new TreeItem<TreeElem>(null));
    private StringProperty currentSearch = new SimpleStringProperty();
    private BooleanProperty showElements = new SimpleBooleanProperty(false);
    private BooleanProperty showByteSize = new SimpleBooleanProperty(false);
    private List<TreeItem<TreeElem>> matches = new ArrayList<>();
    private int matchIndex = 0;

    public TreeViewController() {
        tree.setShowRoot(false);
        tree.setFixedCellSize(-1); // Allow variable row heights

        // tree.setCellFactory( tv -> new HighlightingTreeCell(currentSearch));

        tree.setCellFactory( tv -> new TreeCell<>() {
            private final TextField textField = new TextField();
            private final TextArea textArea = new TextArea();
            private final Text prefixText = new Text("[P] ");
            private final HBox hbox = new HBox(5);

            {
                // textField.setEditable(false);
                textField.setFocusTraversable(false);
                textField.setStyle("""
                    -fx-background-color: transparent;
                    -fx-border-color: transparent;
                    -fx-padding: 0;
                    -fx-font-size: 14px;
                    // -fx-font-family: Monospaced;
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
                    -fx-font-size: 14px;
                    // -fx-font-family: Monospaced;

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

                prefixText.setStyle("""
                    -fx-font-family: Monospaced;
                    -fx-font-size: 14px;
                    -fx-font-weight: bold;
                    -fx-fill:rgb(179, 175, 205);
                    """
                );
            }

            @Override
            protected void updateItem(TreeElem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                } else {

                    TextInputControl textControl;
                    Node disclosureNode = getDisclosureNode();

                    String title = item.title;
                    String tooltip;
                    String prefix = null;

                    boolean hasElements = item.elements != null && item.elements != null && showElements.get();
                    boolean hasByteSize = item.byteSize != null && item.byteSize >= 0 && showByteSize.get();

                    if (hasElements && hasByteSize) {
                        // title = String.format("(%d elements, %d bytes) %s", item.elements, item.byteSize, title);
                        tooltip = String.format("(%d elements, %d bytes)", item.elements, item.byteSize);
                        prefix = String.format("[%2s:%8s]", item.elements, humanReadableSize(item.byteSize));
                    } else if (hasElements) {
                        // title = String.format("(%d elements) %s", item.elements, item.byteSize, title);
                        tooltip = String.format("(%d elements)", item.elements, item.byteSize);
                        prefix = String.format("[%2s]", item.elements);
                    } else if (hasByteSize) {
                        // title = String.format("(%d bytes) %s", item.byteSize, title);
                        tooltip = String.format("(%d bytes)", item.byteSize);
                        prefix = String.format("[%8s]", humanReadableSize(item.byteSize));
                    } else {
                        tooltip = null;
                        prefix = null;
                    }

                    setText(null);
                    if (title.contains("\n")) {
                        textArea.setText(title);

                        hbox.getChildren().clear();
                        hbox.getChildren().addAll(prefixText, textArea);
                        setGraphic(hbox);
                        // setGraphic(textArea);

                        int lineCount = (int) title.chars().filter(ch -> ch == '\n').count() + 1;
                        if (lineCount > 20)
                            lineCount = 20;

                        textArea.setPrefRowCount(lineCount);
                        textControl = textArea;
                    } else {
                        textField.setText(title);
                        // setGraphic(textField);
                        textControl = textField;

                        hbox.getChildren().clear();
                        hbox.getChildren().addAll(prefixText, textField);
                        setGraphic(hbox);
                    }

                    if (prefix == null || prefix.isEmpty()) {
                        prefix = "";
                    }

                    prefixText.setText(prefix);
                    String query = currentSearch.get();
                    if (query != null && !query.isEmpty()) {
                        int index = item.node.toString().toLowerCase().indexOf(query.toLowerCase());
                        if (index >= 0) {
                            textControl.selectRange(index, index + query.length());
                        } else {
                            textControl.deselect();
                        }
                    } else {
                        textControl.deselect();
                    }

                    if (tooltip == null || tooltip.isEmpty()) {
                        setTooltip(null);
                    } else {
                        Tooltip tooltipNode = new Tooltip(tooltip);

                        tooltipNode.setShowDelay(Duration.millis(100));
                        tooltipNode.setShowDuration(Duration.seconds(5));
                        tooltipNode.setHideDelay(Duration.seconds(1));

                        // setTooltip(tooltipNode);
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

    private static record Title(
        String title,
        boolean truncated,
        Integer elements,
        Integer byteSize
    ) {}

    private Title getTitle(JsonNode node) {
        String title;
        boolean truncated = false;
        Integer elements = null;
        Integer byteSize = null;

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

            elements = node.size();
            byteSize = s.length();
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

            byteSize = text.length();
        }

        return new Title(title, truncated, elements, byteSize);
    }

    private void addJsonToTree(JsonNode node, TreeItem<TreeElem> parent) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                TreeItem<TreeElem> child;
                Title result = this.getTitle(entry.getValue());
                String title = result.title();
                Boolean truncated = result.truncated();
                Integer elements = result.elements();
                Integer byteSize = result.byteSize();

                TreeElem elem = new TreeElem(
                    entry.getKey(),
                    entry.getValue(),
                    entry.getKey() + ": " + title,
                    elements,
                    byteSize
                    );

                if (entry.getValue().isObject()) {
                    child = new TreeItem<>(elem);
                    addJsonToTree(entry.getValue(), child);
                } else if (entry.getValue().isArray()) {
                    child = new TreeItem<>(elem);
                    addJsonToTree(entry.getValue(), child);
                } else {
                    if (truncated) { // truncated
                        child = new TreeItem<>(elem);
                        var childElem = new TreeElem(entry.getKey(), entry.getValue(), entry.getValue().asText(), elements, byteSize);

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
                String title = result.title();
                Boolean truncated = result.truncated();
                Integer elements = result.elements();
                Integer byteSize = result.byteSize();
                var elem = new TreeElem(null, element, "[" + index + "]: " + title, elements, byteSize);

                if (element.isObject() || element.isArray()) {
                    child = new TreeItem<>(elem);
                    addJsonToTree(element, child);
                } else {
                    if (truncated) {
                        child = new TreeItem<>(elem);
                        var childElem = new TreeElem(null, element, element.asText(), elements, byteSize);

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

    public static String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";

        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B"; // KB, MB, GB, etc.

        double size = bytes / Math.pow(1024, exp);

        return String.format("%.1f %s", size, unit);
    }

    public void setShowElements(Boolean newVal) {
        this.showElements.set(newVal);
        tree.refresh();
    }

    public void setShowByteSize(Boolean newVal) {
        this.showByteSize.set(newVal);
        tree.refresh();
    }
}
