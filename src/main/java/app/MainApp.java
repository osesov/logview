package app;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
import javafx.stage.FileChooser;
import javafx.stage.Stage;


public class MainApp extends Application
{
    private TreeViewController treeViewController = new TreeViewController();
    private TableViewController tableViewController = new TableViewController(treeViewController);
    private FilterViewController filterViewController = new FilterViewController();
    private FileListController fileListController = new FileListController(tableViewController);
    private TextField searchField = new TextField();
    private Label statusBar = new Label("Ready");
    private JsonLineReader jsonLineReader;

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();

        Node statusContainer = this.buildStatusBar();
        MiddleArea middleArea = this.buildMiddleArea();
        Node topBarContainer = this.buildToolBar(primaryStage, middleArea);

        // wire up the controllers

        // # set up the main layout
        root.setTop(topBarContainer);
        root.setCenter(middleArea.getView());
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

        primaryStage.setTitle("JSON Viewer");
        primaryStage.setScene(scene);
        primaryStage.show();
        jsonLineReader = new JsonLineReader(tableViewController, statusBar, fileListController);
        tableViewController.reset(jsonLineReader);

        tableViewController.focus();

        this.initialLoadFile();

        // save state on exit
        primaryStage.setOnCloseRequest(e -> {
            middleArea.saveState();
            AppSettings.saveWindowBounds(primaryStage.getX(), primaryStage.getY(), primaryStage.getWidth(), primaryStage.getHeight());
            filterViewController.saveRulesToPreferences();
            filterViewController.saveColumnLayout();
            tableViewController.saveColumnLayout();
        });

        // apply filters when cells are edited
        filterViewController.setOnRulesChanged(() -> {
            tableViewController.applyFilters(filterViewController.getRules());
        });

        // apply filters when a rule is added or removed
        filterViewController.getRules().addListener((ListChangeListener<? super FilterRule>) c -> {
            tableViewController.applyFilters(filterViewController.getRules());
        });

        // apply filters when search field is changed
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            tableViewController.applyFilters(filterViewController.getRules());
        });

        fileListController.setOnFileOpen(() -> openFile(primaryStage));
        fileListController.setOnFileClose((String fileName) -> {
            jsonLineReader.removeFile(fileName);
        });

        filterViewController.loadRulesFromPreferences();
        filterViewController.loadColumnLayout();
        tableViewController.loadColumnLayout();
        tableViewController.applyFilters(filterViewController.getRules());
        middleArea.loadState();
    }

    private Node buildToolBar(Stage primaryStage, MiddleArea middleArea)
    {
        // # top bar
        String buttonText[] = {"Explorer »", "« Hide Explorer"};

        boolean explorerState = middleArea.isExplorerVisible();
        Button toggleExplorerTab = new Button(buttonText[explorerState ? 1 : 0]);

        toggleExplorerTab.setOnAction(e -> {
            if (middleArea.toggleExplorer()) {
                toggleExplorerTab.setText(buttonText[1]);
            }
            else {
                toggleExplorerTab.setText(buttonText[0]);
            }
        });

        // ## go to row button
        TextField goToField = new TextField();
        goToField.setPromptText("Go to row #");
        goToField.setPrefWidth(100);
        goToField.setOnAction(e -> {
            try {
                int row = Integer.parseInt(goToField.getText());
                tableViewController.scrollToRow(row - 1);
                goToField.clear();
            } catch (Exception ignored) {}
        });

        // ## search field
        searchField.setPromptText("Search (key or value)...");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            tableViewController.setSearchString(newVal);
        });
        searchField.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                searchField.clear();
                tableViewController.focus();  // implement this to call table.requestFocus()
            }

            if (ev.getCode() == KeyCode.ENTER && ev.isControlDown()) {
                tableViewController.focus();
            }

            if (ev.getCode() == KeyCode.ENTER && !ev.isControlDown() && !ev.isAltDown()) {
                tableViewController.selectNextMatch();
            }

        });

        // it takes the rest of the space
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setMaxWidth(Double.MAX_VALUE);

        // wrap it finally
        HBox topBarContainer = new HBox(toggleExplorerTab, goToField, searchField);
        topBarContainer.setSpacing(10);
        topBarContainer.setPadding(new Insets(5));

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

    private MiddleArea buildMiddleArea()
    {
        return new MiddleArea(
            fileListController,
            filterViewController,
            tableViewController,
            treeViewController
        );
    }

    private void openFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open JSON File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JSONL Files", "*.jsonl", "*.json")
        );
        List<File> selectedFile = fileChooser.showOpenMultipleDialog(stage);
        if (selectedFile == null || selectedFile.isEmpty())
            return;

        List<String> files = selectedFile.stream()
                .map(file -> file.getAbsolutePath())
                .toList();

        this.tryToOpenFile(files, true);
    }

    private boolean tryToOpenFile(List<String> files, boolean saveToSettings)
    {
        if (files == null || files.isEmpty())
            return false;

        for (String fileName : files) {
            if (fileName == null)
                continue;

            Path filePath = Path.of(fileName);
            if (!Files.exists(filePath))
                return false;

            jsonLineReader.addFile(filePath);
        }

        if (saveToSettings) {
            AppSettings.saveLastOpenedFile(fileListController.getOpenFiles());
        }

        return true;
    }

    private void initialLoadFile()
    {
        // load file from parameters
        List<String> args = getParameters().getUnnamed();

        if (this.tryToOpenFile(args, true))
            return;

        // Reload last file
        List<String> files = AppSettings.loadLastOpenedFile();
        this.tryToOpenFile(files, false);
    }

    public static void main(String[] args) {
        launch(args);
    }

}
