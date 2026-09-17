# Model weights not tracked by git

Three model files exceed GitHub's 100 MB per‑file limit and are therefore **excluded from git**
(see `.gitignore`). They are required to build. Place them at exactly these paths:

| File | Size | Path |
|---|---|---|
| GigaAM‑v3 CTC (int8), Russian — `ru` build | 214 MB | `stand/asr-android/gigaam/model.int8.onnx` |
| GigaAM‑Multilingual CTC (int8), ru/kk/ky/uz/en — `multi` build | 214 MB | `stand/asr-android/gigaam-ml/model.int8.onnx` |
| TeraTTS distilled sampler (4‑step) | 245 MB | `tools/tera-tts-java/assets/models/sampler_distilled_cfg3_4step.onnx` |

All the other model files (TeraTTS `text_encoder` / `duration_predictor` / `vocoder`, the tokens, styles,
accent dict, and the JNI `.so` libs) are under the limit and **are** committed.

## Where to get them

- **GigaAM‑v3** — export/convert from the upstream repo
  <https://github.com/salute-developers/GigaAM> to a sherpa‑onnx int8 CTC `model.int8.onnx`
  (the `is_giga_am` metadata + 64‑mel feature config must be present).
- **GigaAM‑Multilingual** (`multilingual_ctc`, MIT) — reproducible export in
  `stand/asr-android/gigaam-ml/export_ml.py` (downloads the checkpoint, exports ONNX opset 17 / IR 8 —
  the car's stock onnxruntime 1.17.1 rejects newer IR — adds the sherpa metadata, int8‑quantizes). Its
  `tokens.txt` (70 chars: Latin, Russian, Kazakh/Kyrgyz letters) IS committed. `test_onnx.py` decodes
  `gigaam-ml/test/*.wav` (one sample per language) under onnxruntime 1.17.1 to prove the export.
  Only ONE of the two GigaAM files is needed per build (`VARIANT=ru` / `VARIANT=multi`).
- **TeraTTS sampler** — from the upstream TeraTTS / TeraSpace weights
  <https://github.com/Tera2Space/TeraTTS> · <https://huggingface.co/TeraSpace>.

If you distribute a release, attach these files as **release assets** (or use Git LFS) rather than
committing them.
