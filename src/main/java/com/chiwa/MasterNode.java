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
    private static final String VIDEO_PATH = "videos/input_video.mp4";
    private static final String OUTPUT_VIDEO_PATH = "processed_videos/output_video.mp4";
    private static final int NUM_WORKERS = 3;

    public static void main(String[] args) {
        System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);

        try {
            System.out.println("Starting MasterNode...");

            System.out.println("Processing video frames...");
            processVideoFrames();

            System.out.println("MasterNode completed successfully.");
        } catch (IOException e) {
            System.err.println("Error: IOException occurred");
            e.printStackTrace();
        }
    }

    private static void processVideoFrames() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Master Node: Waiting for " + NUM_WORKERS + " workers to connect...");

            List<Socket> workerSockets = new ArrayList<>();
            for (int i = 0; i < NUM_WORKERS; i++) {
                Socket workerSocket = serverSocket.accept();
                workerSockets.add(workerSocket);
                System.out.println("Worker " + (i + 1) + " connected: " + workerSocket.getInetAddress());
            }

            VideoCapture videoCapture = new VideoCapture(VIDEO_PATH);
            if (!videoCapture.isOpened()) {
                System.err.println("Error: Could not open video file: " + VIDEO_PATH);
                return;
            }

            int frameWidth = (int) videoCapture.get(Videoio.CAP_PROP_FRAME_WIDTH);
            int frameHeight = (int) videoCapture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
            double fps = videoCapture.get(Videoio.CAP_PROP_FPS);

            VideoWriter videoWriter = new VideoWriter(
                    OUTPUT_VIDEO_PATH,
                    VideoWriter.fourcc('X', '2', '6', '4'),
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

                Socket workerSocket = workerSockets.get(workerIndex);
                workerIndex = (workerIndex + 1) % NUM_WORKERS;

                sendFrame(workerSocket, frame, frameCount);
                Mat processedFrame = receiveProcessedFrame(workerSocket);

                videoWriter.write(processedFrame);
                frameCount++;
            }

            videoCapture.release();
            videoWriter.release();
            System.out.println("Video processing completed. Total frames processed: " + frameCount);
            System.out.println("Processed video saved to: " + OUTPUT_VIDEO_PATH);

            for (Socket workerSocket : workerSockets) {
                DataOutputStream out = new DataOutputStream(workerSocket.getOutputStream());
                out.writeInt(-1);
                out.flush();
                System.out.println("Sent termination signal to worker: " + workerSocket.getInetAddress());
                workerSocket.close();
            }

        } catch (IOException e) {
            System.err.println("Error: IOException occurred in processVideoFrames");
            e.printStackTrace();
        }
    }

    private static void sendFrame(Socket socket, Mat frame, int frameNumber) throws IOException {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".jpg", frame, buffer);
        byte[] imageData = buffer.toArray();

        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.writeInt(imageData.length);
        out.writeInt(frameNumber);
        out.flush();
        out.write(imageData);
        out.flush();
        System.out.println("Master: Sent frame " + frameNumber + " to worker.");
    }

    private static Mat receiveProcessedFrame(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());

        int frameSize = in.readInt();
        if (frameSize <= 0) {
            throw new EOFException("Invalid frame size received: " + frameSize);
        }

        byte[] processedImageData = new byte[frameSize];
        int totalRead = 0;
        while (totalRead < frameSize) {
            int bytesRead = in.read(processedImageData, totalRead, frameSize - totalRead);
            if (bytesRead == -1) {
                throw new EOFException("Stream closed before full frame received.");
            }
            totalRead += bytesRead;
        }

        System.out.println("Master: Received processed frame of size " + frameSize);
        return Imgcodecs.imdecode(new MatOfByte(processedImageData), Imgcodecs.IMREAD_COLOR);
    }
}
