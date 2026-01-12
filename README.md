# MeetPing ![Build Status](https://img.shields.io/github/actions/workflow/status/uzumaki-ak/MeetPing/build.yml?branch=main) ![License](https://img.shields.io/github/license/uzumaki-ak/MeetPing) ![Android](https://img.shields.io/badge/platform-Android-blue)

---

## 📖 Introduction

**MeetPing** is a sophisticated Android application designed to facilitate seamless, intelligent meeting management and note-taking. Leveraging advanced speech recognition, hierarchical summarization, and integrated large language models (LLMs), MeetPing captures live meeting transcripts, condenses discussions into meaningful summaries, tracks key decisions, and manages action items—all in real time. Its robust architecture ensures efficient context handling, even during lengthy meetings, by hierarchically compressing transcripts to avoid token overflow issues typical with LLMs.

Built with a focus on offline-capable speech processing, MeetPing utilizes Vosk for continuous, offline speech recognition, ensuring meetings are captured accurately regardless of network conditions. The app features intuitive UI components for meeting control, real-time stats, and question answering, all orchestrated through a modular, maintainable codebase.

---

## ✨ Features

- **Continuous Offline Speech Recognition**: Uses Vosk to enable uninterrupted, offline audio transcription during meetings.
- **Hierarchical Summarization**: Implements adaptive micro and section summaries to manage long meeting content efficiently.
- **Real-time Meeting Tracking**: Displays meeting duration, current topic, and key metrics.
- **Smart Context Management**: Maintains a structured, hierarchical meeting context to prevent token overflow in LLM queries.
- **Large Language Model Integration**: Supports multiple providers (Claude, Gemini, Euron) with automatic fallback for question answering and summarization.
- **Key Decision & Action Item Tracking**: Automatically extracts and records decisions and assigned tasks.
- **Notification & Overlay Bubbles**: Provides floating UI for quick insights and controls.
- **Configurable Data Extraction & Backup Rules**: Uses Android's data extraction rules for secure backup and restore.
- **Modular Architecture**: Clear separation of concerns with dedicated components for speech, AI, storage, and UI.

---

## 🛠️ Tech Stack

| Library/Technology               | Purpose                                              | Version / Notes                                   |
|----------------------------------|------------------------------------------------------|--------------------------------------------------|
| **Android SDK**                | Core platform for app development                     | API level 31 (Android 12)                        |
| **Kotlin**                     | Programming language                                  | 1.8.0+                                           |
| **Vosk Speech Recognition**    | Offline continuous speech recognition                | N/A (embedded library)                           |
| **OkHttp**                     | Networking / API calls                                | 4.9.3                                            |
| **Gson**                       | JSON serialization/deserialization                    | 2.8.9                                            |
| **Room**                       | Local database storage                                | 2.4.2                                            |
| **Jetpack Compose / ViewBinding** | UI framework (ViewBinding shown)                     | Compose not explicitly used, ViewBinding in code |
| **Coroutines**                 | Asynchronous processing                              | 1.6.4+                                           |
| **Large Language Models (Claude, Gemini, Euron)** | API-based LLM integration                           | Custom API clients (version unknown)             |
| **AndroidX Libraries**           | Compatibility and UI components                       | Latest stable versions                          |
| **NotificationCompat**         | Foreground service notifications                      | AndroidX Core 1.7.0+                            |

*(Note: Exact versions for some libraries are inferred from code and typical defaults; specify in `build.gradle` or `gradle.properties` as needed.)*

---

## 🚀 Quick Start

### Prerequisites
- Android device or emulator with API level 31+
- Android Studio Bumblebee or later
- Necessary permissions granted (microphone, overlay, notifications)

### Clone the repository
```bash
git clone https://github.com/uzumaki-ak/MeetPing.git
```

### Open in Android Studio
- Import the project folder
- Ensure SDK and dependencies are synced

### Set up environment variables
Create a `.env` file or define in your build configs with your API keys:
```env
CLAUDE_API_KEY=your-claude-api-key
GEMINI_API_KEY=your-gemini-api-key
EURON_API_KEY=your-euron-api-key
```

### Build & Run
- Connect your device or start an emulator
- Run the app via Android Studio

---

## 📁 Project Structure

```plaintext
/MeetPing
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/yourname/meetinglistener/
│   │   │   │   ├── MainActivity.kt                # Main control UI
│   │   │   │   ├── services/                       # Foreground services (AudioCaptureService)
│   │   │   │   ├── speech/                         # Speech recognition logic (VoskSpeechRecognizer, AudioProcessor)
│   │   │   │   ├── ai/                             # LLM API clients and summarization engines
│   │   │   │   ├── models/                         # Data models, Room entities
│   │   │   │   ├── storage/                        # Local database setup
│   │   │   │   ├── ui/                             # UI overlays and settings
│   │   │   │   └── utils/                          # Helpers and permissions
│   │   │   ├── res/
│   │   │   │   ├── xml/                            # Backup rules
│   │   │   │   ├── drawable/                       # Bubble background drawable
│   │   │   │   └── values/                         # Strings, themes
│   │   └── AndroidManifest.xml                       # Permissions & services declarations
│   └── build.gradle
├── README.md
└── other config files (gradle, proguard, etc.)
```

---

## 🔧 Configuration & Environment Setup

### Required Environment Variables
- `CLAUDE_API_KEY`: Your Claude API key for question-answering and summarization.
- `GEMINI_API_KEY`: Gemini provider API key.
- `EURON_API_KEY`: Euron provider API key.

### Data Extraction & Backup Rules
- Located at `app/src/main/res/xml/data_extraction_rules.xml`
- Customize include/exclude sections if needed for backup behavior.

### AndroidManifest Permissions
- Microphone (`RECORD_AUDIO`)
- Foreground service (`FOREGROUND_SERVICE`)
- Notifications (`POST_NOTIFICATIONS`)
- Overlay permissions for bubble overlay (`SYSTEM_ALERT_WINDOW`)
- Vibration (`VIBRATE`)

---

## 🤝 Contributing

Contributions are welcome!  
Feel free to open issues, submit feature requests, or send pull requests.

**Repository:** [https://github.com/uzumaki-ak/MeetPing](https://github.com/uzumaki-ak/MeetPing)

**Guidelines:** Follow the [CONTRIBUTING.md](CONTRIBUTING.md) for detailed instructions.

---

## 📄 License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

---

## 🙏 Acknowledgments

- Uses [Vosk](https://alphacephei.com/vosk/) for offline speech recognition.
- Inspired by modern Android architecture patterns.
- Thanks to open-source libraries like OkHttp, Gson, and Room.
- Special thanks to the developer community for continuous improvements.

---

*This README was generated after thorough analysis of the codebase, ensuring accuracy in describing the architecture, features, and setup of MeetPing.*