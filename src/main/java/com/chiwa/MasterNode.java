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
import java.util.*;
import java.util.concurrent.*;

public class MasterNode {
    private static final int PORT = 5000;
    private static final String VIDEO_PATH = "videos/input_video.mp4"; // Path to the input video
    private static final String OUTPUT_VIDEO_PATH = "processed_videos/output_video.mp4"; // Path to save the processed video
    private static final int NUM_WORKERS = 3; // Number of worker nodes to wait for

    // Class to store processed frames with their index
    private static class ProcessedFrame implements Comparable<ProcessedFrame> {
        private final int index;
        private final Mat frame;

        public ProcessedFrame(int index, Mat frame) {
            this.index = index;
            this.frame = frame;
        }

        public int getIndex() {
            return index;
        }

        public Mat getFrame() {
            return frame;
        }

        @Override
        public int compareTo(ProcessedFrame other) {
            return Integer.compare(this.index, other.index);
        }
    }

    public static void main(String[] args) {
        // Load OpenCV native library
        System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);

        try {
            // Process video frames
            processVideoFrames();
        } catch (IOException | InterruptedException e) {
            System.err.println("Error: Exception occurred");
            e.printStackTrace();
        }
    }

    private static void processVideoFrames() throws IOException, InterruptedException {
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

            // Create a thread pool for parallel processing
            ExecutorService executorService = Executors.newFixedThreadPool(NUM_WORKERS);

            // Priority queue to store processed frames in order
            PriorityQueue<ProcessedFrame> frameQueue = new PriorityQueue<>();

            Mat frame = new Mat();
            int frameCount = 0;
            int workerIndex = 0;

            while (videoCapture.read(frame)) {
                System.out.println("Sending frame: " + frameCount);

                // Select the next worker in a round-robin fashion
                Socket workerSocket = workerSockets.get(workerIndex);
                workerIndex = (workerIndex + 1) % NUM_WORKERS;

                // Submit the frame to a worker for processing
                final int currentFrameIndex = frameCount;
                executorService.submit(() -> {
                    try {
                        // Send the frame to the worker
                        sendFrame(workerSocket, frame);

                        // Receive the processed frame from the worker
                        Mat processedFrame = receiveProcessedFrame(workerSocket);

                        // Add the processed frame to the priority queue
                        synchronized (frameQueue) {
                            frameQueue.add(new ProcessedFrame(currentFrameIndex, processedFrame));
                        }
                    } catch (IOException e) {
                        System.err.println("Error: IOException occurred while processing frame " + currentFrameIndex);
                        e.printStackTrace();
                    }
                });

                frameCount++;
            }

            // Shutdown the thread pool
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            // Write frames to the output video in order
            int nextFrameIndex = 0;
            while (!frameQueue.isEmpty()) {
                ProcessedFrame processedFrame = frameQueue.poll();
                if (processedFrame.getIndex() == nextFrameIndex) {
                    videoWriter.write(processedFrame.getFrame());
                    nextFrameIndex++;
                } else {
                    // Re-insert the frame into the queue if it's not the next frame
                    frameQueue.add(processedFrame);
                }
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
}