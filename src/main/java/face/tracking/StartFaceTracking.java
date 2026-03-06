package face.tracking;


import nu.pattern.OpenCV;


public class StartFaceTracking  {

    public static void start() {
        System.setProperty("opencv.videoio.log_level","3");

        try
        {
            OpenCV.loadLocally();

            TrackingManager.getInstance().init();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void main() {
        // load the native OpenCV library
        nu.pattern.OpenCV.loadLocally();

        // 2. Dann die OpenCV-Objekte im Manager vorbereiten
        TrackingManager.getInstance().setup();
        start();
    }
}
