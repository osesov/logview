package app;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class ExplorerTab
{
    private TabPane tabPane = new TabPane();

    ExplorerTab(FileListController fileListController, FilterViewController filterViewController)
    {
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab filesTab = new Tab("Files", fileListController.getView());
        Tab filtersTab = new Tab("Filters", filterViewController.getView());

        // Add both
        tabPane.getTabs().addAll(filesTab, filtersTab);
    }

    public TabPane getPane()
    {
        return tabPane;
    }

}
