# Jarvis — Russian Voice Assistant for Changan Q07

**Maintainer: Jarvis**

[English](README.en.md)

Independent Russian voice-assistant adaptation for Changan Q07.

## Target

- **Vehicle:** Changan Q07
- **Platform:** Qualcomm `msmnile_gvmq`
- **Android:** 11
- **Stock package:** `com.incall.apps.speechassistant`
- **Stock application:** `com.incall.apps.speechassistant.application.VoiceApp`

The original Q07 `SpeechAssistant.apk` is kept outside the repository as the stock reference/rollback artifact. The project builds a separate modified APK and does not modify the stock APK in place.

## Goal

The project uses an independent scenario engine for compound commands and integrates it directly with the Q07 SpeechAssistant/NLU boundary.

Target pipeline:

```
Russian speech
    ↓
Russian ASR
    ↓
RuBridge / Ru2Zh scenario engine
    ↓
Q07 NLU
    ↓
Q07 arbitration / DM
    ↓
Q07 vehicle functions
```

For compound commands:

```
«закрой все окна и выключи климат»
        ↓
command 1 → Q07 NLU
        ↓
command 2 → Q07 NLU
```

The scenario mapper already contains the important safety property: if a compound phrase contains an unknown clause, the whole phrase is rejected instead of executing only part of it.

## Q07 stock SpeechAssistant findings

The supplied Q07 APK contains the following relevant components:

- `NluManager`
- `CloudNlu`
- `BusinessController`
- `SpeechTestManager`
- `SrService`
- `SpeechClientService`
- `UiService`
- `TtsPlayer`
- `TtsPlayer2`

The APK also exposes system-level integration and Changan-specific permissions. The detailed manifest analysis is documented in `Q07_COMPATIBILITY.md`.

## Development stages

### Stage 1 — text → Q07 NLU

Already prepared in `stand/build_q07.sh`.

The modified APK receives a private development broadcast:

```bash
adb shell am broadcast \
  -a com.stand.NLU \
  -p com.incall.apps.speechassistant \
  --es cmd 'открой окно водителя'
```

The trigger calls the Russian scenario engine and then the Q07 NLU boundary. This stage deliberately does **not** replace the microphone or Chinese ASR.

### Stage 2 — Q07 microphone / ASR adapter

The A06 `SrBaseSession` PCM hook must be replaced with the actual Q07 audio/ASR callback. It is not assumed that the A06 callback exists on Q07.

### Stage 3 — Russian TTS

Adapt the existing TeraTTS/PiperCaTts integration to the Q07 TTS process and Q07 `TtsPlayer` implementation.

### Stage 4 — wake button / wake word

Adapt the A06 wake handling only after the Q07 wake path is confirmed.

### Stage 5 — full vehicle test

Test:
1. single Russian command;
2. two-command scenario;
3. three-command scenario;
4. unknown + known mixed phrase;
5. climate/window/light/media commands;
6. rollback to stock.

## Repository layout

```
stand/
  asr-android/        Russian ASR/TTS + scenario engine
  patches/            engineering reference patches and notes
  build_q07.sh        Q07 Stage-1 builder
Q07_COMPATIBILITY.md  Q07-specific findings
Q07_ADAPTATION.md     adaptation scope
```

## Important

This is an independent modification project. It is not affiliated with or endorsed by Changan Automobile.

The stock Q07 SpeechAssistant is proprietary and is not committed to this repository.
