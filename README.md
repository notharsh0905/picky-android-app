# Picky — Multi-Role Android Platform

A production-ready Android application designed to provide structured interfaces for separate user roles (Vendors and Consumers), featuring interactive reviews, real-time analytics dashboards, and state-driven navigation systems.

## Core Application Architecture

* **Modern Reactive UI:** Engineered entirely with native Jetpack Compose, replacing traditional XML views with declarative UI states and components.
* **Component-Driven Material 3:** Built with Google's Material 3 tokens, utilizing dynamic scaffolds, custom top app bars, and responsive layout cards.
* **Dual-Role Navigation Workflow:** Implements secure client-side user navigation routes that completely segregate the Customer interface from the Vendor Analytics control center.
* **Decoupled MVVM State Patterns:** Utilizes separate ViewModels to handle screen composition states, preventing lifecycle resets from wiping active fields or data buffers.

## Tech Stack & Architecture

* **Language Runtime:** Kotlin (Asynchronous Coroutines & Flow API)
* **Frontend UI Engine:** Jetpack Compose & Material Design 3
* **Software Pattern:** MVVM (Model-View-ViewModel Architecture)
* **Cloud Infrastructure Wrapper:** Firebase Core, Authentication, and Firestore Integration
* **Data Protection System:** Dynamic build-time secret injection via `local.properties` and auto-generated `BuildConfig` fields

## Installation & Setup

### 1. Set Up Environment Variables
To protect client secrets, this repository handles keys at compile time. Open your local `local.properties` file and add your key:

CHANNELS_API_KEY="your_firebase_api_key"

### 2. Compile the Android Target
Open the repository root workspace inside Android Studio. Sync project dependencies via Gradle, and build the APK target directly onto your testing emulator or developer device.
