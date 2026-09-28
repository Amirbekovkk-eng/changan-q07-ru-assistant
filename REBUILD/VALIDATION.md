# Validation gate

## Stock invariants
- package: com.incall.apps.speechassistant
- versionCode: 20260318
- versionName: V01.4074
- target: Android 11 / msmnile_gvmq
- stock SHA-256: 261c3d9f042f2d66851a82364663ff5962267a719b6412e3518670240ca616f4

## Required static gates
- Manifest unchanged except explicitly required integration metadata.
- Original classes.dex through classes6.dex preserved byte-for-byte unless a documented hook requires a change.
- New bridge dex passes d8/smali validation.
- Every injected method/class descriptor exists in the stock Q07 DEX.
- APK zip integrity passes.
- zipalign passes.
- APK signature is compatible with the stock package certificate.
- APK installs without replacing the stock system file.

## Required runtime gates on the vehicle
1. Process starts without crash.
2. Wake/steering-key path remains functional.
3. Russian ASR receives real microphone audio.
4. At least one simple Russian vehicle command reaches Q07 NLU and executes.
5. At least one compound command executes sequentially without partial execution.
6. Unknown/free-form speech follows the configured backend path or fails safely.
7. TTS output is Russian and does not break stock TTS lifecycle.
8. Repeated wake/command cycles do not leak receivers, threads or audio resources.
9. Stock vehicle functions remain unaffected outside the Russian voice path.

Static gates are not a substitute for vehicle runtime testing.
