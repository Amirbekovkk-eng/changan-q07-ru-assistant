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
  `27:19:6E:38:6B:87:5E:76:AD:F7:00:E7:EA:84:C6:EE:E3:3D:FA`
- the repository AOSP platform certificate has the same SHA-1 fingerprint.

## Important architectural finding

The existing A06 project already contains the scenario engine we want to preserve:
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

Those are separate stages. The reason is that the A06 `SrBaseSession` hook is platform-specific and must not be assumed to exist on Q07.

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
