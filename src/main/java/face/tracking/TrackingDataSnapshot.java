package face.tracking;


import org.opencv.core.Point;

// Ein einfaches Record für den Datentransport
public class TrackingDataSnapshot {
    // Die berechneten Zustände (Enums)
    public HeadState headState = HeadState.NEUTRAL;
    public  LeanState leanState = LeanState.NEUTRAL;
    public  TiltState tiltState = TiltState.NEUTRAL;

    // Die geglätteten Werte für die UI-Positionierung
    public  double yaw;
    public  double pitch;
    public  double roll;
    public  double z;

    public float[] f = new float[15];

    // Die 5 markanten Punkte für die Gesichts-Marker
    public Point[] facePoints = new Point[5];

    public TrackingDataSnapshot(HeadState head, LeanState lean, TiltState tilt,
                                double yaw, double pitch, double roll, double z,
                                Point[] facePoints, float[] f) {
        this.headState = head;
        this.leanState = lean;
        this.tiltState = tilt;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.z = z;
        for (int i = 0; i < 5; i++) { //kopieren Daten facepoints
            if (facePoints[i] != null) {
                if (this.facePoints[i] == null) this.facePoints[i] = new Point();
                this.facePoints[i].x = facePoints[i].x;
                this.facePoints[i].y = facePoints[i].y;
            }
        }
        for (int i=0; i<14;i++){
            this.f[i] = f[i];
        }


    }
}
