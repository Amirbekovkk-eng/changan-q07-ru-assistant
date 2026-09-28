# Q07 RU Voice — clean rebuild

This branch is a clean rebuild line for Changan Q07 (Android 11, msmnile_gvmq).

## Rules
- Stock Q07 SpeechAssistant.apk is reference-only and must never be modified in place.
- A06 is used as behavioral/reference architecture, not as a source of assumed Q07 class names.
- Every Q07 hook must be proven against the actual stock Q07 DEX before injection.
- No APK is release-ready until static checks, build checks, signing checks and runtime checks pass.

## Pipeline target
Stock Q07 audio/ASR boundary -> Russian ASR -> RuBridge -> Q07 NluManager -> stock vehicle action pipeline -> Russian TTS.

The Q07 audio boundary is now identified from the stock DEX: SpeechInterfaceImpl.speechStart(I), sendSpeechData(I,[B), and speechEnd(I,String,String), delegating to AudioListener.onSpeechStart/onSpeechData/onSpeechEnd. A Q07-specific voice hook is implemented behind Q07_VOICE=1.

SrBaseSession is absent from all six stock Q07 DEX files and is not used by the Q07 hook.
