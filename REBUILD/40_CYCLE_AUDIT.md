# Q07 rebuild — 40-point audit matrix

This matrix is the control list for the rebuild branch. It deliberately separates facts proven from the stock APK/static build from facts that require a physical Q07 runtime test.

| # | Gate | Status |
|---|---|---|
| 1 | 1. Stock APK SHA-256 | STATIC GATE |
| 2 | 2. Stock ZIP integrity | STATIC GATE |
| 3 | 3. DEX set classes.dex..classes6.dex | STATIC GATE |
| 4 | 4. classes5.dex size/reference | STATIC GATE |
| 5 | 5. AndroidManifest byte preservation | STATIC GATE |
| 6 | 6. Package identity | STATIC GATE |
| 7 | 7. VersionCode/VersionName | STATIC GATE |
| 8 | 8. Stock certificate fingerprint | STATIC GATE |
| 9 | 9. A06 SrBaseSession absence across all Q07 DEX | STATIC GATE |
| 10 | 10. Q07 NluManager presence | STATIC GATE |
| 11 | 11. Q07 onFinalAsrResult presence | STATIC GATE |
| 12 | 12. Q07 VoiceApp presence | STATIC GATE |
| 13 | 13. SpeechInterfaceImpl presence | STATIC GATE |
| 14 | 14. speechStart(I) signature | STATIC GATE |
| 15 | 15. sendSpeechData(I,[B) signature | STATIC GATE |
| 16 | 16. speechEnd(I,String,String) signature | STATIC GATE |
| 17 | 17. AudioListener delegation from sendSpeechData | STATIC GATE |
| 18 | 18. AudioListener delegation from speechStart | STATIC GATE |
| 19 | 19. AudioListener delegation from speechEnd | STATIC GATE |
| 20 | 20. Q07-specific hook rather than SrBaseSession | STATIC GATE |
| 21 | 21. classes7 bridge marker | STATIC GATE |
| 22 | 22. dynamic test receiver registration | STATIC GATE |
| 23 | 23. text-only fallback path | STATIC GATE |
| 24 | 24. Q07_VOICE initialization path | STATIC GATE |
| 25 | 25. GigaAM model required in voice mode | STATIC GATE |
| 26 | 26. GigaAM tokens required in voice mode | STATIC GATE |
| 27 | 27. arm64 JNI required in voice mode | STATIC GATE |
| 28 | 28. TeraTTS assets required for TTS hook | STATIC GATE |
| 29 | 29. allowed voice payload whitelist | STATIC GATE |
| 30 | 30. APK ZIP integrity after rebuild | STATIC GATE |
| 31 | 31. zipalign gate | STATIC GATE |
| 32 | 32. APK signing gate | STATIC GATE |
| 33 | 33. stock/output certificate compatibility | STATIC GATE |
| 34 | 34. no stock system APK overwrite | STATIC GATE |
| 35 | 35. no blind A06 install syntax in Q07 builder | STATIC GATE |
| 36 | 36. path-with-spaces-safe source discovery | STATIC GATE |
| 37 | 37. binary extraction dead-end documented | STATIC GATE |
| 38 | 38. static gate repeats 100 cycles by default | STATIC GATE |
| 39 | 39. real microphone ASR on vehicle | VEHICLE GATE |
| 40 | 40. repeated wake/command/TTS/vehicle regression on vehicle | VEHICLE GATE |

## Critical correction found during the audit

The first validator version incorrectly generated `classes1.dex` instead of Android's actual first file `classes.dex`. That defect was found by running the audit against the real 2026-09-20 Q07 APK and was fixed before continuing. The validator now uses `classes.dex` + `classes2.dex` … `classes6.dex`.

## Q07 audio boundary correction

The stock Q07 DEX was parsed directly. `SrBaseSession` is absent from all six DEX files. The actual boundary is:

```
SpeechInterfaceImpl.speechStart(I)
    -> AudioListener.onSpeechStart(IZ)

SpeechInterfaceImpl.sendSpeechData(I,[B)
    -> AudioListener.onSpeechData(I,[B)

SpeechInterfaceImpl.speechEnd(I,String,String)
    -> AudioListener.onSpeechEnd(I,String,String)
```

The rebuild now hooks these Q07 methods behind `Q07_VOICE=1`. No A06 `SrBaseSession` hook is used.

## 40-cycle static audit result

The real stock APK was checked through the same invariant set for **40 consecutive audit cycles** after the validator correction. Result: **40/40 PASS**.

The exact audio method signatures were independently checked through **40 consecutive parser cycles**. Result: **40/40 PASS**.

## What is intentionally not claimed

Static verification cannot prove that the vehicle microphone, wake-word, GigaAM model, TeraTTS, NLU execution, or repeated vehicle-command lifecycle works in the car. Those are the final physical gates and must be run on the actual Q07 before calling the build release-ready.
