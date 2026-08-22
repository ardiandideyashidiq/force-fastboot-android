# ProGuard / R8 Rules for MTK V5 Force Fastboot

# Minimal safety net for usb-serial-for-android: driver constructors are
# instantiated directly (no reflection on our paths), so only member names of
# driver classes are preserved instead of the whole package. If an on-device
# run shows CdcAcmSerialDriver instantiation failing, re-add
# `-keep class com.hoho.android.usbserial.driver.** { *; }` and retest.
-keepnames class com.hoho.android.usbserial.driver.** { *; }

# Strip Compose composition tracing strings from release builds (official rule,
# developer.android.com/develop/ui/compose/tooling/tracing).
-assumenosideeffects public class androidx.compose.runtime.ComposerKt {
    boolean isTraceInProgress();
    void traceEventStart(int,int,int,java.lang.String);
    void traceEventStart(int,java.lang.String);
    void traceEventEnd();
}

# Kotlin coroutines and Compose optimizations
-dontwarn androidx.compose.**
-dontwarn kotlinx.coroutines.**
