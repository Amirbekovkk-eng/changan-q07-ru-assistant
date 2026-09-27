# Changan Q07 Russian Assistant

Q07 adaptation of the Changan A06 Russian Assistant project.

Target:
- Changan Q07
- Android 11
- msmnile_gvmq
- com.incall.apps.speechassistant

The original Q07 SpeechAssistant is preserved as the rollback/reference version.

This project keeps the A06 scenario-execution architecture while replacing A06-specific integration points with Q07-specific adapters.

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
