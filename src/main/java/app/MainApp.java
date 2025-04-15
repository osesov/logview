package app;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Application;
import javafx.geometry.Orientation;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Stage;


public class MainApp extends Application {

    private ObjectMapper mapper = new ObjectMapper();
    private TreeViewController treeController = new TreeViewController(this.mapper);
    private TableViewController tableController = new TableViewController(this.mapper, treeController);
    private TextField searchField = new TextField();
    private Label statusBar = new Label("Ready");
    private JsonLineReader jsonLineReader;

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();

        Button openButton = new Button("Open JSONL File");
        openButton.setOnAction(e -> openFile(primaryStage));

        // # top bar
        HBox topBar = new HBox(openButton);
        topBar.setSpacing(10);
        topBar.setPadding(new Insets(5));

        // ## go to row button
        TextField goToField = new TextField();
        goToField.setPromptText("Go to row #");
        goToField.setPrefWidth(100);
        topBar.getChildren().add(goToField);
        goToField.setOnAction(e -> {
            try {
                int row = Integer.parseInt(goToField.getText());
                tableController.scrollToRow(row - 1);
                goToField.clear();
            } catch (Exception ignored) {}
        });

        // ## search field
        searchField.setPromptText("Search (key or value)...");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            tableController.setFilter(newVal);
        });
        topBar.getChildren().add(searchField);
        searchField.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                searchField.clear();
                tableController.focus();  // implement this to call table.requestFocus()
            }
        });

        // it takes the rest of the space
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setMaxWidth(Double.MAX_VALUE);

        // # middle area
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(
            tableController.getView(),
            treeController.getView()
        );
        splitPane.setDividerPositions(AppSettings.loadDividerPosition(0.5));

        // # status bar
        HBox statusContainer = new HBox(statusBar);
        statusContainer.setPadding(new Insets(5));
        statusContainer.setStyle("-fx-background-color: #eeeeee; -fx-border-color: #cccccc;");

        // # set up the main layout
        root.setTop(topBar);
        root.setCenter(splitPane);
        root.setBottom(statusContainer);

        // load app settings
        double[] bounds = AppSettings.loadWindowBounds(1200, 800);

        Scene scene = new Scene(root, 1200, 800);

        if (!Double.isNaN(bounds[0])) {
            primaryStage.setX(bounds[0]);
            primaryStage.setY(bounds[1]);
        }
        primaryStage.setWidth(bounds[2]);
        primaryStage.setHeight(bounds[3]);

        // add keyboard shortcuts
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SLASH && !event.isControlDown() && !event.isAltDown()) {
                event.consume();
                searchField.requestFocus();
                searchField.selectAll();
            }
        });

        primaryStage.setTitle("JSONL Viewer");
        primaryStage.setScene(scene);
        primaryStage.show();

        tableController.focus();

        this.initialLoadFile();

        // save state on exit
        primaryStage.setOnCloseRequest(e -> {
            AppSettings.saveDividerPosition(splitPane.getDividerPositions()[0]);
            AppSettings.saveWindowBounds(primaryStage.getX(), primaryStage.getY(), primaryStage.getWidth(), primaryStage.getHeight());
        });
    }

    private void openFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open JSON Lines File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JSONL Files", "*.jsonl", "*.json")
        );
        File selectedFile = fileChooser.showOpenDialog(stage);
        this.tryToOpenFile(selectedFile.getAbsolutePath(), true);
    }

    private boolean tryToOpenFile(String fileName, boolean saveToSettings)
    {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }

        Path filePath = Path.of(fileName);
        if (!Files.exists(filePath))
            return false;

        jsonLineReader = new JsonLineReader(filePath);
        jsonLineReader.readLines(tableController, statusBar);
        if (saveToSettings)
            AppSettings.saveLastOpenedFile(fileName);

        return true;
    }

    private void initialLoadFile()
    {
        // load file from parameters
        List<String> args = getParameters().getUnnamed();

        if (this.tryToOpenFile(args.isEmpty() ? null : args.get(0), true))
            return;

        // Reload last file
        String lastFile = AppSettings.loadLastOpenedFile();
        this.tryToOpenFile(lastFile, false);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
