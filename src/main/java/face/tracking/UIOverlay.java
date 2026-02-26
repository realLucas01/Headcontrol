package face.tracking;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class UIOverlay {

    public void drawEverthing(Mat frame, TrackingDataSnapshot data, double yawThres, double pitchThres){
        drawDirectionGrid(frame, data, pitchThres, yawThres);
        drawFaceMarkers(frame,data.f,
                data.facePoints[0],data.facePoints[1], data.facePoints[2], data.facePoints[3],data.facePoints[4] );
        drawStatusText(frame, data);
    }

    /**
     * Zeichnet einen vertikalen Fortschrittsbalken für das Vor- und Zurücklehnen
     *
     * @param frame
     * @param relZ
     * @param leanState
     */
    private void drawLeanBar(Mat frame, double relZ, LeanState leanState) {
        int x = frame.cols() - 40; //Position am rechten Rand
        int yMid = frame.rows() / 2;
        int barHalfHeight = 100;

        // Hintergrund-Schiene (Dunkelgrau)
        Imgproc.rectangle(frame, new Point(x, yMid - (double)barHalfHeight), new Point((double)x + 10, (double)yMid + (double)barHalfHeight), new Scalar(50, 50, 50), -1);

        // Schwellenwert-Linien (Weiß)
        double threshPx = 45.0 * 0.8; // 45 Einheiten skaliert auf die Anzeige
        Imgproc.line(frame, new Point((double)x - 5, yMid - threshPx), new Point((double)x + 15, yMid - threshPx), new Scalar(255, 255, 255), 1);
        Imgproc.line(frame, new Point((double)x - 5, yMid + threshPx), new Point((double)x + 15, yMid + threshPx), new Scalar(255, 255, 255), 1);

        // Aktuelle Position: relZ ist NEGATIV bei FORWARD (Abstand wird kleiner)
        // Wir nehmen -relZ, damit die Kugel beim Vorlehnen nach OBEN geht
        double displayPos = -relZ * 1.5;
        displayPos = Math.max(-barHalfHeight, Math.min(barHalfHeight, displayPos));

        Scalar color = (leanState == LeanState.NEUTRAL) ? new Scalar(0, 255, 0) : new Scalar(0, 255, 255);
        Imgproc.circle(frame, new Point((double)x + 5, yMid + displayPos), 7, color, -1);
    }

    /**
     * Zeichnet ein Fadenkreuz-Gitter in das Bild
     * Die Grenzen des Gitters passen sich dynamisch an die Kalibrierung an
     *
     * @param frame
     * @param data
     *
     */
    void drawDirectionGrid(Mat frame, TrackingDataSnapshot data, double dynamicPitchThres, double dynamicYawThres) {
        int w = frame.cols();
        int h = frame.rows();
        int cx = w / 2;//Bildmitte X
        int cy = h / 2; //Bildmitte Y

        double visualScale = 5.0; //Skalierung für die Visualisierung

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
        double pointerX = cx + (data.yaw * visualScale);
        double pointerY = cy + (data.pitch * visualScale);

        // Aktuellen Status als Text in die jeweilige Ecke schreiben
        Scalar pointerColor = (data.headState == HeadState.NEUTRAL) ? new Scalar(0, 255, 0) : new Scalar(0, 0, 255);
        Imgproc.circle(frame, new Point(pointerX, pointerY), 6, pointerColor, -1);

        // Lean-Balken (Vor/Zurück) zusätzlich zeichnen
        drawLeanBar(frame, data.z, data.leanState );
    }

    /**
     *
     * @param frame
     * @param calibrationFramesCounter
     * @param MAX_CALIBRATION_FRAMES
     */
    void drawCalibrationProgress(Mat frame, int calibrationFramesCounter, int MAX_CALIBRATION_FRAMES) {
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

    void drawStatusText(Mat frame, TrackingDataSnapshot data) {
        // Hintergrund-Box für Lesbarkeit
        Imgproc.rectangle(frame, new Point(10, 10), new Point(280, 120), new Scalar(0, 0, 0), -1);

        Scalar headColor = (data.headState == HeadState.NEUTRAL) ? new Scalar(0, 255, 0) : new Scalar(0, 255, 255);

        Imgproc.putText(frame, "HEAD: " + data.headState, new Point(20, 40),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, headColor, 2);
        Imgproc.putText(frame, "TILT: " + data.tiltState, new Point(20, 70),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(200, 200, 0), 2);
        Imgproc.putText(frame, "LEAN: " + data.leanState, new Point(20, 100),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255), 2);

        // Debug-Werte am unteren Rand
        String debug = String.format("Y: %.1f | P: %.1f | R: %.1f | Z: %.1f",
                data.yaw, data.pitch, data.roll, data.z);
        Imgproc.putText(frame, debug, new Point(20, (double)frame.rows() - 20),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 255, 255), 1);
    }

    void drawFaceMarkers(Mat frame, float[] f, Point re, Point le, Point no, Point rm, Point lm) {// Bounding Box
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

}
