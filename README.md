# Face Attendance

A robust, offline-first Android application designed for seamless and automated attendance tracking using advanced Face Recognition technology. The app leverages the power of Google's ML Kit for rapid face detection and a local TensorFlow Lite (TFLite) MobileFaceNet/FaceNet model for generating accurate face embeddings.

Designed with privacy and speed in mind, all processing and data storage happens locally on the device, meaning the app is fully functional without requiring an internet connection.

---

## 🌟 Core Features

- **Real-time Live Recognition**: Utilize the device's camera to scan and identify individuals in real-time. The app uses an optimized processing queue to handle multiple faces smoothly without lagging the UI.
- **Group Photo Recognition**: Upload or select a group photo from the gallery, and the app will detect and identify all registered individuals in the photo simultaneously, marking their attendance in bulk.
- **Secure Member Registration**: Easily enroll new members by taking a photo. The app extracts the facial embedding and securely stores it alongside their details in the local database.
- **Smart Learning System**: When you correct a misidentified face or tag someone in a group photo, the app captures that new facial template and stores up to 5 diverse representations of that person to continually improve accuracy.
- **Offline Capable & Privacy-First**: 100% of the biometric processing and data storage is handled entirely on-device using a local SQLite database (via Room), ensuring maximum privacy and no dependency on cloud APIs.
- **Comprehensive Dashboard & Logs**: Track presence history and view detailed attendance logs in real-time with a modern 4-grid statistics dashboard.
- **CSV Export & Sharing**: Export your attendance logs directly to a CSV file and share it via email, WhatsApp, or any other app seamlessly.

---

## 🏗️ Technical Architecture

### 1. Artificial Intelligence & Machine Learning (AI/ML)
- **Face Detection**: Uses **[Google ML Kit Vision](https://developers.google.com/ml-kit/vision/face-detection)** to detect face boundaries and tracking IDs in the camera feed.
- **Face Embedding (Recognition)**: A **TensorFlow Lite (`facenet.tflite`)** model is used to convert the cropped face bitmaps into a mathematical 192-dimensional vector (embedding). 
- **Matching Algorithm**: The app uses similarity distance metrics (Cosine Similarity / L2 Distance) to compare the live embedding against all registered embeddings stored in memory to find the closest match.

### 2. Camera & Image Processing
- **CameraX API**: Employs **[AndroidX CameraX](https://developer.android.com/training/camerax)** for a lifecycle-aware, responsive, and robust camera preview and ImageAnalysis pipeline.
- **Multi-threaded Pipeline**: To maintain a high frame rate, the heavy ML operations (TFLite inference) are decoupled from the camera frame analysis using an `ExecutorService` and a `ConcurrentLinkedQueue`.

### 3. Local Data & Storage
- **Room Persistence Library**: Built on top of SQLite, **[AndroidX Room](https://developer.android.com/training/data-storage/room)** manages the `Member` and `AttendanceLog` tables, ensuring thread-safe read/write operations.
- **Security Crypto**: Sensitive shared preferences and data are securely encrypted using **[AndroidX Security Crypto](https://developer.android.com/topic/security/data)**.

---

## 🗂️ Project Structure

The codebase is organized into several key components representing the main workflows:

| Component | Description |
|-----------|-------------|
| **`MainActivity`** | The central hub utilizing a `BottomNavigationView` to switch between the Dashboard, Member List, and Attendance Logs. |
| **`LoginActivity`** | Secures access to the application and attendance logs. |
| **`RegisterActivity`** | Handles the onboarding of new members. Captures a clear face image, extracts the embedding via TFLite, and saves the `MemberEntity` to Room. |
| **`RecognitionActivity`** | The core live-scanning view. It binds the CameraX `ImageAnalysis` use-case to ML Kit, draws bounding boxes on an overlay, and queues faces for TFLite embedding generation and matching. |
| **`GroupPhotoActivity`** | Provides an interface to pick an image from the gallery, detect all faces in the static image, and run batch recognition to mark attendance for everyone found. |
| **`db/` Package** | Contains the Room `AppDatabase`, Data Access Objects (`DAO`), and Entities (`MemberEntity`, `LogEntity`) representing the data schema. |
| **`ml/Facenet`** | The generated TFLite wrapper class used to invoke the `.tflite` model. |

---

## 🚀 Application Workflow

1. **Registration**: 
   - Admin opens the Registration screen.
   - A photo of the member is taken.
   - ML Kit detects the face bounding box and crops it.
   - The cropped face is passed through the `facenet.tflite` model to generate an embedding.
   - The embedding and name are saved to the Room Database.
2. **Live Attendance**:
   - Admin opens the scanner (`RecognitionActivity`).
   - CameraX streams frames to ML Kit.
   - ML Kit assigns a temporary tracking ID to a face.
   - If the tracking ID hasn't been recognized recently, the face is queued for TFLite processing.
   - The TFLite model generates the live embedding and compares it against the database.
   - If a match is found (distance < threshold), a log entry is created in Room.
3. **Review & Export**:
   - Admin navigates to the Dashboard or Logs tab in `MainActivity` to view the generated attendance records.
   - Admin can hit the Export button to generate and share a `.csv` file containing the attendance data.

---

## 🛠️ Setup & Prerequisites

### Prerequisites
- **Android Studio** (Flamingo or newer recommended).
- A physical Android device or emulator running **API Level 24 (Android 7.0)** or higher. (A physical device is recommended for optimal camera performance).

### Installation Instructions
1. Clone the repository to your local machine:
   ```bash
   git clone https://github.com/abhishekkhade9137/Attendance-Android.git
   ```
2. Open the project in **Android Studio**.
3. Allow Gradle to sync. Ensure you have an active internet connection so Gradle can download the required ML Kit, TensorFlow, and CameraX dependencies.
4. Click **Run** (`Shift + F10`) to compile and deploy the app to your connected device.

---

## 🔐 Permissions Required

To function correctly, the app requests the following runtime permissions:
- **`CAMERA`**: Required for live face scanning and taking photos during member registration.
- **`READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`**: Required to select group photos from the gallery and export/manage attendance data.
