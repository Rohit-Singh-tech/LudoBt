# LudoBt

**LudoBt** is a professional, modern Android implementation of the classic Ludo board game. It is built from the ground up using **Jetpack Compose** and follows **Clean Architecture** principles to provide a high-performance, reactive, and scalable gaming experience.

## 🎮 Key Features

- **📶 Bluetooth Multiplayer**: Play with friends nearby without an internet connection using local device-to-device communication.
- **🌍 Online Multiplayer**: Connect with players globally through private rooms using a simple PIN-based entry system.
- **🤖 Smart AI Engine**: Challenge yourself against a sophisticated AI with four distinct difficulty levels: Easy, Medium, Hard, and Expert.
- **📱 Pass & Play**: Enjoy the classic experience of playing with 2-4 players on a single device.
- **⏮️ Move Undo**: Mistakes happen! The game includes an undo feature for local modes (AI and Pass & Play).
- **🎨 Modern UI/UX**: Features a beautiful, high-fidelity board with 3D pin-style tokens, premium gradients, and smooth animations.
- **⚙️ Customizable Setup**: Full control over player positions and colors, including forced opposite court placement for balanced 2-player matches.

## 🏗 Architecture & Design

The project strictly adheres to **Clean Architecture** to ensure maintainability and testability:

### 1. Presentation Layer (`com.ludobt.app.presentation`)
- **UI Framework**: 100% Jetpack Compose using Material 3 design system.
- **State Management**: Uses `GameViewModel` with `StateFlow` to expose immutable UI states.
- **Canvas Rendering**: The game board is custom-drawn on a Compose `Canvas` for maximum performance and visual precision.

### 2. Domain Layer (`com.ludobt.app.domain`)
- **Game Engine**: `LudoGameEngine` encapsulates all official Ludo rules, capture logic, and movement calculations.
- **AI Logic**: `LudoAiEngine` implements strategic move selection based on difficulty levels.
- **Session Management**: `SessionManager` acts as the orchestrator, handling game state transitions, move sequencing, and synchronization between local and remote players.
- **Transport Abstraction**: `GameTransport` interface allows the game logic to remain agnostic of the underlying connection method (Bluetooth vs. Cloud).

### 3. Data Layer (`com.ludobt.app.data`)
- **Bluetooth Classic**: Reliable socket-based implementation for nearby discovery and communication.
- **Cloud/Firebase**: Scalable real-time synchronization for online matches.

## 🛠 Technology Stack

- **Language**: Kotlin (utilizing Coroutines, Flow, and Kotlinx Serialization).
- **Minimum SDK**: 26 (Android 8.0 Oreo).
- **Target SDK**: 35 (Android 15).
- **Build System**: Gradle Kotlin DSL with Version Catalogs.
- **Jetpack Libraries**: Compose, Navigation, ViewModel, Lifecycle.

## 🚀 Getting Started

1. Clone the repository: `git clone https://github.com/Rohit-Singh-tech/LudoBt`
2. Open in **Android Studio Meerkat** or newer.
3. Build and run on an Android device or emulator (Min SDK 26).
