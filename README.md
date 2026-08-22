# MTK V5 Force Fastboot

An Android USB-host tool that rescues MediaTek (MTK) devices stuck in a bootloop by forcing the preloader into **fastboot mode** — no PC required.

## How it works

When an MTK device fails to boot, it can drop into **preloader mode**, exposing a CDC ACM serial port over USB (VID `0x0E8D`, PID `0x2000` / `0x0003`). This app:

1. Detects the attached preloader device (or any MTK fastboot-class interface).
2. Requests USB permission and opens the serial port at 115200 8N1.
3. Burst-sends the `FASTBOOT` command every 50 ms until the device acknowledges.
4. Waits for re-enumeration as a fastboot device (PID `0x201C` / `0x2024`, class `0xFF`, subclass `0x42`, protocol `0x03`).
5. Runs USB endpoint resets to clear stale data-toggle state, then queries `getvar` (product, bootloader version, etc.) to confirm the device is responsive.

From there you can flash with any fastboot client connected to the phone's USB port.

## Requirements

- Android 7.0+ (API 24) device with USB OTG support
- USB OTG adapter / cable
- Bootlooping MTK device that reaches preloader mode

## Building

```bash
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # release APK (signed with local debug keystore if present)
./gradlew testDebugUnitTest    # unit tests
```

## Credits

- [R0rt1z2](https://github.com/R0rt1z2) (Roger Ortiz) — shared the force-fastboot mechanism (preloader serial `FASTBOOT` command) that this tool is built around
- [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) by mik3y — USB serial driver library used for preloader CDC ACM communication
- [Jetpack Compose](https://developer.android.com/compose) and Material 3 — UI framework
- AndroidX Baseline Profiles tooling — startup performance optimization

## License

Released under the [GNU General Public License v3.0](LICENSE).
