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
    private static final int NUM_WORKERS = 3; // Number of worker nodes to wait for

    public static void main(String[] args) {
        // Load OpenCV native library
        System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);

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

            // After processing all frames, send termination signal to all workers
            for (Socket workerSocket : workerSockets) {
                DataOutputStream out = new DataOutputStream(workerSocket.getOutputStream());
                out.writeInt(-1); // Send termination signal
                out.flush();
                System.out.println("Sent termination signal to worker: " + workerSocket.getInetAddress());
            }

            // Release resources
            videoCapture.release();
            videoWriter.release();
            System.out.println("Video processing completed. Total frames processed: " + frameCount);
            System.out.println("Processed video saved to: " + OUTPUT_VIDEO_PATH);

            // Close worker connections
            for (Socket workerSocket : workerSockets) {
                workerSocket.close();
            }

        } catch (IOException e) {
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
}