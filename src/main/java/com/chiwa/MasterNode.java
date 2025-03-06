package com.chiwa;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MasterNode {
    private static final int PORT = 5000;
    private static final String VIDEO_PATH = "videos/input_video.mp4"; // Path to the input video
    private static final String OUTPUT_VIDEO_PATH = "processed_videos/output_video.mp4"; // Path to save the processed video
    private static final String AUDIO_PATH = "audio.mp3"; // Path to save the extracted audio
    private static final String FINAL_OUTPUT_PATH = "processed_videos/output_video_with_audio.mp4"; // Path to save the final video with audio
    private static final int NUM_WORKERS = 3; // Number of worker nodes to wait for

    public static void main(String[] args) {
        // Load OpenCV native library
        System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);

        try {
            System.out.println("Starting MasterNode...");

            // Step 1: Extract audio from the original video
            System.out.println("Extracting audio...");
            extractAudio(VIDEO_PATH, AUDIO_PATH);
            System.out.println("Audio extracted to: " + AUDIO_PATH);

            // Step 2: Process video frames
            System.out.println("Processing video frames...");
            processVideoFrames();

            // Step 3: Reattach audio to the processed video
            System.out.println("Reattaching audio...");
            attachAudio(OUTPUT_VIDEO_PATH, AUDIO_PATH, FINAL_OUTPUT_PATH);
            System.out.println("Audio reattached to: " + FINAL_OUTPUT_PATH);

            System.out.println("MasterNode completed successfully.");

        } catch (IOException e) {
            System.err.println("Error: IOException occurred");
            e.printStackTrace();
        }
    }

    private static void processVideoFrames() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Master Node: Waiting for " + NUM_WORKERS + " workers to connect...");

            // Wait for all workers to connect
            List<Socket> workerSockets = new ArrayList<>();
            for (int i = 0; i < NUM_WORKERS; i++) {
                Socket workerSocket = serverSocket.accept();
                workerSockets.add(workerSocket);
                System.out.println("Worker " + (i + 1) + " connected: " + workerSocket.getInetAddress());
            }

            // Open the video file
            VideoCapture videoCapture = new VideoCapture(VIDEO_PATH);
            if (!videoCapture.isOpened()) {
                System.err.println("Error: Could not open video file: " + VIDEO_PATH);
                return;
            }

            // Get video properties
            int frameWidth = (int) videoCapture.get(Videoio.CAP_PROP_FRAME_WIDTH);
            int frameHeight = (int) videoCapture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
            double fps = videoCapture.get(Videoio.CAP_PROP_FPS);

            // Initialize VideoWriter to save the processed video
            VideoWriter videoWriter = new VideoWriter(
                    OUTPUT_VIDEO_PATH,
                    VideoWriter.fourcc('X', '2', '6', '4'), // Codec (e.g., X264)
                    fps,
                    new Size(frameWidth, frameHeight)
            );

            if (!videoWriter.isOpened()) {
                System.err.println("Error: Could not open video writer for: " + OUTPUT_VIDEO_PATH);
                return;
            }

            Mat frame = new Mat();
            int frameCount = 0;
            int workerIndex = 0;

            while (videoCapture.read(frame)) {
                System.out.println("Processing frame: " + frameCount);

                // Select the next worker in a round-robin fashion
                Socket workerSocket = workerSockets.get(workerIndex);
                workerIndex = (workerIndex + 1) % NUM_WORKERS;

                // Send the frame to the worker
                sendFrame(workerSocket, frame);

                // Receive the processed frame from the worker
                Mat processedFrame = receiveProcessedFrame(workerSocket);

                // Write the processed frame to the output video
                videoWriter.write(processedFrame);

                frameCount++;
            }

            // Release resources
            videoCapture.release();
            videoWriter.release();
            System.out.println("Video processing completed. Total frames processed: " + frameCount);
            System.out.println("Processed video saved to: " + OUTPUT_VIDEO_PATH);

            // Send termination signal to all workers
            for (Socket workerSocket : workerSockets) {
                DataOutputStream out = new DataOutputStream(workerSocket.getOutputStream());
                out.writeInt(-1); // Send termination signal
                out.flush();
                System.out.println("Sent termination signal to worker: " + workerSocket.getInetAddress());
                workerSocket.close();
            }

        } catch (IOException e) {
            System.err.println("Error: IOException occurred in processVideoFrames");
            e.printStackTrace();
        }
    }

    private static void sendFrame(Socket socket, Mat frame) throws IOException {
        // Convert the frame to a byte array
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".jpg", frame, buffer);
        byte[] imageData = buffer.toArray();

        // Send the size of the frame first
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.writeInt(imageData.length);
        out.write(imageData);
        out.flush();
        System.out.println("Sent frame to worker");
    }

    private static Mat receiveProcessedFrame(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());

        // Read the size of the processed frame
        int frameSize = in.readInt();
        byte[] processedImageData = new byte[frameSize];

        // Read the processed frame data
        in.readFully(processedImageData);

        // Convert the byte array back to a Mat
        return Imgcodecs.imdecode(new MatOfByte(processedImageData), Imgcodecs.IMREAD_COLOR);
    }

    private static void extractAudio(String inputVideoPath, String outputAudioPath) throws IOException {
        System.out.println("Debug: Extracting audio from " + inputVideoPath + " to " + outputAudioPath);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg", "-i", inputVideoPath, "-q:a", "0", "-map", "a", outputAudioPath
        );
        Process process = processBuilder.start();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            System.err.println("Error: InterruptedException occurred in extractAudio");
            e.printStackTrace();
        }
    }

    private static void attachAudio(String inputVideoPath, String inputAudioPath, String outputVideoPath) throws IOException {
        System.out.println("Debug: Attaching audio from " + inputAudioPath + " to " + inputVideoPath);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg", "-i", inputVideoPath, "-i", inputAudioPath, "-c:v", "copy", "-c:a", "aac", "-strict", "experimental", outputVideoPath
        );
        Process process = processBuilder.start();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            System.err.println("Error: InterruptedException occurred in attachAudio");
            e.printStackTrace();
        }
    }
}