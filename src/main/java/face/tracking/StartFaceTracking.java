package face.tracking;

public class StartFaceTracking  {

    public static void start() throws Exception {


            TrackingManager.getInstance().init();

    }

    public static void main() throws Exception {
        // load the native OpenCV library
        //nu.pattern.OpenCV.loadLocally();

        // 2. Dann die OpenCV-Objekte im Manager vorbereiten
        TrackingManager.getInstance().start(0);
        start();
    }
}
