# Codex Launcher - IntelliJ Plugin

[![Version](https://img.shields.io/badge/version-1.1.18.2-blue.svg)](https://github.com/x0x0b/codex-launcher/releases)
[![Rider](https://img.shields.io/badge/Rider-2026.1+-orange.svg)](https://www.jetbrains.com/rider/)
[![JetBrains Plugin Downloads](https://img.shields.io/jetbrains/plugin/d/28264)](https://plugins.jetbrains.com/plugin/28264-codex-launcher)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/x0x0b/codex-launcher)

> [!IMPORTANT]
> Codex is now officially integrated into JetBrains IDEs.
> Install the official integration: https://developers.openai.com/codex/ide/#jetbrains-ide-integration.
>
> With an official integration now available, this plugin is expected to transition to maintenance-only mode.

<img width="800" alt="The screenshot of Codex Launcher." src="https://github.com/user-attachments/assets/4ee3fbd8-e384-4672-94c6-e4e9041a8e0d" />

Codex Launcher is an **unofficial** IntelliJ IDEA plugin that keeps the OpenAI Codex CLI one click away inside the IDE.

> **Important:** Install the [OpenAI Codex CLI](https://github.com/openai/codex) separately before using this plugin.

> **For Windows users:** Please select your terminal shell in the plugin settings to ensure proper functionality. Go to _Settings (→ Other Settings) → Codex Launcher_.

## ✨ Features

- **One-click launch** from the toolbar or Tools menu
- **Integrated terminal** that opens a dedicated "Codex" tab in the project root
- **Completion notifications** after Codex CLI finishes processing the current run
- **Automatic file opening** for files updated by Codex
- **Built-in MCP server pairing** with guided setup for IntelliJ's MCP server (2025.2+)
- **Flexible configuration** for launch modes, models, and notifications

## 🛠️ Installation

### Prerequisites
- Rider or a compatible JetBrains IDE 2026.1 or later (build 261+); version 1.1.18.2 targets Rider 2026.1.5 and no longer supports 2025 IDEs
- OpenAI Codex CLI installed and available in your system PATH

### Installation
[![Install Plugin](https://img.shields.io/badge/Install%20Plugin-JetBrains-orange?style=for-the-badge&logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/plugin/28264-codex-launcher)

## 🚀 Usage

### Quick Start
1. **Left-click** the Codex toolbar button to insert the current file path into the selected visible Terminal tab. If editor text is selected, its line range and text are included as a multiline paste, without submitting.
2. When Terminal is hidden or has no tabs, left-click opens or focuses the dedicated "Codex" terminal. **Right-click** the button to always open or focus Codex, even when another terminal is visible.
3. Both toolbar positions use the same mouse behavior. The **Tools** menu and keyboard invocation follow the left-click behavior.
4. With Terminal visible, use **Send File or Selection to Current Terminal** in the editor context menu or selection toolbar to send context to that terminal. The terminal does not need to run Codex, and keyboard focus may remain in the editor.
5. Both manually opened **Reworked** terminals and **Classic** terminals are supported. If the current terminal is not ready, has ended, or cannot be identified, the plugin shows the reason and does not create or switch to another terminal.

Multiline selections require bracketed-paste support in the receiving shell or application. Without that support, embedded newlines may be interpreted as command submission; use file-path-only insertion instead.

### Configuration
Open **Settings (→ Other Settings) → Codex Launcher** to pick the launch mode, model, notification behavior, and auto-open options.

## 📝 Development

### Building from Source
```bash
git clone https://github.com/x0x0b/codex-launcher.git
cd codex-launcher
./gradlew buildPlugin
```

On Windows PowerShell, with JDK 21 configured, run `.\gradlew.bat buildPlugin` from the repository root. The first build downloads the Rider 2026.1.5 SDK. Install the generated ZIP from `build/distributions/` using **Settings → Plugins → Install Plugin from Disk**, then restart Rider.

### Manual Verification in Rider 2026.1.5

- Open a Reworked terminal manually before launching Codex. Left-click each toolbar button: the current file path should appear there without opening a Codex tab.
- Open several terminal tabs, including Codex. Switch tabs and send from the toolbar, editor context menu, and selection toolbar; input should follow the selected tab even with focus back in the editor.
- Send a path containing spaces or Chinese characters, then a multiline selection. Check the path, line range, and selected text, and confirm the input is not submitted.
- Right-click each toolbar button to launch or focus Codex without sending context. Hide Terminal and left-click to confirm the original launch/focus behavior.
- Try a terminal that is starting or whose shell has exited (if the tab stays open). It should report the state rather than opening another tab.
- Repeat with the Classic engine and a plugin-created Codex terminal.

## 📄 License

This project is licensed under the terms specified in the [LICENSE](LICENSE) file.
