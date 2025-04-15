package app;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import app.FilterViewController.FilterRule;
import javafx.application.Application;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Insets;
import javafx.scene.Node;
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
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;


public class MainApp extends Application {

    private ObjectMapper mapper = new ObjectMapper();
    private TreeViewController treeController = new TreeViewController(this.mapper);
    private TableViewController tableController = new TableViewController(this.mapper, treeController);
    private FilterViewController filterController = new FilterViewController();
    private TextField searchField = new TextField();
    private Label statusBar = new Label("Ready");
    private SplitPane tablePane;
    private JsonLineReader jsonLineReader;

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();

        Node topBarContainer = this.buildToolBar(primaryStage);
        Node statusContainer = this.buildStatusBar();
        SplitPane middleArea = this.buildMiddleArea();


        // # set up the main layout
        root.setTop(topBarContainer);
        root.setCenter(middleArea);
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
            AppSettings.saveDividerPosition(middleArea.getDividerPositions()[0]);
            AppSettings.saveWindowBounds(primaryStage.getX(), primaryStage.getY(), primaryStage.getWidth(), primaryStage.getHeight());
            filterController.saveRulesToPreferences(mapper);
            filterController.saveColumnLayout(mapper);
            tableController.saveColumnLayout(mapper);
            double dividers[] = tablePane.getDividerPositions();
            if (dividers.length > 0)
                AppSettings.saveFilterDividerPosition(dividers[0]);
        });

        tableController.applyFilters(filterController.getRules());
    }

    private Node buildToolBar(Stage primaryStage)
    {
        // # top bar
        Button openButton = new Button("Open JSONL File");
        openButton.setOnAction(e -> openFile(primaryStage));


        HBox topBarContainer = new HBox(openButton);
        topBarContainer.setSpacing(10);
        topBarContainer.setPadding(new Insets(5));

        // ## go to row button
        TextField goToField = new TextField();
        goToField.setPromptText("Go to row #");
        goToField.setPrefWidth(100);
        topBarContainer.getChildren().add(goToField);
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
            tableController.setSearchString(newVal);
        });
        topBarContainer.getChildren().add(searchField);
        searchField.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                searchField.clear();
                tableController.focus();  // implement this to call table.requestFocus()
            }

            if (ev.getCode() == KeyCode.ENTER && ev.isControlDown()) {
                tableController.focus();
            }

            if (ev.getCode() == KeyCode.ENTER && !ev.isControlDown() && !ev.isAltDown()) {
                tableController.selectNextMatch();
            }

        });

        // it takes the rest of the space
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setMaxWidth(Double.MAX_VALUE);

        // filter button
        VBox filterView = filterController.getView();

        Button toggleFilter = new Button("Filters »");
        topBarContainer.getChildren().add(toggleFilter);

        // show/hide filters
        toggleFilter.setOnAction(e -> {
            if (tablePane.getItems().contains(filterView)) {
                tablePane.getItems().remove(filterView);
                AppSettings.saveFilterDividerPosition(tablePane.getDividerPositions()[0]);

                toggleFilter.setText("Filters »");
            } else {
                tablePane.getItems().add(filterView);
                tablePane.setDividerPositions(AppSettings.loadFilterDividerPosition(0.66));
                toggleFilter.setText("« Hide Filters");
            }
        });

        return topBarContainer;
    }

    private Node buildStatusBar()
    {
        // # status bar
        HBox statusContainer = new HBox(statusBar);
        statusContainer.setPadding(new Insets(5));
        statusContainer.setStyle("-fx-background-color: #eeeeee; -fx-border-color: #cccccc;");

        return statusContainer;
    }

    private SplitPane buildMiddleArea()
    {
        // ## Table/Filter area
        tablePane = new SplitPane();
        tablePane.setOrientation(Orientation.HORIZONTAL);
        tablePane.getItems().addAll(tableController.getView() /* filterView hidden by default */);
        tablePane.setDividerPositions(1.0); // full width to TableView initially


        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(
            tablePane, // tableController.getView(),
            treeController.getView()
        );
        splitPane.setDividerPositions(AppSettings.loadDividerPosition(0.5));

        // apply filters when cells are edited
        filterController.setOnRulesChanged(() -> {
            tableController.applyFilters(filterController.getRules());
        });

        // apply filters when a rule is added or removes
        filterController.getRules().addListener((ListChangeListener<? super FilterRule>) c -> {
            tableController.applyFilters(filterController.getRules());
        });

        // apply filters when search field is changed
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            tableController.applyFilters(filterController.getRules());
        });

        filterController.loadRulesFromPreferences(mapper);
        filterController.loadColumnLayout(mapper);
        tableController.loadColumnLayout(mapper);

        return splitPane;
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
