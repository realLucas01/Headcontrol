package faceTracking;

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
	private double smoothYaw=0, smoothPitch=0;
	private static final double POSEALPHA = 0.25;

	// Kopfhaltung
	public enum HeadState {
	    NEUTRAL,
		LEFT,
	    RIGHT,
	    UP,
	    DOWN
	}
	private HeadState headState= HeadState.NEUTRAL; // Startposition

    // Anzahl der Frames, die der Kopf in einer Position bleiben muss

    private int holdCounter = 0;
	private static final int HOLD_FRAMES = 5;

	@FXML
	private Button cameraButton;
	@FXML
	private ImageView originalFrame;
	
	private ScheduledExecutorService timer;
	private VideoCapture capture;
	private boolean cameraActive;
	private Mat rvecPrev;
	private Mat tvecPrev;
	private boolean hasPrevPose = false;
	// Schwellenwert für die Zustandsänderung
	private static final double YAW_ENTER  = 15.0;
	private static final double PITCH_ENTER = 15.0;

	// SChwellenwert zurücksetzten
	private static final double YAW_EXIT   = 8.0;
	private static final double PITCH_EXIT = 8.0;

	private double offsetYaw = 0, offsetPitch = 0;
	private boolean isCalibrated = false;
	private int calibrationFramesCounter = 0;
	private static final int MAX_CALIBRATION_FRAMES = 30; // ca. 1 Sekunde bei 30 FPS

	// Summen für die Mittelwertbildung
	private double sumYaw = 0, sumPitch = 0;

	
	protected void init() {
	    this.capture = new VideoCapture();
		rvecPrev = new Mat();
		tvecPrev = new Mat();

	    try {
	    	// Modell laden
	        String modelPath = extractResourceToTemp("/models/face_detection_yunet_2023mar.onnx", ".onnx");
	        yunet = FaceDetectorYN.create(modelPath, "", new Size(320,240), 0.9f, 0.3f, 5000);
	        yunetReady = true;
	    } catch (Exception e) {
	        yunetReady = false;
	        e.printStackTrace();
	        cameraButton.setDisable(true); 
	    }
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
	    HeadState previousState = headState;

	    switch (headState) { // TODO, fast jeder zweite zustand ist neutral -> fix finden, logik überprüfen
	        case NEUTRAL:
	            if (yawDeg > YAW_ENTER) { 
	                if (++holdCounter >= HOLD_FRAMES) headState = HeadState.RIGHT;
	            } else if (yawDeg < -YAW_ENTER) {
	                if (++holdCounter >= HOLD_FRAMES) headState = HeadState.LEFT;
	            } else if (pitchDeg > PITCH_ENTER) {
	                if (++holdCounter >= HOLD_FRAMES) headState = HeadState.DOWN;
	            } else if (pitchDeg < -PITCH_ENTER) {
	                if (++holdCounter >= HOLD_FRAMES) headState = HeadState.UP;
	            } else {
	                holdCounter = 0; 
	            }
	            break;

	        case LEFT:
	        case RIGHT:
	            if (Math.abs(yawDeg) < YAW_EXIT) headState = HeadState.NEUTRAL;
	            break;

	        case UP:
	        case DOWN:
	            if (Math.abs(pitchDeg) < PITCH_EXIT) headState = HeadState.NEUTRAL;
	            break;
	    }

	    // Konsolen-Ausgabe nur bei Änderung
	    if (previousState != headState) {
	        System.out.println(">>> STATUS-WECHSEL: " + headState);
	        holdCounter = 0; // Reset nach Wechsel
	    }
	}
	
	
	@FXML
	protected void startCamera()
	{	
		if (!this.cameraActive)
		{
			// start the video capture
			this.capture.open(0);
			
			// is the video stream available?
			if (this.capture.isOpened())
			{
				this.cameraActive = true;
				
				// grab a frame every 33 ms (30 frames/sec)
				Runnable frameGrabber = new Runnable() {
					
					@Override
					public void run()
					{
						// effectively grab and process a single frame
						Mat frame = grabFrame();
						// convert and show the frame
						Image imageToShow = Utils.mat2Image(frame);
						updateImageView(originalFrame, imageToShow);
					}
				};
				
				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);
				
				// update the button content
				this.cameraButton.setText("Stop Camera");
			}
			else
			{
				// log the error
				System.err.println("Failed to open the camera connection...");
			}
		}
		else
		{
			// the camera is not active at this point
			this.cameraActive = false;
			// update again the button content
			this.cameraButton.setText("Start Camera");
			
			// stop the timer
			this.stopAcquisition();
		}
	}
	
	private Mat grabFrame()
	{
		Mat frame = new Mat();
		
		// check if the capture is open
		if (this.capture.isOpened())
		{
			try
			{
				// read the current frame
				this.capture.read(frame);
				
				// if the frame is not empty, process it
				if (!frame.empty())
				{
					// face detection
					this.detectAndDisplay(frame);
				}
				
			}
			catch (Exception e)
			{
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
		MatOfPoint2f imagePoints = new MatOfPoint2f(no, le, re, lm, rm);

		// 3D ModellPunkte
		MatOfPoint3f modelPoints = new MatOfPoint3f(
				new Point3(0.0, 0.0, 0.0),    // nose tip
				new Point3(-225.0, 170.0, -135.0), // left eye
				new Point3(225.0, 170.0, -135.0), // right eye
				new Point3(-150.0, -150.0, -125.0),// left mouth
				new Point3(150.0, -150.0, -125.0) // right mouth
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

		boolean ok = Calib3d.solvePnP(
				modelPoints, imagePoints,
				cameraMatrix, distCoeffs,
				rvec, tvec,
				false, Calib3d.SOLVEPNP_EPNP
		);
		if (!ok) return;

		rvecPrev = rvec.clone();
		tvecPrev = tvec.clone();
		hasPrevPose = true;

		Mat R = new Mat();

		Calib3d.Rodrigues(rvec, R);


		double[] e = rotationMatrixToEuler(R);

		double pitch = e[0], yaw = e[1], roll = e[2];

		if (!isCalibrated) {
			// KALIBRIERUNGSPHASE
			sumYaw += yaw;
			sumPitch += pitch;
			calibrationFramesCounter++;

			Imgproc.putText(frameBgr, "Kalibrierung... Bitte gerade schauen (" + calibrationFramesCounter + ")",
					new Point(20, 130), Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0, 165, 255), 2);

			if (calibrationFramesCounter >= MAX_CALIBRATION_FRAMES) {
				offsetYaw = sumYaw / MAX_CALIBRATION_FRAMES;
				offsetPitch = sumPitch / MAX_CALIBRATION_FRAMES;
				isCalibrated = true;
				System.out.println(">>> KALIBRIERUNG ABGESCHLOSSEN: Yaw Offset: " + offsetYaw + " Pitch Offset: " + offsetPitch);
			}
		} else {
			// NORMALER BETRIEB (Werte um Offset korrigieren)
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

		// Visuelles Feedback im Bild (GUI)
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
	    System.out.println(">>> Kalibrierung wird neu gestartet...");
	}
	
	private static double[] rotationMatrixToEuler(Mat R) {
	    double r00 = R.get(0,0)[0], r01 = R.get(0,1)[0], r02 = R.get(0,2)[0];
	    double r10 = R.get(1,0)[0], r11 = R.get(1,1)[0], r12 = R.get(1,2)[0];
	    double r20 = R.get(2,0)[0], r21 = R.get(2,1)[0], r22 = R.get(2,2)[0];

	    double sy = Math.sqrt(r00*r00 + r10*r10);
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
	    return new double[] { x*180/Math.PI, y*180/Math.PI, z*180/Math.PI };
	}





	private void detectAndDisplay(Mat frameBgr) {
     int leftHold=0,	rightHold=0;

		if (!yunetReady) return;

	    yunet.setInputSize(new Size(frameBgr.cols(), frameBgr.rows()));

	    Mat faces = new Mat();
	    yunet.detect(frameBgr, faces);

	    if (faces.empty() || faces.rows() == 0) {
	        headState = HeadState.NEUTRAL;
	        leftHold = rightHold = 0;
	        Imgproc.putText(frameBgr, "No face", new Point(20, 30),
	                Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(0,0,255), 2);
	        return;
	    }

	    int cols = faces.cols();
	    if (cols < 15) {
	        System.out.println("Unexpected YuNet output: rows=" + faces.rows() + " cols=" + cols + " type=" + faces.type());
	        return;
	    }

	    int best = 0;
	    double bestArea = -1;

	    for (int i = 0; i < faces.rows(); i++) {
	        float[] row = new float[cols];
	        faces.get(i, 0, row);
	        double area = row[2] * row[3];
	        if (area > bestArea) { bestArea = area; best = i; }
	    }

	    float[] f = new float[cols];
	    faces.get(best, 0, f);

	    double x = f[0], y = f[1], w = f[2], h = f[3];
	    double score = f[4];

		// Landmarks: left eye, right eye, nose, left mouth, right mouth
	    Point le = new Point(f[5],  f[6]);
	    Point re = new Point(f[7],  f[8]);
	    Point no = new Point(f[9],  f[10]);
	    Point lm = new Point(f[11], f[12]);
	    Point rm = new Point(f[13], f[14]);

		// Zeichnen
	    Imgproc.rectangle(frameBgr, new Point(x, y), new Point(x+w, y+h), new Scalar(0,255,0), 2);
	    Imgproc.circle(frameBgr, le, 2, new Scalar(0,255,0), -1);
	    Imgproc.circle(frameBgr, re, 2, new Scalar(0,255,0), -1);
	    Imgproc.circle(frameBgr, no, 2, new Scalar(0,255,0), -1);
	    Imgproc.circle(frameBgr, lm, 2, new Scalar(0,255,0), -1);
	    Imgproc.circle(frameBgr, rm, 2, new Scalar(0,255,0), -1);

	    Imgproc.putText(frameBgr, String.format("score=%.2f", score), new Point(20, 60),
	            Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0,255,0), 2);

	    estimatePoseFrom5Points(frameBgr, le, re, no, lm, rm);
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

