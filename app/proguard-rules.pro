# Project-specific ProGuard rules can be added here.

# AutoValue's shaded JavaPoet code references compiler-only javax.lang.model
# types that are not present on Android. These are annotation-processing
# paths, not runtime app features; suppress only these missing-class warnings.
-dontwarn javax.lang.model.**
