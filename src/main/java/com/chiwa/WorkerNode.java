package com.chiwa;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class WorkerNode {
    private static final String MASTER_IP = "localhost";
    private static final int MASTER_PORT = 5000;

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        try (Socket socket = new Socket(MASTER_IP, MASTER_PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("Worker: Connected to Master");

            while (true) {
                int frameSize = in.readInt();
                int frameNumber = in.readInt();

                // Check for termination signal
                if (frameSize == -1) {
                    System.out.println("Worker: Received termination signal. Exiting...");
                    break;
                }

                if (frameSize <= 0) {
                    System.err.println("Worker: Invalid frame size received: " + frameSize);
                    continue;
                }

                byte[] imageData = new byte[frameSize];
                in.readFully(imageData);
                System.out.println("Worker: Received frame " + frameNumber + " from Master");

                // Decode the incoming image as a color image
                Mat frame = Imgcodecs.imdecode(new MatOfByte(imageData), Imgcodecs.IMREAD_COLOR);
                if (frame.empty()) {
                    System.err.println("Worker: Failed to decode image");
                    continue;
                }

                // Process the frame while maintaining the original color
                Mat processedFrame = processFrame(frame);
                System.out.println("Worker: Frame " + frameNumber + " processed");

                // Encode the processed frame back to JPEG
                MatOfByte processedBuffer = new MatOfByte();
                Imgcodecs.imencode(".jpg", processedFrame, processedBuffer);
                byte[] processedImageData = processedBuffer.toArray();

                out.writeInt(processedImageData.length);
                out.flush();
                out.write(processedImageData);
                out.flush();
                System.out.println("Worker: Sent processed frame " + frameNumber + " back to Master");
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

    /**
     * Processes the frame by detecting edges and overlaying them in red on top of the original image.
     * This preserves the original color information.
     */
    private static Mat processFrame(Mat frame) {
        // Convert the frame to grayscale for edge detection
        Mat grayscaleFrame = new Mat();
        Imgproc.cvtColor(frame, grayscaleFrame, Imgproc.COLOR_BGR2GRAY);

        // Apply Canny edge detection to get the edges
        Mat edges = new Mat();
        Imgproc.Canny(grayscaleFrame, edges, 100, 200);

        // Create a binary mask from the detected edges
        Mat mask = new Mat();
        Imgproc.threshold(edges, mask, 1, 255, Imgproc.THRESH_BINARY);

        // Clone the original frame to overlay the edges onto it
        Mat result = frame.clone();

        // Create a red overlay image (BGR: blue=0, green=0, red=255)
        Mat redOverlay = new Mat(frame.size(), frame.type(), new Scalar(0, 0, 255));

        // Copy the red overlay to the result using the mask so that only edge locations are affected
        redOverlay.copyTo(result, mask);

        return result;
    }
}
