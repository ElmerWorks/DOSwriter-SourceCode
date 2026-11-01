# DOSwriter Hardware Abstraction Layer (HAL) Specification

## 1. Overview
The Hardware Abstraction Layer (HAL) provides a unified interface for DOSwriter to interact with device-specific hardware features, particularly on E-Ink devices (Onyx BOOX, Bigme, Supernote), while maintaining a single universal APK. It prioritizes high-performance vendor APIs when available and gracefully falls back to standard Android system calls on conventional hardware.

## 2. Architecture
The HAL is built on the **Strategy Pattern**. The core application logic communicates exclusively with the `HardwareProvider` interface.

### Component Stack:
1.  **Command Dispatcher**: Triggers hardware actions (e.g., `BrightnessUp`).
2.  **HardwareProvider Interface**: Defines the contract for all hardware interactions.
3.  **HardwareFactory**: A singleton that selects and initializes the correct implementation based on user settings and runtime discovery.
4.  **Vendor Implementations**: Specific classes (e.g., `BooxHardwareProvider`) that handle the low-level API calls.

## 3. Core Interface (`HardwareProvider.kt`)
Every provider must implement the following capabilities:

| Function | Description |
| :--- | :--- |
| `initialize(activity)` | Probes for SDKs and establishes hooks. |
| `adjustBrightness(delta)` | Incremental change to display lighting. |
| `setBrightness(level)` | Absolute change (0.0 to 1.0). |
| `setWarmLight(level)` | Controls "Moonlight" / Warm light channels. |
| `setRefreshMode(mode)` | Sets E-Ink speed (Normal, Regal, A2, X-Mode). |
| `performFullRefresh()` | Triggers a full-screen EPD clear to remove ghosting. |
| `getCapabilities()` | Returns a `HardwareCapabilities` object for UI adaptation. |

## 4. Current Implementations

### 4.1 Generic Android Provider
The default fallback for all devices.
*   **Brightness**: Uses `WindowManager.LayoutParams.screenBrightness`.
*   **Secondary Fallback**: Attempts `Settings.System.SCREEN_BRIGHTNESS` if window overrides are ignored.
*   **Capabilities**: Frontlight only.

### 4.2 BOOX (Onyx) Provider
Uses a **Reflection-Based Discovery** model to avoid static SDK dependencies.
*   **Discovery Paths**: Scans `com.onyx.android.sdk.api.device` and `com.onyx.hardware`.
*   **EPD Control**: Hooks into `EpdController` for mode switching and repainting.
*   **Lighting**: Hooks into `FrontlightManager` for dual-channel (Warm/Cold) control.

### 4.3 Bigme & Supernote Providers
Currently implemented as structured stubs. These provide the necessary entry points for future vendor-specific logic without requiring core code changes.

## 5. Dynamic UI Adaptation
The DOSwriter settings menu is **Capability-Aware**. It queries the active HAL provider before rendering:
*   **Refresh Mode Menu**: Only visible if `capabilities.hasRefreshModes` is true.
*   **Warm Light Sliders**: Only visible if `capabilities.hasWarmLight` is true.
*   **Hardware Test**: Provides a diagnostic breakdown of detected SDK hooks and signature status.

## 6. Reflection-Based Discovery Logic
To maintain the "One APK" goal, the HAL does not bundle vendor `.aar` files. It uses the following logic:
1.  Attempt to load the vendor class using the Activity's `ClassLoader`.
2.  If found, search for specific method signatures (e.g., `setFrontlightValue`).
3.  If signatures match, "SDK Active" status is set to true.
4.  If any step fails, the provider remains in "Standard Fallback" mode.

## 7. Keyboard Integration
The HAL is mapped to the following global shortcuts:
*   **Alt + [Plus] / [Minus]**: Brightness Increase / Decrease.
*   **Ctrl + Alt + R**: Request Full Screen E-Ink Refresh.
*   **Ctrl + F1 / F2**: Legacy Brightness support.

## 8. Development Roadmap
*   **Phase 1 (Complete)**: Foundation and Generic Fallback.
*   **Phase 2 (Complete)**: Multi-step Setup Wizard and Diagnostics.
*   **Phase 3 (Active)**: Reflection-based Boox integration.
*   **Phase 4 (Future)**: Bigme/Supernote SDK signature matching.
*   **Phase 5 (Future)**: Automatic "Fast Mode" activation when virtual keyboard is active.
