package face.tracking;

import net.Gamesco.Headcontrol.client.HeadControlState;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.FaceDetectorYN;


public class HeadTrackingLogic {
    UIOverlay uiOverlay = new UIOverlay();

    //matrizen für Pose Stabilität, speichert die letzte bekannte Position
    private Mat rvecPrev;
    private Mat tvecPrev;
    private boolean hasPrevPose = false;

    // Schwellenwerte, die während der Kalibrierung dynamisch berechnet werden
    private double dynamicYawThres = 15.0;   // Startwert (Fallback)
    private double dynamicPitchThres = 12.0; // Startwert (Fallback)

    // faktor zum Verlassen eines Zustands
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

    // Variablen für Roll (Neigen zur Schulter)
    private double smoothRoll = 0;
    private double sumRoll = 0;
    private double offsetRoll = 0;
    private double maxObservedRoll = 0;
    private double dynamicRollThres = 15.0; // Standard-Fallback

    // Rohwerte aus der 3D-Schätzung
    private double rawYaw;
    private double rawPitch;
    private double rawZ;

    // Maximale Kalibrierungswerte (für dynamische Schwellen)
    private static final int MAX_CALIBRATION_FRAMES = 60; // 2 Sek bei 30 FPS


    // Variablen für Glättung
    private double smoothYaw = 0;
    private double smoothPitch = 0;
    private static final double POSEALPHA = 0.4;

    //gespeicherte gesichtspunkte
    private Point leSmooth = null;
    private Point reSmooth = null;
    private Point noSmooth = null;
    private Point lmSmooth = null;
    private Point rmSmooth = null;
    private static final double LM_ALPHA = 0.35; //Glättung für die 2D Punkte

    private static HeadState headState = HeadState.NEUTRAL; // Startposition
    private static LeanState leanState = LeanState.NEUTRAL;
    private static TiltState tiltState = TiltState.NEUTRAL;

    private int holdCounter = 0;

    private boolean yunetReady = false;

    public void setYunetReady(boolean setting){
        this.yunetReady = setting;
    }
    public synchronized boolean getCalibrationStatus(){
        return isCalibrated;
    }

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



    /**
     * Exponentieller gleitender Durschnitt (EMA)
     * Verhindert das Springen von 2D Punkten bei Bildrauschen
     * @param prev prev point
     * @param cur current point
     * @param a Glättungsfaktor
     * @return Durschschnittspunkt
     */
    private Point ema(Point prev, Point cur, double a) {
        if (prev == null) return cur;
        return new Point(
                (1 - a) * prev.x + a * cur.x,
                (1 - a) * prev.y + a * cur.y
        );
    }
    /*
     * Aktualisierung Kopfzustand anhand Yaw/Pitch Werten
     */
    private void updateHeadState(double yaw, double pitch) {
        // Dynamische Schwellenwerte aus der Kalibrierung nutzen
        double baseYawThres = this.dynamicYawThres;
        double basePitchThres = this.dynamicPitchThres;

        if (tiltState!=TiltState.NEUTRAL) {
            headState = HeadState.NEUTRAL;
            holdCounter = 0;
            return; // Methode hier beenden, keine weitere Prüfung
        }
        // Zustands-Booleans mit den gewichteten Schwellenwerten berechnen
        boolean isLeft  = yaw < -(baseYawThres);
        boolean isRight = yaw >  (baseYawThres);
        boolean isUp    = pitch < -(basePitchThres);   // Nase nach oben
        boolean isDown  = pitch >  (basePitchThres); // Nase nach unten

        // Ziel-Zustand (TargetState) ermitteln
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

        // Stability-Check (HoldCounter)
        if (targetState != headState) {
            if (++holdCounter >= 3) {
                headState = targetState;
                holdCounter = 0;
            }
        } else {
            holdCounter = 0;
        }
    }

    /**
     * Logik zur Bestimmung und Änderung des Lehnen States
     * @param relRoll Wert des Lehnwertes/Richtung
     */
    private void updateTiltState(double relRoll) {
        if (headState == HeadState.LEFT || headState == HeadState.RIGHT ||
                headState == HeadState.LEFT_UP || headState == HeadState.LEFT_DOWN ||
                headState == HeadState.RIGHT_UP || headState == HeadState.RIGHT_DOWN) {

            tiltState = TiltState.NEUTRAL;
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

    /**
     * Logik zur Bestimmung und Änderung des Vorwärts/Rückwärts States
     * @param relZ
     */
    private void updateLeanState(double relZ) {
        LeanState targetLean = LeanState.NEUTRAL;
        if (tiltState != TiltState.NEUTRAL) {
            this.leanState = LeanState.NEUTRAL;
            return;
        }

        // 3D-Z-Werte sind grober. 40-50 Einheiten sind ein guter Schwellenwert.
        double thresh = 45.0;

        // Wenn relZ NEGATIV ist, ist der aktuelle Abstand kleiner als der Kalibrierungsabstand -> FORWARD
        if (relZ < -thresh ) {
            targetLean = LeanState.FORWARD;
        }
        // Wenn relZ POSITIV ist -> BACKWARD
        else if (relZ > thresh) {
            targetLean = LeanState.BACKWARD;
        }
        if (targetLean != leanState ) {
            leanState = targetLean;
        }
    }

    /**
     * Berechnet die Kopfpose (Drehung/Verschiebung) aus 5 Gesichtspunkten
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

        MatOfDouble distCoeffs = new MatOfDouble(0, 0, 0, 0);
        Mat rvec = new Mat();
        Mat tvec = new Mat();

        //PnP Algorithms (Perspective n Point) zur Lösung der 3D position
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

        if (ok) {
            rvecPrev = rvec.clone();
            tvecPrev = tvec.clone();
            hasPrevPose = true;

            // Extrahiere Z-Distanz (Lean)
            this.rawZ = tvec.get(2, 0)[0];

            // Definiere Achsen-Endpunkte im 3D-Raum (Länge 100 Einheiten)
            MatOfPoint3f axisPoints = new MatOfPoint3f(
                    new Point3(100, 0, 0),   // X-Achse (Rot) -> Rechts
                    new Point3(0, 100, 0),   // Y-Achse (Grün) -> Unten
                    new Point3(0, 0, 100)    // Z-Achse (Blau) -> "Aus der Nase heraus"
            );

            MatOfPoint2f imagePointsProj = new MatOfPoint2f();
            Calib3d.projectPoints(axisPoints, rvec, tvec, cameraMatrix, distCoeffs, imagePointsProj);

            Point[] p = imagePointsProj.toArray();

            // Zeichne die Linien (X=Rot, Y=Grün, Z=Blau)
            Imgproc.line(frameBgr, no, p[0], new Scalar(0, 0, 255), 3); // X-Achse
            Imgproc.line(frameBgr, no, p[1], new Scalar(0, 255, 0), 3); // Y-Achse
            Imgproc.line(frameBgr, no, p[2], new Scalar(255, 0, 0), 3); // Z-Achse

            //Rodrigues transformation: Rotationsvektor -> Rotationsmatrix
            Mat rotationMatrix = new Mat();
            Calib3d.Rodrigues(rvec, rotationMatrix);
            double[] e = rotationMatrixToEuler(rotationMatrix); // Matrix -> Gradzahlen(Yaw, Pitch, Roll)
            this.rawPitch = e[0];
            this.rawYaw = e[1];
        }

    }

    /**
     * getter Funktionen für die Übertragung der States nach Minecraft
     * @return den geforderten State
     */
    public static synchronized HeadState getHeadState(){return headState;}
    public static synchronized LeanState getLeanState(){return leanState;}
    public static synchronized TiltState getTiltState(){return tiltState;}


    /* ========   Werte für Minecraft-HUD   ======== */

    // Links / Rechts (Yaw)
    public synchronized double getUiYaw() {return smoothYaw;}
    // Hoch / Runter (Pitch)
    public synchronized double getUiPitch() {return smoothPitch;}
    // Vor / Zurück (Lean)
    // NEGATIV = nach vorne lehnen (wie im OpenCV-Code)
    public synchronized double getUiRelZ() {return smoothZ;}


    /**
     * Wandelt eine 3x3 Rotationsmatrix in Euler Winkel (pitch, Yaw, Roll) um
     * @param rotationMatrix
     * @return Euler Winkel Array
     */
    private static double[] rotationMatrixToEuler(Mat rotationMatrix) {
        // extraktion der MAtrix Elemente
        double r00 = rotationMatrix.get(0, 0)[0], r01 = rotationMatrix.get(0, 1)[0], r02 = rotationMatrix.get(0, 2)[0];
        double r10 = rotationMatrix.get(1, 0)[0], r11 = rotationMatrix.get(1, 1)[0], r12 = rotationMatrix.get(1, 2)[0];
        double r20 = rotationMatrix.get(2, 0)[0], r21 = rotationMatrix.get(2, 1)[0], r22 = rotationMatrix.get(2, 2)[0];

        //Hilfsvariable zur Prüfung auf Singularität
        double sy = Math.sqrt(r00 * r00 + r10 * r10);
        boolean singular = sy < 1e-6;

        double x;
        double y;
        double z;
        if (!singular) {
            x = Math.atan2(r21, r22); //Pitch
            y = Math.atan2(-r20, sy); //Yaw
            z = Math.atan2(r10, r00); //Roll
        } else {
            //Sonderfall, blick nach oben oder unten
            x = Math.atan2(-r12, r11);
            y = Math.atan2(-r20, sy);
            z = 0;
        }
        //Umrechnung von Radian in grad
        return new double[]{x * 180 / Math.PI, y * 180 / Math.PI, z * 180 / Math.PI};
    }

    /**
     * Hauptverarbeitungsschleife pro Frame
     * @param frameBgr
     */
    void detectAndDisplay(Mat frameBgr, FaceDetectorYN yunet) {
        if (!yunetReady) return;
        if (!HeadControlState.isEnabled()) return;

        Size inputSize = frameBgr.size();
        yunet.setInputSize(inputSize);
        Mat faces = new Mat();
        yunet.detect(frameBgr, faces); // Detektion auf dem kleinen Bild

        if (faces.empty() || faces.rows() == 0) {
            Imgproc.putText(frameBgr, "No face", new Point(20, 30),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(0, 0, 255), 2);
            return;
        }


        // extrahiere Daten des ersten erkannten Gesichts
        float[] f = new float[faces.cols()];
        faces.get(0, 0, f);

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

        // Glättung (EMA) 2D Punkte
        reSmooth = ema(reSmooth, re, LM_ALPHA);
        leSmooth = ema(leSmooth, le, LM_ALPHA);
        noSmooth = ema(noSmooth, no, LM_ALPHA);
        rmSmooth = ema(rmSmooth, rm, LM_ALPHA);
        lmSmooth = ema(lmSmooth, lm, LM_ALPHA);

        //Pose Schätzung starten
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

            uiOverlay.drawCalibrationProgress(frameBgr, calibrationFramesCounter, MAX_CALIBRATION_FRAMES);

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

            Point[] currentPoints = {reSmooth, leSmooth, noSmooth, rmSmooth, lmSmooth};
            TrackingDataSnapshot currentData = new TrackingDataSnapshot(headState,leanState,tiltState,smoothYaw,
                    smoothPitch,smoothRoll,smoothZ,currentPoints,f);
            // Visualisierung
            uiOverlay.drawDirectionGrid(frameBgr, currentData, dynamicPitchThres, dynamicYawThres);
            uiOverlay.drawStatusText(frameBgr, currentData);
        }

        // Gesicht und Punkte zeichnen (Kontrolle)
        uiOverlay.drawFaceMarkers(frameBgr, f, reSmooth, leSmooth, noSmooth, rmSmooth, lmSmooth);
        faces.release();
    }

    /**
     * berechnet den 2D Rollwinkel, basierend auf der Steigung der Verbindungslinie zwischen beiden Augen
     * @param re
     * @param le
     * @return
     */
    private double calculate2DRoll(Point re, Point le) {
        if (re == null || le == null) return 0;
        double dy = le.y - re.y; //Höhenunterschied
        double dx = le.x - re.x; //Horizontaler Abstand
        // Math.atan2 liefert den Winkel im Bogenmaß, wir wandeln in Grad um
        return Math.toDegrees(Math.atan2(dy, dx));
    }
}