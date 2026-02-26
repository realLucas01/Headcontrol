package face.tracking;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import net.Gamesco.Headcontrol.client.HeadControlState;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.*;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.opencv.videoio.Videoio;


/**
 * FXController steuert die Benutzeroberfläche und die Logik für das Face-Tracking
 * Es nutzt OpenCV für die Bildverarbeitung und YuNet für die Gesichtserkennung
 */
public class FXController {
    UIOverlay uiOverlay = new UIOverlay();
    HeadTrackingLogic trackingLogic = new HeadTrackingLogic();
    //YuNet Modell zur Gesichtserkennung
	private FaceDetectorYN yunet;
	private boolean yunetReady = false;

	public static volatile FXController instance;

	//FXML UI Elemente
	@FXML
	private Button cameraButton;
	@FXML
	private Button resetButton;
	@FXML
	private ImageView originalFrame;
	@FXML
	private Label statusLabel;


	private ScheduledExecutorService timer;
	private VideoCapture capture;
	private boolean cameraActive;

	//matrizen für Pose Stabilität, speichert die letzte bekannte Position
	private Mat rvecPrev;
	private Mat tvecPrev;


	private boolean isCalibrated = false;

	/**
	 * Initialisiert den Controller, lädt das KI-Modell und bereitet die Kamera vor
	 */
	protected void init() {
		instance = this;
		this.capture = new VideoCapture();
		rvecPrev = new Mat();
		tvecPrev = new Mat();

		try {
			// Extrahiert das ONNX-Modell aus den Ressourcen
			String modelPath = extractResourceToTemp("/models/face_detection_yunet_2023mar.onnx", ".onnx");
			// Erstellt den FaceDetector mit Input Größe
			yunet = FaceDetectorYN.create(modelPath, "", new Size(320, 240), 0.6f, 0.3f, 5000);
			yunetReady = true;
            trackingLogic.setYunetReady(yunetReady);
		} catch (Exception e) {
			yunetReady = false;
            trackingLogic.setYunetReady(yunetReady);
            e.printStackTrace();
			cameraButton.setDisable(true);
		}
		if (cameraSelector.getItems().isEmpty()) {
			cameraSelector.getItems().addAll("Kamera 0", "Kamera 1", "Kamera 2");
		}
		cameraSelector.getSelectionModel().selectFirst();
	}


	// Zwischenfunktions, extrahiert Ressourcen und speichert sie temporär
	private static String extractResourceToTemp(String resourcePath, String suffix) throws Exception {
		try (var in = FXController.class.getResourceAsStream(resourcePath)) {
			if (in == null) throw new RuntimeException("Resource not found: " + resourcePath);

			var tmp = java.nio.file.Files.createTempFile("model_", suffix);
			java.nio.file.Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			return tmp.toAbsolutePath().toString();
		}
	}


	/**
	 * startet die kamera für den Bildstream
	 * @param actionEvent
	 */
	@FXML
	protected void startCamera(javafx.event.ActionEvent actionEvent) {
		if (!this.cameraActive) {
			// start the video capture
			int selectedIndex = cameraSelector.getSelectionModel().getSelectedIndex();
			this.capture.open(Videoio.CAP_DSHOW + selectedIndex);
			cameraSelector.setDisable(true);

			// is the video stream available?
			if (this.capture.isOpened()) {
				this.cameraActive = true;

				// grab a frame every 33 ms (30 frames/sec)
				Runnable frameGrabber = () -> {
                    // effectively grab and process a single frame
                    Mat frame = grabFrame();
                    // convert and show the frame
                    Image imageToShow = Utils.mat2Image(frame);
                    updateImageView(originalFrame, imageToShow);
					frame.release();
                };

				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);

				// update the button content
				this.cameraButton.setText("Stop Camera");
				if (statusLabel != null) statusLabel.setText("Running");
			} else {
				// log the error
			}
		} else {
			// the camera is not active at this point
			this.cameraActive = false;
			// update again the button content
			this.cameraButton.setText("Start Camera");
			if (statusLabel != null) statusLabel.setText("Idle");

			// stop the timer
			this.stopAcquisition();
			this.cameraSelector.setDisable(false);

		}
	}

	/**
	 * sucht die angeschlossenen Kameras des verwendeten Systems
	 * OpenCv kann leider dabei, keine Kameranamen erkennen, daher nur Kamera 0, 1 und 2
	 */
	private void findAvailableCameras() {
		// Liste leeren, falls sie schon Daten enthielt
		cameraSelector.getItems().clear();

		for (int i = 0; i < 5; i++) {
			VideoCapture tempCap = new VideoCapture();
			try {
				// Versuche die Kamera explizit über DirectShow zu öffnen
				if (tempCap.open(Videoio.CAP_DSHOW + i)) {
					// Prüfe, ob wir wirklich ein Bild bekommen könnten
					if (tempCap.isOpened()) {
						cameraSelector.getItems().add("Kamera " + i);
					}
					tempCap.release();
				}
			} catch (Exception e) {
				// Fehler bei diesem Index einfach ignorieren
			}
		}

		// Falls gar nichts gefunden wurde
		if (cameraSelector.getItems().isEmpty()) {
			cameraSelector.getItems().add("Keine Kamera!");
			cameraButton.setDisable(true);
		} else {
			cameraSelector.getSelectionModel().selectFirst(); // Standardmäßig Index 0 wählen
			cameraButton.setDisable(false);
		}
	}

	/**
	 * Kalibrierungsreset, zur neuen Kalibrierung
	 * @param actionEvent
	 */
	@FXML
	protected void handleResetCalibration(javafx.event.ActionEvent actionEvent) {
		trackingLogic.resetCalibration();
	}

	private Mat grabFrame() {
		Mat frame = new Mat();
		if (!HeadControlState.isEnabled()) return frame;

		// check if the capture is open
		if (this.capture.isOpened()) {
			try {
				// read the current frame
				this.capture.read(frame);

				// if the frame is not empty, process it
				if (!frame.empty()) {
					// face detection
					Imgproc.resize(frame, new Mat(), new Size(320,240));
					this.trackingLogic.detectAndDisplay(frame, yunet);
				}

			} catch (Exception e) {
				// log the (full) error
				if (!frame.empty()) frame.release();
			}
		}
		return frame;
	}


	/**
	 * Stop the acquisition from the camera and release all the resources
	 */
	private void stopAcquisition()
	{
		if (this.timer!=null && !this.timer.isShutdown())
		{
			try
			{
				// stop the timer
				this.timer.shutdown();
				this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
			}
			catch (InterruptedException e)
			{
				// log any exception
			}
		}
		if (this.capture.isOpened())
		{
			// release the camera
			this.capture.release();
		}
	}
	
	/**
	 * Update the {@link ImageView} in the JavaFX main thread
	 * 
	 * @param view
	 *            the {@link ImageView} to update
	 * @param image
	 *            the {@link Image} to show
	 */
	private void updateImageView(ImageView view, Image image)
	{
		Utils.onFXThread(view.imageProperty(), image);
	}

	
	/**
	 * On application close, stop the acquisition from the camera
	 */
	protected void setClosed()
	{
		this.stopAcquisition();
	}

	@FXML
	private ComboBox<String> cameraSelector;
	

	// ====== Bridge-API für Minecraft UI (ohne JavaFX) ======
	
	public synchronized boolean isCameraActive() {
	    return cameraActive;
	}
	
	public synchronized boolean isCalibrated() {
	    return isCalibrated;
	}
	
	/** Startet Kamera mit Index (0,1,2...) */
	public synchronized boolean startCameraWithIndex(int cameraIndex) {
	    if (cameraActive) return true;
	
	    if (this.capture == null) this.capture = new VideoCapture();
	    if (rvecPrev == null) rvecPrev = new Mat();
	    if (tvecPrev == null) tvecPrev = new Mat();
	
	    // DirectShow + Index (wie bei dir)
	    this.capture.open(Videoio.CAP_DSHOW + cameraIndex);
	
	    if (this.capture.isOpened()) {
	        this.cameraActive = true;
	
	        Runnable frameGrabber = () -> {
	            Mat frame = grabFrame();
	            Image imageToShow = Utils.mat2Image(frame);
	            updateImageView(originalFrame, imageToShow);
	        };
	
	        this.timer = Executors.newSingleThreadScheduledExecutor();
	        this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);
	
	        return true;
	    } else {
	        this.cameraActive = false;
	        return false;
	    }
	}
	
	public synchronized void stopCameraNow() {
	    if (!cameraActive) return;
	    cameraActive = false;
	    stopAcquisition();
	}
	
	/** Kamera togglen */
	public synchronized boolean toggleCamera(int cameraIndex) {
	    if (cameraActive) {
	        stopCameraNow();
	        return false;
	    } else {
	        return startCameraWithIndex(cameraIndex);
	    }
	}
	
	/** Kalibrierung neu starten */
	public synchronized void recalibrate() {
	    trackingLogic.resetCalibration();
	}

}

