package com.chiwa;

import org.opencv.core.*;
import org.opencv.videoio.VideoWriter;
//import org.opencv.videoio.VideoWriter.fourcc;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.util.Arrays;

import static org.opencv.videoio.VideoWriter.fourcc;

public class VideoReconstructor {
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {
        String processedFramesFolder = "processed_frames/";
        String outputVideoPath = "output_video.avi";
        int fps = 30;

        File folder = new File(processedFramesFolder);
        File[] frameFiles = folder.listFiles();
        if (frameFiles == null) {
            System.out.println("No processed frames found!");
            return;
        }

        Arrays.sort(frameFiles); // Ensure correct order

        Mat firstFrame = Imgcodecs.imread(frameFiles[0].getAbsolutePath());
        VideoWriter videoWriter = new VideoWriter(outputVideoPath, fourcc('M', 'J', 'P', 'G'), fps, firstFrame.size());

        for (File frameFile : frameFiles) {
            Mat frame = Imgcodecs.imread(frameFile.getAbsolutePath());
            videoWriter.write(frame);
        }

        videoWriter.release();
        System.out.println("Video reconstructed: " + outputVideoPath);
    }
}
