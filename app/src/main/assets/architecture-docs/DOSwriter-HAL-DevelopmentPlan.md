# DOSwriter Hardware Abstraction Layer (HAL): Development Plan

## 1. Executive Summary
The Hardware Abstraction Layer (HAL) provides a common interface between DOSwriter and device-specific hardware features. It enables high-performance features on E-Ink devices (like BOOX or Bigme) while maintaining a single, stable APK that remains fully compatible with standard Android tablets and phones.

## 2. Architectural Vision
*   **Strategy Pattern:** The core editor communicates with a generic `HardwareProvider` interface. The implementation (BOOX, Bigme, Generic) is swapped at runtime based on user selection or hardware detection.
*   **Graceful Fallback:** Vendor-specific APIs are treated as optional enhancements. If a BOOX SDK is not found, the app automatically reverts to the "Generic Android" provider without errors or crashes.
*   **Capability-Based UI:** The settings menu dynamically queries the active provider to determine which sliders or buttons to show (e.g., hiding "Warm Light" on devices that only support standard brightness).

## 3. Staged Development Plan

### Stage 1: The Core HAL Foundation
*   Define the `HardwareProvider` interface in `com.example.minimaltexteditor.logic.hal`.
*   Implement `GenericAndroidProvider` to handle standard screen brightness and basic system functions.
*   Refactor `MainActivity.kt` to use `hal.adjustBrightness()` instead of modifying window attributes directly.

### Stage 2: First-Run Hardware Wizard
*   Create a multi-step setup overlay for fresh installations:
    *   **Step 1:** Select Display Type (Backlit, Color E-Ink, Mono E-Ink).
    *   **Step 2:** Select Manufacturer (Auto, BOOX, Bigme, Supernote, Other).
*   Implement a "Hardware Test" diagnostic page to verify SDK detection and capabilities.

### Stage 3: BOOX (Onyx) Integration
*   Integrate the `onyx-android-sdk` into the project library path.
*   Implement `BooxHardwareProvider` with support for:
    *   Dual-channel frontlight (mixing Warm/Cold light).
    *   Refresh Mode selection (Regal, A2, X-Mode).
    *   Full-screen E-Ink refresh to clear ghosting.

### Stage 4: Enhanced Refresh Logic
*   Add a "Full Refresh" keyboard command.
*   Implement "Automatic Typing Optimization": Automatically switch to a fast-refresh mode (like A2) when the virtual keyboard is opened or high-speed text entry is detected.

## 4. Technical Implementation Notes
*   **SDK Handling:** Use a `ProviderFactory` with safety checks to ensure vendor SDK classes aren't accessed unless the hardware is confirmed, preventing crashes on non-target devices.
*   **Standardized Scales:** Map all brightness and light levels to a consistent 0–100 or 0–255 scale within the HAL so the UI logic remains simple.
*   **Permissions:** Add necessary manifest permissions (`WRITE_SETTINGS`) required for hardware light control.

## 5. Summary of Benefits
*   **Performance:** Unlocks the full potential of high-end writing tablets.
*   **Reliability:** Guarantees a functional editor on every Android device.
*   **Future-Proof:** Allows new hardware vendors (like Supernote or Bigme) to be added with minimal changes to the editor core.
