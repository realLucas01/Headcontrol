package face.tracking;

import javafx.event.ActionEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
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
import org.opencv.videoio.Videoio;

public class FXController {
	private FaceDetectorYN yunet;
	private boolean yunetReady = false;

    // Rohwerte aus der 3D-Schätzung
    private double rawYaw, rawPitch, rawZ;

    // Maximale Kalibrierungswerte (für dynamische Schwellen)
    private static final int MAX_CALIBRATION_FRAMES = 60; // 2 Sek bei 30 FPS


	// Variablen für Glättung
	private double smoothYaw = 0;
    private double smoothPitch = 0;
	private static final double POSEALPHA = 0.4;
	private Point leSmooth = null;
    private Point reSmooth = null;
    private Point noSmooth = null;
    private Point lmSmooth = null;
    private Point rmSmooth = null;
	private static final double LM_ALPHA = 0.35;

	public static volatile FXController instance;

	// Kopfhaltung
	public enum HeadState {
		NEUTRAL,
		LEFT, RIGHT, UP, DOWN,
		LEFT_UP, LEFT_DOWN,
		RIGHT_UP, RIGHT_DOWN
	}


	public enum LeanState{
		FORWARD, BACKWARD, NEUTRAL
	}

	public enum TiltState {
		LEFT, RIGHT, NEUTRAL
	}


	private HeadState headState = HeadState.NEUTRAL; // Startposition
	private LeanState leanState = LeanState.NEUTRAL;
	private TiltState  tiltState = TiltState.NEUTRAL;


	// Anzahl der Frames, die der Kopf in einer Position bleiben muss
	private int holdCounter = 0;
	private static final int HOLD_FRAMES = 3;

	@FXML
	private Button cameraButton;
	@FXML
	private Button resetButton;
	@FXML
	private ImageView originalFrame;

	@FXML
	private HBox buttonContainer;

	private ScheduledExecutorService timer;
	private VideoCapture capture;
	private boolean cameraActive;
	private Mat rvecPrev;
	private Mat tvecPrev;
	private boolean hasPrevPose = false;

	// Diese werden nun während der Kalibrierung berechnet
	private double dynamicYawThres = 15.0;   // Startwert (Fallback)
	private double dynamicPitchThres = 12.0; // Startwert (Fallback)
	private double dynamicZThresForward = 40.0;  // Standard-Fallback
	private double dynamicZThresBackward = 40.0;
	private double maxObservedForward = 0;
	private double maxObservedBackward = 0;

	// Exit-Werte bleiben proportional (z.B. 70% des Enter-Werts)
	private static final double EXIT_FACTOR = 0.7;

	// Variablen für die Suche nach den Maxima
	private double maxObservedYaw = 0;
	private double maxObservedPitch = 0;

	private double offsetYaw = 0;
    private double offsetPitch = 0;
	private boolean isCalibrated = false;
	private int calibrationFramesCounter = 0;

	// Summen für die Mittelwertbildung
	private double sumYaw = 0;
    private double sumPitch = 0;

	// Z-Werte (Entfernung in mm/Modelleinheiten)
	private double smoothZ = 0;
	private double sumZ = 0;
	private double offsetZ = 0;

	// 0.5 = sehr sensibel, 0.8 = eher stabil/anstrengend
	private double leanSensitivity = 0.6;

	// Verhindert das "Zappeln" zwischen den Zuständen
	private static final double LEAN_EXIT_FACTOR = 0.55;

	// Mindestbewegung in Einheiten (mm), damit überhaupt eine Kalibrierung zählt
	private static final double MIN_Z_MOVEMENT = 15.0;

	// Variablen für Roll (Neigen zur Schulter)
	private double smoothRoll = 0;
	private double sumRoll = 0;
	private double offsetRoll = 0;
	private double maxObservedRoll = 0;
	private double dynamicRollThres = 15.0; // Standard-Fallback


	// Schwellenwerte für Vor/Zurück (muss experimentell bestimmt werden)
	private static final double Z_ENTER = 50.0; // Abweichung um 50 Einheiten
	private static final double Z_EXIT = 35.0;

	protected void init() {
		instance = this;
		this.capture = new VideoCapture();
		rvecPrev = new Mat();
		tvecPrev = new Mat();


		try {
			// Modell laden
			String modelPath = extractResourceToTemp("/models/face_detection_yunet_2023mar.onnx", ".onnx");
			yunet = FaceDetectorYN.create(modelPath, "", new Size(320, 240), 0.6f, 0.3f, 5000);
			yunetReady = true;

			//findAvailableCameras();
		} catch (Exception e) {
			yunetReady = false;
			e.printStackTrace();
			cameraButton.setDisable(true);
		}
		if (cameraSelector.getItems().isEmpty()) {
			cameraSelector.getItems().addAll("Kamera 0", "Kamera 1", "Kamera 2");
		}
		cameraSelector.getSelectionModel().selectFirst();
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
	private void updateHeadState(double yaw, double pitch) {
		// 1. Dynamische Schwellenwerte aus der Kalibrierung nutzen
		double baseYawThres = this.dynamicYawThres;
		double basePitchThres = this.dynamicPitchThres;

		// 2. Asymmetrie-Modifikatoren
		// DOWN muss deutlich stärker sein (z.B. Faktor 2.2),
		// während UP/LEFT/RIGHT sensibel bleiben (Faktor 1.0 - 1.2).


		if (tiltState!=TiltState.NEUTRAL) {
			headState = HeadState.NEUTRAL;
			holdCounter = 0;
			return; // Methode hier beenden, keine weitere Prüfung
		}
		// 3. Zustands-Booleans mit den gewichteten Schwellenwerten berechnen
		boolean isLeft  = yaw < -(baseYawThres);
		boolean isRight = yaw >  (baseYawThres);
		boolean isUp    = pitch < -(basePitchThres);   // Nase nach oben
		boolean isDown  = pitch >  (basePitchThres); // Nase nach unten (unempfindlicher)

		// 4. Ziel-Zustand (TargetState) ermitteln
		HeadState targetState = HeadState.NEUTRAL;

		// Diagonale zuerst (da sie spezifischer sind)
		if (isLeft && isUp)         targetState = HeadState.LEFT_UP;
		else if (isLeft && isDown)  targetState = HeadState.LEFT_DOWN;
		else if (isRight && isUp)   targetState = HeadState.RIGHT_UP;
		else if (isRight && isDown) targetState = HeadState.RIGHT_DOWN;
			// Dann die Hauptrichtungen
		else if (isLeft)            targetState = HeadState.LEFT;
		else if (isRight)           targetState = HeadState.RIGHT;
		else if (isUp)              targetState = HeadState.UP;
		else if (isDown && leanState == LeanState.NEUTRAL ) targetState = HeadState.DOWN;

		// 5. Stabilitäts-Check (HoldCounter)
		// Wenn du die Reaktion NOCH schneller willst, senke den Counter von 3 auf 2.
		if (targetState != headState) {
			if (++holdCounter >= 3) {
				headState = targetState;
				holdCounter = 0;
			}
		} else {
			holdCounter = 0;
		}
	}

	private void updateTiltState(double relRoll) {

		if (headState == HeadState.LEFT || headState == HeadState.RIGHT ||
				headState == HeadState.LEFT_UP || headState == HeadState.LEFT_DOWN ||
				headState == HeadState.RIGHT_UP || headState == HeadState.RIGHT_DOWN) {

			this.tiltState = TiltState.NEUTRAL;
			return;
		}
		TiltState targetTilt = TiltState.NEUTRAL;

        // Hysterese: 100% zum Eintreten, 70% zum Verlassen
        double enterThres = dynamicRollThres;
        double exitThres = dynamicRollThres * EXIT_FACTOR;

        if (tiltState == TiltState.NEUTRAL) {
            if (relRoll > enterThres) targetTilt = TiltState.LEFT;
            else if (relRoll < -enterThres) targetTilt = TiltState.RIGHT;
        } else {
            // Wir sind bereits in LEFT oder RIGHT -> Exit-Schwelle prüfen
            if (tiltState == TiltState.LEFT && relRoll > exitThres) targetTilt = TiltState.LEFT;
            else if (tiltState == TiltState.RIGHT && relRoll < -exitThres) targetTilt = TiltState.RIGHT;
            else targetTilt = TiltState.NEUTRAL;
        }
        tiltState = targetTilt;
    }

    private void updateLeanState(double relZ) {
        LeanState targetLean = LeanState.NEUTRAL;
		if (tiltState != TiltState.NEUTRAL) {
			this.leanState = LeanState.NEUTRAL;
			return;
		}

        // 3D-Z-Werte sind grober. 40-50 Einheiten sind ein guter Schwellenwert.
        double thresh = 45.0;

        // relZ = smoothZ - offsetZ
        // Wenn relZ NEGATIV ist, ist der aktuelle Abstand kleiner als der Kalibrierungsabstand -> FORWARD
        if (relZ < -thresh ) {
            targetLean = LeanState.FORWARD;
        }
        // Wenn relZ POSITIV ist, bist du weiter weg -> BACKWARD
        else if (relZ > thresh) {
            targetLean = LeanState.BACKWARD;
        }

        if (targetLean != leanState ) {
            leanState = targetLean;
        }
    }

    private void drawLeanBar(Mat frame, double relZ) {
        int x = frame.cols() - 40;
        int yMid = frame.rows() / 2;
        int barHalfHeight = 100;

        // Hintergrund-Schiene (Dunkelgrau)
        Imgproc.rectangle(frame, new Point(x, yMid - barHalfHeight), new Point(x + 10, yMid + barHalfHeight), new Scalar(50, 50, 50), -1);

        // Schwellenwert-Linien (Weiß)
        double threshPx = 45.0 * 0.8; // 45 Einheiten skaliert auf die Anzeige
        Imgproc.line(frame, new Point(x - 5, yMid - threshPx), new Point(x + 15, yMid - threshPx), new Scalar(255, 255, 255), 1);
        Imgproc.line(frame, new Point(x - 5, yMid + threshPx), new Point(x + 15, yMid + threshPx), new Scalar(255, 255, 255), 1);

        // Aktuelle Position: relZ ist NEGATIV bei FORWARD (Abstand wird kleiner)
        // Wir nehmen -relZ, damit die Kugel beim Vorlehnen nach OBEN geht
        double displayPos = -relZ * 1.5;
        displayPos = Math.max(-barHalfHeight, Math.min(barHalfHeight, displayPos));

        Scalar color = (leanState == LeanState.NEUTRAL) ? new Scalar(0, 255, 0) : new Scalar(0, 255, 255);
        Imgproc.circle(frame, new Point(x + 5, yMid + displayPos), 7, color, -1);
    }

	private void drawDirectionGrid(Mat frame) {
		int w = frame.cols();
		int h = frame.rows();
		int cx = w / 2;
		int cy = h / 2;

		double visualScale = 5.0;

		// Gitter passt sich an die kalibrierten Grenzen an!
		int dx = (int)(dynamicYawThres * visualScale);
		int dy = (int)(dynamicPitchThres * visualScale);

		Scalar gridColor = new Scalar(100, 100, 100); // Dunkelgrau

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

		drawLeanBar(frame, smoothZ );
	}


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
			} else {
				// log the error
				//System.err.println("Failed to open the camera connection...");
			}
		} else {
			// the camera is not active at this point
			this.cameraActive = false;
			// update again the button content
			this.cameraButton.setText("Start Camera");

			// stop the timer
			this.stopAcquisition();
			this.cameraSelector.setDisable(false);

		}
	}
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

	@FXML
	protected void handleResetCalibration(javafx.event.ActionEvent actionEvent) {
		resetCalibration();

		// Optional: Feedback in der Konsole oder auf dem Button
		//System.out.println("Kalibrierung manuell neu gestartet...");
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
					Imgproc.resize(frame, new Mat(), new Size(320,240));
					this.detectAndDisplay(frame);
				}

			} catch (Exception e) {
				// log the (full) error
				if (!frame.empty()) frame.release();
				//System.err.println("Exception during the image elaboration: " + e);
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
		//if (!ok) return;

		if (ok) {
			rvecPrev = rvec.clone();
			tvecPrev = tvec.clone();
            hasPrevPose = true;

            // Extrahiere Z-Distanz (Lean)
            this.rawZ = tvec.get(2, 0)[0];

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

            Mat R = new Mat();
            Calib3d.Rodrigues(rvec, R);
            double[] e = rotationMatrixToEuler(R);
            this.rawPitch = e[0];
            this.rawYaw = e[1];
		}

	}
    public HeadState getHeadState(){return headState;}
	public LeanState getLeanState(){return leanState;}
	public TiltState getTiltState(){return tiltState;}

	/**
	 * Reset der Kalibrierung
	 */

	public void resetCalibration() {
		isCalibrated = false;
		calibrationFramesCounter = 0;

		// Mittelwerte zurücksetzen
		sumYaw = 0; sumPitch = 0; sumZ = 0;
		offsetYaw = 0; offsetPitch = 0; offsetZ = 0;

		// Glättung zurücksetzen
		smoothYaw = 0; smoothPitch = 0; smoothZ = 0;
		hasPrevPose = false;

		// Maxima der dynamischen Kalibrierung zurücksetzen
		maxObservedYaw = 0; maxObservedPitch = 0;
		maxObservedForward = 0; maxObservedBackward = 0;

		sumRoll = 0;
		offsetRoll = 0;
		maxObservedRoll = 0;
		smoothRoll = 0;
		tiltState = TiltState.NEUTRAL;


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

		if (f[14] < 0.7) return; // Schwelle etwas senken für stabilere Drehung

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

        double geoRoll = calculate2DRoll(reSmooth, leSmooth);
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

        if (!isCalibrated) {
            calibrationFramesCounter++;
            sumYaw += rawYaw;
            sumPitch += rawPitch;
            sumZ += rawZ;
            sumRoll += geoRoll;

            // Dynamische Maxima finden (Abweichung vom aktuellen Schnitt)
            double curRelYaw = Math.abs(rawYaw - (sumYaw / calibrationFramesCounter));
            double curRelPitch = Math.abs(rawPitch - (sumPitch / calibrationFramesCounter));
            double curRelRoll = Math.abs(geoRoll - (sumRoll / calibrationFramesCounter));

            if (curRelYaw > maxObservedYaw) maxObservedYaw = curRelYaw;
            if (curRelPitch > maxObservedPitch) maxObservedPitch = curRelPitch;
            if (curRelRoll > maxObservedRoll) maxObservedRoll = curRelRoll;

            drawCalibrationProgress(frameBgr);

            if (calibrationFramesCounter >= MAX_CALIBRATION_FRAMES) {
                offsetYaw = sumYaw / MAX_CALIBRATION_FRAMES;
                offsetPitch = sumPitch / MAX_CALIBRATION_FRAMES;
                offsetZ = sumZ / MAX_CALIBRATION_FRAMES;
                offsetRoll = sumRoll / MAX_CALIBRATION_FRAMES;

                // Schwellenwerte basierend auf der Bewegung während der Kalibrierung
                // Wir nehmen 80% des Maximums, aber mindestens einen "Noise Floor"
                dynamicYawThres = Math.max(8.0, maxObservedYaw * 0.8);
                dynamicPitchThres = Math.max(6.0, maxObservedPitch * 0.8);
                dynamicRollThres = Math.max(10.0, maxObservedRoll * 0.8);

                smoothRoll = 0; smoothYaw = 0; smoothPitch = 0; smoothZ = 0;
                isCalibrated = true;
            }
        } else {
            // NORMALER BETRIEB
            // Glättung der Differenzwerte
            smoothYaw = (1 - POSEALPHA) * smoothYaw + POSEALPHA * (rawYaw - offsetYaw);
            smoothPitch = (1 - POSEALPHA) * smoothPitch + POSEALPHA * (rawPitch - offsetPitch);
            smoothZ = (1 - POSEALPHA) * smoothZ + POSEALPHA * (rawZ - offsetZ);
            smoothRoll = (1 - POSEALPHA) * smoothRoll + POSEALPHA * (geoRoll - offsetRoll);

            // States aktualisieren
			updateTiltState(smoothRoll);


			updateLeanState(smoothZ);


			updateHeadState(smoothYaw, smoothPitch);

            // Visualisierung
            drawDirectionGrid(frameBgr);
            drawStatusText(frameBgr);
        }

        // Gesicht und Punkte zeichnen (Kontrolle)
        drawFaceMarkers(frameBgr, f, reSmooth, leSmooth, noSmooth, rmSmooth, lmSmooth);
		faces.release();
	}


    private double calculate2DRoll(Point re, Point le) {
        if (re == null || le == null) return 0;
        double dy = le.y - re.y;
        double dx = le.x - re.x;
        // Wir berechnen den Winkel der Augenlinie zur Horizontalen
        return Math.toDegrees(Math.atan2(dy, dx));
    }
    private double calculate2DYaw(Point re, Point le, Point no) {
        // Distanz Auge-Nase (Horizontal)
        double distToRightEye = Math.abs(no.x - re.x);
        double distToLeftEye = Math.abs(no.x - le.x);

        // Verhältnis berechnen (Wertebereich ca. -1.0 bis 1.0)
        // Ein Wert von 0 bedeutet "Nase ist mittig"
        return (distToRightEye - distToLeftEye) / (distToRightEye + distToLeftEye) * 100.0;
    }
    private double calculate2DPitch(Point re, Point le, Point no, Point rm, Point lm) {
        // Durchschnittliche Augenhöhe und Mundhöhe
        double eyeY = (re.y + le.y) / 2.0;
        double mouthY = (rm.y + lm.y) / 2.0;

        // Wo liegt die Nase dazwischen?
        double faceHeight = mouthY - eyeY;

        // Verhältnis (Standardwert liegt meist bei ~0.4 - 0.5)
        double ratio = (no.y - eyeY) / faceHeight;
        // Wir normalisieren das auf einen Wert um 0 (Abweichung vom Standard)
        return (ratio - 0.50) * 100.0;
    }
    private void drawCalibrationProgress(Mat frame) {
        String msg = "KALIBRIERUNG: Kopf bewegen...";
        Imgproc.putText(frame, msg, new Point(20, 130),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0, 165, 255), 2);

        // Fortschrittsbalken (max 200 Pixel breit)
        double progress = (double) calibrationFramesCounter / MAX_CALIBRATION_FRAMES;
        Imgproc.rectangle(frame, new Point(20, 150),
                new Point(20 + (progress * 200), 165), new Scalar(0, 255, 0), -1);
        Imgproc.rectangle(frame, new Point(20, 150),
                new Point(220, 165), new Scalar(255, 255, 255), 1);
    }
    private void drawStatusText(Mat frame) {
        // Hintergrund-Box für Lesbarkeit
        Imgproc.rectangle(frame, new Point(10, 10), new Point(280, 120), new Scalar(0, 0, 0), -1);

        Scalar headColor = (headState == HeadState.NEUTRAL) ? new Scalar(0, 255, 0) : new Scalar(0, 255, 255);

        Imgproc.putText(frame, "HEAD: " + headState, new Point(20, 40),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, headColor, 2);
        Imgproc.putText(frame, "TILT: " + tiltState, new Point(20, 70),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(200, 200, 0), 2);
        Imgproc.putText(frame, "LEAN: " + leanState, new Point(20, 100),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255), 2);

        // Debug-Werte am unteren Rand
        String debug = String.format("Y: %.1f | P: %.1f | R: %.1f | Z: %.1f",
                smoothYaw, smoothPitch, smoothRoll, smoothZ);
        Imgproc.putText(frame, debug, new Point(20, frame.rows() - 20),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 255, 255), 1);
    }

    private void drawFaceMarkers(Mat frame, float[] f, Point re, Point le, Point no, Point rm, Point lm) {        // Bounding Box
        Imgproc.rectangle(frame, new Point(f[0], f[1]), new Point(f[0] + f[2], f[1] + f[3]), new Scalar(0, 255, 0), 1);

        // Die 5 Punkte mit verschiedenen Farben zur Unterscheidung
        Point[] pts = {re, le, no, rm, lm};
        Scalar[] colors = {
                new Scalar(255, 0, 0),   // RE: Blau
                new Scalar(0, 0, 255),   // LE: Rot
                new Scalar(0, 255, 0),   // NO: Grün
                new Scalar(0, 255, 255), // RM: Gelb
                new Scalar(255, 0, 255)  // LM: Magenta
        };

        for (int i = 0; i < pts.length; i++) {
            if (pts[i] != null) {
                Imgproc.circle(frame, pts[i], 4, colors[i], -1);
            }
        }
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
				//System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
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


}

