package face.tracking;


import java.io.*;
import java.nio.file.*;

    public class OpenCVLoader {

        private static boolean loaded = false;

        public static void init(){
            nu.pattern.OpenCV.loadLocally();
        }
        public static void load() {
            if (loaded) return;

            try {
                System.loadLibrary("opencv_java490");
                loaded = true;
            } catch (UnsatisfiedLinkError e) {
                loadFromJar();
                loaded = true;
            }
        }

        private static void loadFromJar() {
            try (InputStream in = OpenCVLoader.class.getResourceAsStream("/natives/opencv_java490.dll")) {

                File temp = File.createTempFile("opencv", ".dll");
                temp.deleteOnExit();

                Files.copy(in, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.load(temp.getAbsolutePath());

            } catch (Exception ex) {
                throw new RuntimeException("Failed to load OpenCV", ex);
            }
        }
    }
