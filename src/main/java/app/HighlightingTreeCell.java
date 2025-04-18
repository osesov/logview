package app;

import java.util.Optional;

import javafx.beans.property.StringProperty;
import javafx.scene.control.IndexRange;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class HighlightingTreeCell extends TreeCell<TreeViewController.TreeElem> {
    private final TextField textField = new TextField();
    private final StringProperty searchQuery;

    public HighlightingTreeCell(StringProperty searchQuery) {
        this.searchQuery = searchQuery;

        textField.setEditable(false);
        textField.setFocusTraversable(true);
        textField.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0;");

        // If user clicks to select, keep TextField shown
        textField.focusedProperty().addListener((obs, old, isFocused) -> {
            if (!isFocused) {
                getTreeView().refresh(); // switch back to highlight mode
            }
        });

        // Optional: expand/collapse on double click
        // textField.setOnMouseClicked(e -> {
        //     if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
        //         TreeItem<String> item = getTreeItem();
        //         if (item != null && !item.isLeaf()) {
        //             item.setExpanded(!item.isExpanded());
        //         }
        //     }
        // });
    }

    @Override
    protected void updateItem(TreeViewController.TreeElem item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        setText(null); // always null to avoid overlapping

        if (isFocused() || textField.isFocused()) {
            textField.setText(item.title()  );
            highlightInTextField(textField, item.title(), searchQuery.get());
            setGraphic(textField);
        } else {
            setGraphic(buildHighlightFlow(item.title(), searchQuery.get()));
        }
    }

    // Optional selection highlight in TextField
    private void highlightInTextField(TextField field, String text, String query) {
        if (query == null || query.isEmpty()) {
            field.deselect();
            return;
        }

        int index = text.toLowerCase().indexOf(query.toLowerCase());
        if (index >= 0) {
            field.selectRange(index, index + query.length());
        } else {
            field.deselect();
        }
    }

    // Styled TextFlow with highlighted match
    private TextFlow buildHighlightFlow(String text, String query) {
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

    public Optional<String> getSelection()
    {
        IndexRange selection = textField.getSelection();
        return selection.getLength() == 0 ? Optional.empty() : Optional.ofNullable(textField.getSelectedText());
    }
}
