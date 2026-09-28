# Known failures carried into the rebuild

1. Do not port A06 SrBaseSession hooks blindly: Q07 has a different voice stack.
2. Broadcast com.stand.NLU only tests text injection; it is not a microphone/ASR implementation.
3. Stage-2 did not prove end-to-end voice operation.
4. build_dex.sh previously broke when the repository path contained spaces; the rebuild must use arrays/null-delimited file discovery.
5. Do not use adb exec-out cat as a binary APK extraction method; use adb pull or another verified byte-preserving method.
6. Do not overwrite /system/app/SpeechAssistant/SpeechAssistant.apk during development.
7. Do not claim runtime reliability from static APK checks alone.
8. Installation syntax must be validated against the actual Q07 package/signing state; do not invent an old installation command.
