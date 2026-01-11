package face.tracking;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;

import javafx.event.EventHandler;
import javafx.stage.WindowEvent;
import nu.pattern.OpenCV;


public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try
        {
            OpenCV.loadLocally(); // oder loadShared()

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/FirstFX.fxml"));
            Parent root = loader.load();


            // load the FXML resource
            //FXMLLoader loader = new FXMLLoader(getClass().getResource("/FirstFX.fxml"));
            //BorderPane root = (BorderPane) loader.load();
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

        launch(args);
    }
}
