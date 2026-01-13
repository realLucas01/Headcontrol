package face.tracking;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class FXController {
	private FaceDetectorYN yunet;
	private boolean yunetReady = false;

	// Variablen für Glättung
	private double smoothYaw = 0;
    private double smoothPitch = 0;
	private static final double POSEALPHA = 0.15;
	private Point leSmooth = null;
    private Point reSmooth = null;
    private Point noSmooth = null;
    private Point lmSmooth = null;
    private Point rmSmooth = null;
	private static final double LM_ALPHA = 0.35;

	// Kopfhaltung
	public enum HeadState {
		NEUTRAL,
		LEFT, RIGHT, UP, DOWN,
		LEFT_UP, LEFT_DOWN,
		RIGHT_UP, RIGHT_DOWN
	}

	private HeadState headState = HeadState.NEUTRAL; // Startposition

	// Anzahl der Frames, die der Kopf in einer Position bleiben muss
	private int holdCounter = 0;
	private static final int HOLD_FRAMES = 3;

	@FXML
	private Button cameraButton;
	@FXML
	private Button resetButton;
	@FXML
	private ImageView originalFrame;

	private ScheduledExecutorService timer;
	private VideoCapture capture;
	private boolean cameraActive;
	private Mat rvecPrev;
	private Mat tvecPrev;
	private boolean hasPrevPose = false;

	// Diese werden nun während der Kalibrierung berechnet
	private double dynamicYawThres = 15.0;   // Startwert (Fallback)
	private double dynamicPitchThres = 12.0; // Startwert (Fallback)

	// Exit-Werte bleiben proportional (z.B. 70% des Enter-Werts)
	private static final double EXIT_FACTOR = 0.7;

	// Variablen für die Suche nach den Maxima
	private double maxObservedYaw = 0;
	private double maxObservedPitch = 0;

	private double offsetYaw = 0;
    private double offsetPitch = 0;
	private boolean isCalibrated = false;
	private int calibrationFramesCounter = 0;
	private static final int MAX_CALIBRATION_FRAMES = 30; // ca. 1 Sekunde bei 30 FPS

	// Summen für die Mittelwertbildung
	private double sumYaw = 0;
    private double sumPitch = 0;


	protected void init() {
		this.capture = new VideoCapture();
		rvecPrev = new Mat();
		tvecPrev = new Mat();

		try {
			// Modell laden
			String modelPath = extractResourceToTemp("/models/face_detection_yunet_2023mar.onnx", ".onnx");
			yunet = FaceDetectorYN.create(modelPath, "", new Size(320, 240), 0.6f, 0.3f, 5000);
			yunetReady = true;
		} catch (Exception e) {
			yunetReady = false;
			e.printStackTrace();
			cameraButton.setDisable(true);
		}
	}
	//Glättung skalierte Punkte
	private Point ema(Point prev, Point cur, double a) {
		if (prev == null) return cur;
		return new Point(
				(1 - a) * prev.x + a * cur.x,
				(1 - a) * prev.y + a * cur.y
		);
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

	/*
	 * Aktualisierung Kopfzustand anhand Yaw/Pitch Werten
	 */
	private void updateHeadState(double yawDeg, double pitchDeg) {
		//  Schwellenwerte bestimmen
		double currentYawThres   = (headState != HeadState.NEUTRAL) ? (dynamicYawThres * EXIT_FACTOR) : dynamicYawThres;
		double currentPitchThres = (headState != HeadState.NEUTRAL) ? (dynamicPitchThres * EXIT_FACTOR) : dynamicPitchThres;

		// Temporäre Richtungen bestimmen
		boolean isLeft  = yawDeg < -currentYawThres;
		boolean isRight = yawDeg > currentYawThres;
		boolean isUp    = pitchDeg < -currentPitchThres;
		boolean isDown  = pitchDeg > currentPitchThres;

		// Ziel-Zustand ermitteln
		HeadState targetState = HeadState.NEUTRAL;
		if (isLeft && isUp)         targetState = HeadState.LEFT_UP;
		else if (isLeft && isDown)  targetState = HeadState.LEFT_DOWN;
		else if (isRight && isUp)   targetState = HeadState.RIGHT_UP;
		else if (isRight && isDown) targetState = HeadState.RIGHT_DOWN;
		else if (isLeft)            targetState = HeadState.LEFT;
		else if (isRight)           targetState = HeadState.RIGHT;
		else if (isUp)              targetState = HeadState.UP;
		else if (isDown)            targetState = HeadState.DOWN;

		// Zustandswechsel mit Hold-Counter (Bestätigung über mehrere Frames)
		if (targetState != headState) {
			if (++holdCounter >= HOLD_FRAMES) {
				headState = targetState;
				System.out.println(">>> BESTÄTIGTER STATUS: " + headState);
				holdCounter = 0;
			}
		} else {
			holdCounter = 0; // Reset, wenn der Zielzustand wieder dem aktuellen entspricht
		}
	}

	private void drawDirectionGrid(Mat frame) {
		int w = frame.cols();
		int h = frame.rows();
		int cx = w / 2;
		int cy = h / 2;

		double visualScale = 6.0;

		// Gitter passt sich an die kalibrierten Grenzen an!
		int dx = (int)(dynamicYawThres * visualScale);
		int dy = (int)(dynamicPitchThres * visualScale);

		Scalar gridColor = new Scalar(80, 80, 80); // Dunkelgrau

		// Zeichne das "Steuer-Kreuz"
		// Vertikale Linien (Links/Rechts Grenzen)
		Imgproc.line(frame, new Point(cx - (double)dx, 0), new Point(cx - (double)dx, h), gridColor, 1);
		Imgproc.line(frame, new Point(cx + (double)dx, 0), new Point(cx + (double)dx, h), gridColor, 1);

		// Horizontale Linien (Oben/Unten Grenzen)
		Imgproc.line(frame, new Point(0, cy - (double)dy), new Point(w, cy - (double)dy), gridColor, 1);
		Imgproc.line(frame, new Point(0, cy + (double)dy), new Point(w, cy + (double)dy), gridColor, 1);
		// Zeichne einen "Zielpunkt", der deine aktuelle Kopfneigung anzeigt
		double pointerX = cx + (smoothYaw * visualScale);
		double pointerY = cy + (smoothPitch * visualScale);

		// Aktuellen Status als Text in die jeweilige Ecke schreiben
		Scalar pointerColor = (headState == HeadState.NEUTRAL) ? new Scalar(0, 255, 0) : new Scalar(0, 0, 255);
		Imgproc.circle(frame, new Point(pointerX, pointerY), 6, pointerColor, -1);
	}


	@FXML
	protected void startCamera() {
		if (!this.cameraActive) {
			// start the video capture
			this.capture.open(0);

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
                };

				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);

				// update the button content
				this.cameraButton.setText("Stop Camera");
			} else {
				// log the error
				System.err.println("Failed to open the camera connection...");
			}
		} else {
			// the camera is not active at this point
			this.cameraActive = false;
			// update again the button content
			this.cameraButton.setText("Start Camera");

			// stop the timer
			this.stopAcquisition();
		}
	}
	@FXML
	protected void handleResetCalibration() {
		// Ruft deine bestehende Logik auf
		resetCalibration();

		// Optional: Feedback in der Konsole oder auf dem Button
		System.out.println("Kalibrierung manuell neu gestartet...");
	}

	private Mat grabFrame() {
		Mat frame = new Mat();

		// check if the capture is open
		if (this.capture.isOpened()) {
			try {
				// read the current frame
				this.capture.read(frame);

				// if the frame is not empty, process it
				if (!frame.empty()) {
					// face detection
					this.detectAndDisplay(frame);
				}

			} catch (Exception e) {
				// log the (full) error
				System.err.println("Exception during the image elaboration: " + e);
			}
		}
		return frame;
	}

	/**
	 * Pose Schätzung aus 5 Gesichtspunkten:
	 * - 2D Punkte aus YuNet: Augen, Nase, Mundwinkel
	 * - 3D “Standard Face Model” Punkte
	 */
	private void estimatePoseFrom5Points(Mat frameBgr, Point le, Point re, Point no, Point lm, Point rm) {
		// 2D Bildpunkte
		MatOfPoint2f imagePoints = new MatOfPoint2f(
				no, // 1. Nase
				re, // 2. Rechtes Auge
				le, // 3. Linkes Auge
				rm, // 4. Rechter Mundwinkel
				lm  // 5. Linker Mundwinkel
		);

		// 3D ModellPunkte
		MatOfPoint3f modelPoints = new MatOfPoint3f(
				new Point3(0.0, 0.0, 0.0),          // Nase
				new Point3(-25.0, 35.0, -25.0),     // Rechtes Auge (relativ zur Nase)
				new Point3(25.0, 35.0, -25.0),      // Linkes Auge
				new Point3(-18.0, -30.0, -20.0),    // Rechter Mund
				new Point3(18.0, -30.0, -20.0)      // Linker Mund
		);

		// Kamera-Intrinsics grob schätzen:
		double focal = frameBgr.cols();
		Point center = new Point(frameBgr.cols() / 2.0, frameBgr.rows() / 2.0);

		Mat cameraMatrix = Mat.eye(3, 3, CvType.CV_64F);
		cameraMatrix.put(0, 0, focal);
		cameraMatrix.put(0, 2, center.x);
		cameraMatrix.put(1, 1, focal);
		cameraMatrix.put(1, 2, center.y);

		MatOfDouble distCoeffs = new MatOfDouble(0, 0, 0, 0);   // oder 5 Werte: 0,0,0,0,0

		Mat rvec = new Mat();
		Mat tvec = new Mat();

		boolean ok;
		if (!hasPrevPose) {
			// Start: EPNP (geht mit >=4 Punkten)
			ok = Calib3d.solvePnP(
					modelPoints, imagePoints,
					cameraMatrix, distCoeffs,
					rvec, tvec,
					false, Calib3d.SOLVEPNP_EPNP
			);
		} else {
			// Stabil: ITERATIVE + Startwerte aus vorherigem Frame
			rvec = rvecPrev.clone();
			tvec = tvecPrev.clone();

			ok = Calib3d.solvePnP(
					modelPoints, imagePoints,
					cameraMatrix, distCoeffs,
					rvec, tvec,
					true, Calib3d.SOLVEPNP_ITERATIVE
			);
		}
		if (!ok) return;

		if (ok) {
			rvecPrev = rvec.clone();
			tvecPrev = tvec.clone();
			hasPrevPose = true;
		}

		if (ok) {
			// 1. Definiere Achsen-Endpunkte im 3D-Raum (Länge 100 Einheiten)
			MatOfPoint3f axisPoints = new MatOfPoint3f(
					new Point3(100, 0, 0),   // X-Achse (Rot) -> Rechts
					new Point3(0, 100, 0),   // Y-Achse (Grün) -> Unten
					new Point3(0, 0, 100)    // Z-Achse (Blau) -> "Aus der Nase heraus"
			);

			MatOfPoint2f imagePointsProj = new MatOfPoint2f();
			Calib3d.projectPoints(axisPoints, rvec, tvec, cameraMatrix, distCoeffs, imagePointsProj);

			Point[] p = imagePointsProj.toArray();

            // 2. Zeichne die Linien (X=Rot, Y=Grün, Z=Blau)
			Imgproc.line(frameBgr, no, p[0], new Scalar(0, 0, 255), 3); // X-Achse
			Imgproc.line(frameBgr, no, p[1], new Scalar(0, 255, 0), 3); // Y-Achse
			Imgproc.line(frameBgr, no, p[2], new Scalar(255, 0, 0), 3); // Z-Achse
		}
		drawDirectionGrid(frameBgr);

		Mat R = new Mat();

		Calib3d.Rodrigues(rvec, R);


		double[] e = rotationMatrixToEuler(R);

		double pitch = e[0], yaw = e[1], roll = e[2];

		if (!isCalibrated) {
			// KALIBRIERUNGSPHASE
			calibrationFramesCounter++;

			// Mittelwert für den Nullpunkt (Offset)
			sumYaw += yaw;
			sumPitch += pitch;

			// 2. Maxima erfassen
			double currentRelYaw = Math.abs(yaw - (sumYaw / calibrationFramesCounter));
			double currentRelPitch = Math.abs(pitch - (sumPitch / calibrationFramesCounter));

			if (currentRelYaw > maxObservedYaw) maxObservedYaw = currentRelYaw;
			if (currentRelPitch > maxObservedPitch) maxObservedPitch = currentRelPitch;

			Imgproc.putText(frameBgr, "KALIBRIERUNG: Bewege den Kopf in alle Ecken!",
					new Point(20, 130), Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0, 165, 255), 2);

			// Fortschrittsbalken  anzeigen
			Imgproc.rectangle(frameBgr, new Point(20, 150), new Point(20 + (calibrationFramesCounter * 2), 160), new Scalar(0, 255, 0), -1);

			if (calibrationFramesCounter >= 200) { // Zeitangabe zum Kali, 30 frames = 1 sek
				offsetYaw = sumYaw / calibrationFramesCounter;
				offsetPitch = sumPitch / calibrationFramesCounter;

				// Schwellenwerte festlegen, 80% des Maximums
				dynamicYawThres = maxObservedYaw * 0.8;
				dynamicPitchThres = maxObservedPitch * 0.8;

				// Sicherheitsschranken
				dynamicYawThres = Math.max(10.0, Math.min(30.0, dynamicYawThres));
				dynamicPitchThres = Math.max(8.0, Math.min(25.0, dynamicPitchThres));

				isCalibrated = true;
				System.out.println(">>> DYNAMISCHE SCHWELLEN: Yaw: " + dynamicYawThres + " Pitch: " + dynamicPitchThres);
			}
		}else {
			// NORMALER BETRIEB
			double correctedYaw = yaw - offsetYaw;
			double correctedPitch = pitch - offsetPitch;

			// Smoothing auf die korrigierten Werte anwenden
			smoothPitch = (1 - POSEALPHA) * smoothPitch + POSEALPHA * correctedPitch;
			smoothYaw = (1 - POSEALPHA) * smoothYaw + POSEALPHA * correctedYaw;

			updateHeadState(smoothYaw, smoothPitch);

			// Anzeige der korrigierten Werte
			Imgproc.putText(frameBgr,
					String.format("Yaw %.1f | Pitch %.1f (kalibriert)", smoothYaw, smoothPitch),
					new Point(20, 30), Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0, 255, 0), 2
			);
		}

		// Visuelles Feedback im Bild
		Scalar color = (headState == HeadState.NEUTRAL) ? new Scalar(0, 255, 0) : new Scalar(0, 255, 255);

		// Hintergrund-Box für bessere Lesbarkeit
		Imgproc.rectangle(frameBgr, new Point(10, 70), new Point(250, 110), new Scalar(0, 0, 0), -1);

		// Status-Text zeichnen
		Imgproc.putText(
				frameBgr,
				"STATUS: " + headState.toString(),
				new Point(20, 100),
				Imgproc.FONT_HERSHEY_SIMPLEX,
				0.8,
				color,
				2
		);

	}

	/**
	 * Reset der Kalibrierung
	 */

	public void resetCalibration() {
		isCalibrated = false;
		calibrationFramesCounter = 0;
		sumYaw = 0;
		sumPitch = 0;
		hasPrevPose = false;
		smoothYaw = 0;
		smoothPitch = 0;
		maxObservedYaw = 0;
		maxObservedPitch = 0;

		leSmooth = null;
		reSmooth = null;
		noSmooth = null;
		lmSmooth = null;
		rmSmooth = null;
	}

	private static double[] rotationMatrixToEuler(Mat R) {
		double r00 = R.get(0, 0)[0], r01 = R.get(0, 1)[0], r02 = R.get(0, 2)[0];
		double r10 = R.get(1, 0)[0], r11 = R.get(1, 1)[0], r12 = R.get(1, 2)[0];
		double r20 = R.get(2, 0)[0], r21 = R.get(2, 1)[0], r22 = R.get(2, 2)[0];

		double sy = Math.sqrt(r00 * r00 + r10 * r10);
		boolean singular = sy < 1e-6;

		double x, y, z;
		if (!singular) {
			x = Math.atan2(r21, r22);
			y = Math.atan2(-r20, sy);
			z = Math.atan2(r10, r00);
		} else {
			x = Math.atan2(-r12, r11);
			y = Math.atan2(-r20, sy);
			z = 0;
		}
		return new double[]{x * 180 / Math.PI, y * 180 / Math.PI, z * 180 / Math.PI};
	}


	private void detectAndDisplay(Mat frameBgr) {
		if (!yunetReady) return;

		Size inputSize = frameBgr.size();
		yunet.setInputSize(inputSize);


		Mat faces = new Mat();
		yunet.detect(frameBgr, faces); // Detektion auf dem kleinen Bild

		if (faces.empty() || faces.rows() == 0) {
			Imgproc.putText(frameBgr, "No face", new Point(20, 30),
					Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(0, 0, 255), 2);
			return;
		}


		// Bestes Gesicht finden
		float[] f = new float[faces.cols()];
		faces.get(0, 0, f); // Vereinfacht: erstes Gesicht nehmen

		// f[0..3]  : Bounding Box (x, y, w, h)
		// f[4..5]  : Rechtes Auge (aus Sicht der Kamera: links im Bild)
		// f[6..7]  : Linkes Auge  (aus Sicht der Kamera: rechts im Bild)
		// f[8..9]  : Nasenspitze
		// f[10..11]: Rechter Mundwinkel
		// f[12..13]: Linker Mundwinkel
		// f[14]    : Confidence Score

		double score = f[14];
		if (score < 0.7) return; // Schwelle etwas senken für stabilere Drehung

		Point re = new Point(f[4], f[5]);  // Right Eye
		Point le = new Point(f[6], f[7]);  // Left Eye
		Point no = new Point(f[8], f[9]);  // Nose
		Point rm = new Point(f[10], f[11]);// Right Mouth
		Point lm = new Point(f[12], f[13]);// Left Mouth

		// Glättung (EMA)
		reSmooth = ema(reSmooth, re, LM_ALPHA);
		leSmooth = ema(leSmooth, le, LM_ALPHA);
		noSmooth = ema(noSmooth, no, LM_ALPHA);
		rmSmooth = ema(rmSmooth, rm, LM_ALPHA);
		lmSmooth = ema(lmSmooth, lm, LM_ALPHA);

		estimatePoseFrom5Points(frameBgr, reSmooth, leSmooth, noSmooth, rmSmooth, lmSmooth);

		// Zeichnen zur Kontrolle
		Imgproc.rectangle(frameBgr, new Point(f[0], f[1]), new Point(f[0] + f[2], f[1] + f[3]), new Scalar(0, 255, 0), 2);
		Imgproc.circle(frameBgr, noSmooth, 3, new Scalar(0, 255, 0), -1);
		Scalar[] colors = {
				new Scalar(255,0,0),   // Blau
				new Scalar(0,0,255),   // Rot
				new Scalar(0,255,0),   // Grün
				new Scalar(0,255,255), // Gelb
				new Scalar(255,0,255)  // Magenta
		};
		Point[] pts = {reSmooth, leSmooth, noSmooth, rmSmooth, lmSmooth};

		for(int i=0; i<5; i++) {
			Imgproc.circle(frameBgr, pts[i], 4, colors[i], -1);
		}
	}

	private static boolean inside(Rect2d r, Point p) {
		return p.x >= r.x && p.x <= r.x + r.width && p.y >= r.y && p.y <= r.y + r.height;
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
				System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
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
	
}

