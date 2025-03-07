# Scalable Distributed Video Processing System Using Parallel Computing

## 📌 Project Overview
This project processes video frames using a **distributed computing system** and **parallel processing**.  
It splits a video into frames, distributes them across multiple worker nodes (**distributed computing**), and processes frames in parallel (**parallel computing**).  

## 🚀 Features
✅ **Distributed Computing** (Master-Worker Model)  
✅ **Parallel Frame Processing** using Java Threads  
✅ **OpenCV Integration** for video processing  
✅ **Scalability** with multiple workers  
✅ **Communication** via TCP sockets  

## ⚙️ Technologies Used
- **Java** (Threads for parallelism)  
- **OpenCV** (Image processing)  
- **Sockets (TCP/IP)** (Communication between Master & Worker nodes)  
- **Multi-threading** (Parallel processing on worker nodes)  

---
## 🏗️ Project Structure

📂 Capstone-Project
┣ 📂 src/com/chiwa
┃ ┣ 📜 MasterNode.java       # Manages worker nodes & distributes frames
┃ ┣ 📜 WorkerNode.java       # Processes frames received from Master
┃ ┣ 📜 Utils.java            # Utility functions (frame extraction, video merging)
┃ ┣ 📜 Config.java           # Configuration settings (port, frame paths)
┣ 📂 frames                  # Stores extracted frames
┣ 📂 processed_frames        # Stores processed frames
┣ 📜 README.md               # Project documentation
┗ 📜 .gitignore              # Git ignore file

---

## 🔧 Setup & Installation

### **1️⃣ Install OpenCV**
1. **Download OpenCV** from [opencv.org](https://opencv.org/releases/)
2. Extract to a directory (e.g., `C:\opencv\`)
3. Set OpenCV's **Java library path**:  
   - Windows: `C:\opencv\build\java\x64`
   - Linux: `/usr/local/lib/`

---

### **2️⃣ Add OpenCV to Java**
#### **IntelliJ Setup**
1. **Go to** `File > Project Structure > Libraries`
2. **Add JARs:** Select `C:\opencv\build\java\opencv-4110.jar`
3. **Set VM Options:**  
   - **Go to** `Run > Edit Configurations > Add VM options`
   - **Add:**  
     ```
     -Djava.library.path="C:\opencv\build\java\x64"
     ```

---

## 🏃‍♂️ How to Run

### **1️⃣ Start the Master Node**
```sh
java com.chiwa.MasterNode
Splits the video into frames.
Distributes frames to workers.
2️⃣ Start Worker Nodes (on separate machines or locally)
sh
Copy
Edit
java com.chiwa.WorkerNode
Connects to Master.
Receives frames, processes them, and sends them back.
📜 Workflow
1️⃣ Master Node

Reads video, extracts frames.
Distributes frames to multiple Worker Nodes.
2️⃣ Worker Nodes

Receives frames, processes (e.g., grayscale, edge detection).
Sends back processed frames.
3️⃣ Master Node

Collects processed frames.
Merges them back into a video.
🔍 Example Processing
Convert video frames to grayscale
Apply edge detection
Resize or enhance frames
🛠️ Future Improvements
GPU Acceleration (CUDA, OpenCL)
Dynamic Load Balancing (Intelligent frame distribution)
Cloud Deployment (AWS, GCP, or Azure)
🤝 Contributors
[Your Name] - Master & Worker Node Implementation
Team Members - Additional contributions
📄 License
This project is licensed under the MIT License.

🎯 Happy Coding! 🚀


This `README.md` covers:  
✅ **Setup & Installation**  
✅ **Project Structure**  
✅ **How to Run**  
✅ **Features & Future Improvements**  


