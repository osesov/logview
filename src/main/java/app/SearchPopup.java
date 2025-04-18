package app;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

public class SearchPopup
{
    private static TextField searchField = new TextField();
    private static HBox overlayBox = new HBox(searchField);
    private static StackPane stackPane;

    public static void start(Pane rootPane)
    {
        overlayBox.setAlignment(Pos.TOP_RIGHT);
        overlayBox.setPadding(new Insets(10));
        overlayBox.setMouseTransparent(false); // so it can receive clicks
        overlayBox.setVisible(false); // hidden by default

        searchField.setPromptText("Search...");
        searchField.setStyle("-fx-background-color: white; -fx-border-color: gray;");
        searchField.setMaxWidth(400);

        // will be attached to pane later
        // rootPane.getChildren().add(overlayBox);
    }

    public static void show(StackPane stackPane, Consumer<String> onSearch)
    {
        // overlayBox.setVisible(true);
        // searchField.requestFocus();
        // searchField.selectAll();

        if (SearchPopup.stackPane != null) {
            SearchPopup.stackPane.getChildren().remove(overlayBox);
        }

        stackPane.getChildren().add(overlayBox);
        StackPane.setAlignment(overlayBox, Pos.TOP_RIGHT);

        searchField.getParent().setVisible(true);
        searchField.requestFocus();
        searchField.selectAll();

        searchField.setOnAction(e -> onSearch.accept(searchField.getText()));

        SearchPopup.stackPane = stackPane;
    }

    public static void hide()
    {
        if (SearchPopup.stackPane == null) {
            return;
        }

        // overlayBox.setVisible(false);
        searchField.getParent().setVisible(false);
        SearchPopup.stackPane.getChildren().remove(overlayBox);
        SearchPopup.stackPane = null;
    }

}
