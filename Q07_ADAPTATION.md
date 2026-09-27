# Jarvis — Changan Q07 Russian Assistant

**Maintainer: Jarvis**


Independent Q07 adaptation of the Russian voice-assistant stack.

Target:
- Changan Q07
- Android 11
- msmnile_gvmq
- com.incall.apps.speechassistant

The original Q07 SpeechAssistant is preserved as the rollback/reference version.

The project uses a Q07-specific integration layer around the scenario engine and stock Q07 NLU boundary.

## Status

Initial Q07 adaptation stage.

## Architecture

Russian ASR
→ Scenario Engine
→ Q07 Adapter
→ Q07 SpeechAssistant / SpeechAdapter
→ vehicle functions

## Important

Do not modify or overwrite the original Q07 SpeechAssistant.apk during development.
