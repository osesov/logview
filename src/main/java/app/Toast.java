package app;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Toast
{
    private static Stage ownerStage = null;

    public static void setOwnerStage(Stage stage) {
        ownerStage = stage;
    }

    public static void show(String message, int durationMillis) {
        Popup popup = new Popup();

        Label label = new Label(message);
        label.setStyle("""
            -fx-background-color: rgba(0, 0, 0, 0.8);
            -fx-text-fill: white;
            -fx-padding: 10px;
            -fx-font-size: 14px;
            -fx-background-radius: 8px;
        """);

        popup.getContent().add(label);
        popup.setAutoFix(true);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        // Position bottom-center of owner stage
        popup.show(ownerStage);
        double x = ownerStage.getX() + (ownerStage.getWidth() - label.getWidth()) / 2;
        double y = ownerStage.getY() + ownerStage.getHeight() - 100;
        popup.setX(x);
        popup.setY(y);

        // Fade out
        Timeline fadeTimeline = new Timeline(
            new KeyFrame(Duration.millis(durationMillis), e -> popup.hide())
        );
        fadeTimeline.play();
    }
}
