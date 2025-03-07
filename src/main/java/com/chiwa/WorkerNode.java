package com.chiwa;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class WorkerNode {
    private static final String MASTER_IP = "localhost"; // Change to Master's IP if needed
    private static final int MASTER_PORT = 5000;

    public static void main(String[] args) {
        // Load OpenCV native library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        try (Socket socket = new Socket(MASTER_IP, MASTER_PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("Worker: Connected to Master");

            while (true) {
                // Read the size of the frame
                int frameSize = in.readInt();

                // Check for termination signal
                if (frameSize == -1) {
                    System.out.println("Worker: Received termination signal. Exiting...");
                    break;
                }

                // Read the frame data
                byte[] imageData = new byte[frameSize];
                in.readFully(imageData);
                System.out.println("Worker: Received frame from Master");

                // Convert byte array to OpenCV Mat
                Mat frame = Imgcodecs.imdecode(new MatOfByte(imageData), Imgcodecs.IMREAD_COLOR);
                if (frame.empty()) {
                    System.err.println("Worker: Failed to decode image");
                    break;
                }

                // Process the frame (e.g., grayscale conversion and edge detection)
                Mat processedFrame = processFrame(frame);
                System.out.println("Worker: Frame processed");

                // Encode processed frame back to byte array
                MatOfByte processedBuffer = new MatOfByte();
                Imgcodecs.imencode(".jpg", processedFrame, processedBuffer);
                byte[] processedImageData = processedBuffer.toArray();

                // Send the size of the processed frame first
                out.writeInt(processedImageData.length);
                out.write(processedImageData);
                out.flush();
                System.out.println("Worker: Sent processed frame back to Master");
            }

        } catch (SocketException e) {
            System.err.println("Worker: Connection reset or closed by Master");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("Worker: IOException occurred");
            e.printStackTrace();
        } finally {
            System.out.println("Worker: Disconnected");
        }
    }

    private static Mat processFrame(Mat frame) {
        // Convert to grayscale
        Mat grayscaleFrame = new Mat();
        Imgproc.cvtColor(frame, grayscaleFrame, Imgproc.COLOR_BGR2GRAY);

        // Apply edge detection (optional)
        Mat edges = new Mat();
        Imgproc.Canny(grayscaleFrame, edges, 100, 200);

        return edges; // Return the processed frame
    }
}