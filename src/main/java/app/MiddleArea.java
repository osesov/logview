package app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;

public class MiddleArea
{

    private SplitPane mainPane;
    ExplorerTab explorerTab;
    double dividerPos = 0.2;

    MiddleArea(
        FileListController fileListController,
        FilterViewController filterViewController,
        TableViewController tableViewController,
        TreeViewController treeViewController
    )
    {
        // ExplorerTab | TableView
        //             |-
        //             | TreeView

        mainPane = new SplitPane();
        explorerTab = new ExplorerTab(fileListController, filterViewController);

        mainPane.setOrientation(Orientation.HORIZONTAL);
        mainPane.getItems().add(explorerTab.getPane());

        SplitPane tablePane = new SplitPane();
        tablePane.setOrientation(Orientation.VERTICAL);
        tablePane.getItems().add(tableViewController.getView());
        tablePane.getItems().add(treeViewController.getView());

        mainPane.getItems().add(tablePane);
        mainPane.setDividerPositions(dividerPos);
    }

    public Node getView() {
        return mainPane;
    }

    public void saveState()
    {
        Map<String, Object> state = new LinkedHashMap<>();

        state.put("dividerPos", isExplorerVisible() ? mainPane.getDividerPositions()[0] : dividerPos);
        AppSettings.saveJson("mainView", state);
    }

    public void loadState()
    {
        JsonNode json = AppSettings.loadJson("mainView");
        if (json == null) return;
        double position = json.at("/dividerPos").asDouble(0.2);
        mainPane.setDividerPositions(position);
        dividerPos = position;
    }

    public boolean isExplorerVisible()
    {
        List<Node> items = mainPane.getItems();
        Node tabPane = explorerTab.getPane();

        return items.contains(tabPane);
    }

    public boolean toggleExplorer()
    {
        List<Node> items = mainPane.getItems();
        Node tabPane = explorerTab.getPane();

        if (items.contains(tabPane)) {
            dividerPos = mainPane.getDividerPositions()[0];
            items.remove(tabPane);
            return false;
        }
        else {
            items.add(0, tabPane);
            mainPane.setDividerPositions(dividerPos);
            return true;
        }
    }
}
