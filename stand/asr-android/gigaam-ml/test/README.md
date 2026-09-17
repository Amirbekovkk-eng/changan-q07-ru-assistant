# One utterance per language for `test_onnx.py`

16 kHz mono 16‑bit WAV. Reference transcripts (what the model must print, lowercase, no punctuation):

| file | language | source | transcript |
|---|---|---|---|
| `ru.wav` | Russian | Google TTS | включи кондиционер и поставь двадцать два градуса |
| `en.wav` | English | Google TTS | turn on the air conditioner and set twenty two degrees |
| `kk.wav` | Kazakh | edge‑tts `kk-KZ-DauletNeural` | кондиционерді қосып жиырма екі градус қой |
| `uz.wav` | Uzbek | edge‑tts `uz-UZ-SardorNeural` | konditsionerni yoqing va yigirma ikki daraja qo'ying |
| `ky.wav` | Kyrgyz | FLEURS `ky_kg` validation #0 (CC‑BY‑4.0, Google) | полиция сөөк ал жерде бир күндөй турганын билдирген |

Result of the export (`model.int8.onnx`, onnxruntime 1.17.1, Apple M‑series CPU, 3 threads):
all five decode exactly, except `ky.wav` where the model writes «тургандыгын» for «турганын»
(a real dialect variant; the reference audio says it that way). Decode time ≈ 70 ms per second of audio.
