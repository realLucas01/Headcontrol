package faceTracking;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.fxml.FXMLLoader;
import org.opencv.core.Core;

import javafx.event.EventHandler;
import javafx.stage.WindowEvent;


public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try
        {
            // load the FXML resource
            FXMLLoader loader = new FXMLLoader(getClass().getResource("FirstFX.fxml"));
            BorderPane root = (BorderPane) loader.load();
            // create and style a scene
            Scene scene = new Scene(root, 800, 600);
            // scene
            primaryStage.setTitle("Face Detection");
            primaryStage.setScene(scene);
            // show the GUI
            primaryStage.show();

            // init the controller
            FXController controller = loader.getController();
            controller.init();

            // set the proper behavior on closing the application
            primaryStage.setOnCloseRequest((new EventHandler<WindowEvent>() {
                public void handle(WindowEvent we)
                {
                    controller.setClosed();
                }
            }));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // load the native OpenCV library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        launch(args);
    }
}
