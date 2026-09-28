# Q07 compatibility findings — 2026-09-27

## Stock APK examined

Input: Q07 `SpeechAssistant.apk`

Confirmed:
- package: `com.incall.apps.speechassistant`
- Android 11 target device
- application: `com.incall.apps.speechassistant.application.VoiceApp`
- stock APK contains `classes.dex` through `classes6.dex`
- the relevant SpeechAssistant application/NLU/TTS classes are in `classes5.dex`
- APK signing certificate SHA-1:
  `27:19:6E:38:6B:87:5E:76:AD:F7:00:E7:EA:84:E4:C6:EE:E3:3D:FA`
- the repository AOSP platform certificate has the same SHA-1 fingerprint.

## Important architectural finding

The scenario engine used by this project provides:
- `Ru2Zh.ru2zhAll()` supports simple commands and compound commands.
- Compound clauses are serialized with a delay and sent one by one.
- A phrase is rejected as a whole if any clause is not understood, avoiding partial execution.
- `RuBridge.handlePhraseZh()` routes known commands into the stock NLU path.

## Q07 integration point

The Q07 stock APK contains:
- `com.incall.apps.speechassistant.nlu.NluManager`
- `CloudNlu`
- `BusinessController`
- `SpeechTestManager`
- `TtsPlayer`
- `TtsPlayer2`
- `SrService`
- `UiService`
- `SpeechClientService`

The Q07 bridge therefore starts from the existing native NLU boundary rather than immediately replacing the microphone/ASR layer.

## Stage 1 design

```
Russian text
    ↓
RuBridge / Ru2Zh scenario engine
    ↓
Q07 NluManager.onFinalAsrResult(...)
    ↓
Q07 native NLU / arbitration
    ↓
Q07 vehicle pipeline
```

The stock APK remains untouched. The builder creates a separate patched APK.

## Deliberate exclusions from Stage 1

Do NOT yet hook:
- Q07 microphone PCM
- `SrBaseSession`
- Chinese ASR shutdown
- TTS replacement

Those are separate stages. The reason is that the platform-specific `SrBaseSession` hook must not be assumed to exist on Q07.

## Stage 1 test trigger

The Q07 builder registers a dynamic broadcast receiver:
`com.stand.NLU`

Example:
```bash
adb shell am broadcast -a com.stand.NLU -p com.incall.apps.speechassistant --es cmd 'открой окно водителя'
```

This tests the Russian scenario → Q07 NLU boundary without involving microphone ASR.

## Rollback

The original Q07 APK is the input/reference APK and is never modified in place.


## Binary audit correction — 2026-09-28

The uploaded Q07 reference APK was rechecked directly, across all six DEX files.

- SHA-256: `261c3d9f042f2d66851a82364663ff5962267a719b6412e3518670240ca616f4`
- ZIP integrity: PASS
- DEX layout: `classes.dex` through `classes6.dex`
- `SrBaseSession`: **absent from all six DEX files** (not merely absent from classes5.dex)
- Q07 stock contains `SpeechInterfaceImpl`, `RecordController`, and `SrEngineProxy` symbols, but their exact microphone callback hook must still be recovered from the real DEX method table before any PCM hook is written.
- `NluManager` and `onFinalAsrResult` are present in the stock DEX.
- This binary audit is the basis for rejecting a blind A06 `SrBaseSession` port.

The rebuild validator now checks these invariants automatically and repeats the static gate by default for 100 cycles.
