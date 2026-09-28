# Q07 RU Voice — clean rebuild

This branch is a clean rebuild line for Changan Q07 (Android 11, msmnile_gvmq).

## Rules
- Stock Q07 SpeechAssistant.apk is reference-only and must never be modified in place.
- A06 is used as behavioral/reference architecture, not as a source of assumed Q07 class names.
- Every Q07 hook must be proven against the actual stock Q07 DEX before injection.
- No APK is release-ready until static checks, build checks, signing checks and runtime checks pass.

## Pipeline target
Stock Q07 audio/ASR boundary -> Russian ASR -> RuBridge -> Q07 NluManager -> stock vehicle action pipeline -> Russian TTS.

The first task is to identify the real Q07 audio callback and its exact method signature. SrBaseSession is not assumed to exist on Q07.
