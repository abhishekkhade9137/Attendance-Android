# Attendance-Android

A robust, offline-first Android application for automated attendance tracking using Face Recognition. The app leverages Google ML Kit for fast face detection and a TensorFlow Lite model for accurate face embeddings and recognition.

## Features

- **Real-time Face Recognition**: Mark attendance quickly using the live device camera.
- **Group Photo Recognition**: Process group photos to identify and mark attendance for multiple individuals at once.
- **Member Management**: Register new members with their face data securely.
- **Offline Capable**: All data is stored locally using Room Database, meaning the app works entirely offline without requiring a constant internet connection.
- **Attendance Logs & Dashboard**: View detailed attendance logs, track member presence, and visualize statistics.
- **Secure Storage**: Sensitive data and preferences are secured using AndroidX Security Crypto.

## Tech Stack & Architecture

- **Language**: Java
- **UI Architecture**: Standard Android Activities and Fragments.
- **Face Detection**: [Google ML Kit Face Detection](https://developers.google.com/ml-kit/vision/face-detection)
- **Face Recognition**: [TensorFlow Lite](https://www.tensorflow.org/lite) (MobileFaceNet / FaceNet).
- **Camera**: [AndroidX CameraX](https://developer.android.com/training/camerax) for a responsive, lifecycle-aware camera implementation.
- **Local Database**: [Room Persistence Library](https://developer.android.com/training/data-storage/room)
- **Data Visualization**: [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) for interactive dashboard charts.

## Project Structure

- **`LoginActivity` & `MainActivity`**: Handles user authentication and serves as the main hub with navigation to the Dashboard, Members, and Logs.
- **`RecognitionActivity`**: The core camera view for real-time face scanning and attendance logging.
- **`RegisterActivity`**: Handles capturing photos and extracting face embeddings for new members.
- **`GroupPhotoActivity`**: Interface for selecting and processing group images for batch attendance.
- **`db/`**: Contains Room Database Entities, DAOs, and the Database instance for managing `Members` and `Attendance Logs`.

## Setup Instructions

1. Clone the repository to your local machine.
2. Open the project in **Android Studio**.
3. Allow Gradle to sync and download all dependencies (ML Kit, TensorFlow Lite, CameraX, Room).
4. Connect a physical Android device or start an emulator (Minimum SDK: 24, Target SDK: 34).
5. Build and run the application.

## Permissions Required

- **Camera**: For real-time face detection and member registration.
- **Storage**: For picking group photos from the gallery.
