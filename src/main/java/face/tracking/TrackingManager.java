package face.tracking;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.core.Size;

import org.opencv.videoio.Videoio;


/**
 * FXController steuert die Benutzeroberfläche und die Logik für das Face-Tracking
 * Es nutzt OpenCV für die Bildverarbeitung und YuNet für die Gesichtserkennung
 */
public class TrackingManager {
    HeadTrackingLogic trackingLogic = HeadTrackingLogic.getInstance();
    private static TrackingManager INSTANCE ;
    //YuNet Modell zur Gesichtserkennung
	private FaceDetectorYN yunet;

	public static volatile TrackingManager instance;


	private ScheduledExecutorService timer;
	private VideoCapture capture;
	private boolean cameraActive = false;

	/**
	 * Initialisiert den Controller, lädt das KI-Modell und bereitet die Kamera vor
	 */
	protected void init() throws Exception {
        String modelPath = extractResourceToTemp("/models/face_detection_yunet_2023mar.onnx", ".onnx");
        yunet = FaceDetectorYN.create(modelPath, "", new Size(320, 240), 0.6f, 0.3f, 5000);
        trackingLogic.setYunetReady(true);

	}

    public static synchronized TrackingManager getInstance() {
        if (instance == null) {
            instance = new TrackingManager();
        }
        return instance;
    }

    public void start(int cameraIndex) {
        if (cameraActive) return;
        setup();
        CompletableFuture.runAsync(() -> {
            capture.open(Videoio.CAP_DSHOW + cameraIndex);
            if (capture.isOpened()) {
                cameraActive = true;
                timer = Executors.newSingleThreadScheduledExecutor();
                timer.scheduleAtFixedRate(this::processFrame, 0, 33, TimeUnit.MILLISECONDS);
            }
        });

    }


    private void processFrame() {
        Mat frame = new Mat();
        if (capture.read(frame) && !frame.empty()) {
            trackingLogic.detectAndDisplay(frame, yunet);
            frame.release(); // Wichtig: Speicher freigeben!
        }
    }

    public void stop() {
        cameraActive = false;
        if (timer != null) timer.shutdown();
        if (capture.isOpened()) capture.release();
    }
    public void setup() {
        if (capture == null) {
            capture = new VideoCapture();
        }
    }


	// Zwischenfunktions, extrahiert Ressourcen und speichert sie temporär
	private static String extractResourceToTemp(String resourcePath, String suffix) throws Exception {
		try (var in = TrackingManager.class.getResourceAsStream(resourcePath)) {
			if (in == null) throw new RuntimeException("Resource not found: " + resourcePath);

			var tmp = java.nio.file.Files.createTempFile("model_", suffix);
			java.nio.file.Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			return tmp.toAbsolutePath().toString();
		}
	}

    // Im neuen TrackingManager (ehemals FXController)
    public synchronized void toggleCamera(int index) {
        setup();
        if (this.cameraActive) {
            stop(); // Beendet den Thread und schließt die Kamera
            this.cameraActive = false;
        } else {
            this.capture.open(Videoio.CAP_DSHOW + index);
            if (this.capture.isOpened()) {
                this.cameraActive = true;
                // Der Timer läuft im Hintergrund weiter wie bisher
                this.timer = Executors.newSingleThreadScheduledExecutor();
                this.timer.scheduleAtFixedRate(this::processFrame, 0, 33, TimeUnit.MILLISECONDS);
            }
        }
    }

    public boolean isCameraActive() {
        return cameraActive;
    }

    public VideoCapture getCapture() {
        return capture;
    }
}

