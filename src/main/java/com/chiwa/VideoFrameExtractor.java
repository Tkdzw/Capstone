package com.chiwa;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

public class VideoFrameExtractor {
    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    public static void extractFrames(String videoPath, String outputFolder) {
        VideoCapture cap = new VideoCapture(videoPath);
        if (!cap.isOpened()) {
            System.out.println("Error: Cannot open video file.");
            return;
        }

        Mat frame = new Mat();
        int frameCount = 0;

        while (cap.read(frame)) {
            String filePath = outputFolder + "/frame_" + frameCount + ".jpg";
            Imgcodecs.imwrite(filePath, frame);
            frameCount++;
        }

        cap.release();
        System.out.println("Extracted " + frameCount + " frames.");
    }

    public static void main(String[] args) {
        extractFrames("input_video.mp4", "frames");
    }
}

