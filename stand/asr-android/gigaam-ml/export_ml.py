#!/usr/bin/env python3
"""Export GigaAM-Multilingual CTC (ru/kk/ky/uz/en) to a sherpa-onnx int8 model.

Mirrors k2-fsa/sherpa-onnx scripts/nemo/GigaAM/export-onnx-ctc-v3.py, plus two pins that the car's
STOCK onnxruntime 1.17.1 needs: IR version 8 (torch 2.14 + onnx 1.19 would write IR 10) and opset 17.

Outputs (in this dir): tokens.txt, multilingual_ctc.onnx (fp32, ~880 MB, not shipped), model.int8.onnx.
"""
import os
import sys

import gigaam
import onnx
import torch
from onnxruntime.quantization import QuantType, quantize_dynamic

HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
MODEL_NAME = "multilingual_ctc"
IR_VERSION = 8   # == the v3 model that already runs on the car (ORT 1.17.1 supports IR <= 9)


def add_meta_data(filename: str, meta_data: dict):
    model = onnx.load(filename)
    while len(model.metadata_props):
        model.metadata_props.pop()
    for key, value in meta_data.items():
        meta = model.metadata_props.add()
        meta.key = key
        meta.value = str(value)
    model.ir_version = IR_VERSION
    onnx.save(model, filename)


def pin_ir(filename: str):
    model = onnx.load(filename)
    model.ir_version = IR_VERSION
    onnx.save(model, filename)


def main():
    model = gigaam.load_model(MODEL_NAME, fp16_encoder=False, device="cpu",
                              download_root=os.path.join(HERE, "ckpt"))
    cfg = model.cfg
    print("=== cfg.preprocessor:", dict(cfg.preprocessor))
    enc = dict(cfg.encoder)
    print("=== cfg.encoder: feat_in=%s subsampling=%s factor=%s layers=%s d_model=%s" % (
        enc.get("feat_in"), enc.get("subsampling"), enc.get("subsampling_factor"),
        enc.get("n_layers"), enc.get("d_model")))
    vocab = list(cfg["decoding"]["vocabulary"])
    print("=== vocab (%d): %r" % (len(vocab), "".join(vocab)))

    # sherpa-onnx's IsGigaAM() fbank path is hard-wired to 64 mel / n_fft 320 / hop 160 / hann /
    # no preemph / no dc-offset / htk. Refuse to export a model those features don't match.
    p = cfg.preprocessor
    assert int(p.features) == 64, p.features
    assert int(p.get("n_fft", 320)) == 320 and int(p.get("win_length", 320)) == 320 \
        and int(p.get("hop_length", 160)) == 160, dict(p)
    assert int(enc.get("subsampling_factor", 4)) == 4, enc
    assert cfg.get("model_class", "ctc") == "ctc"

    with open("tokens.txt", "w", encoding="utf-8") as f:
        for i, s in enumerate(vocab):
            f.write(f"{s} {i}\n")
        f.write(f"<blk> {len(vocab)}\n")
    print("saved tokens.txt")

    model.to_onnx(".")   # -> ./multilingual_ctc.onnx (+ .yaml)
    src = f"./{MODEL_NAME}.onnx"
    add_meta_data(src, {
        "vocab_size": len(vocab) + 1,
        "normalize_type": "",
        "subsampling_factor": 4,
        "model_type": "EncDecCTCModel",
        "version": "1",
        "model_author": "https://github.com/salute-developers/GigaAM",
        "license": "https://github.com/salute-developers/GigaAM/blob/main/LICENSE",
        "language": "Russian,Kazakh,Kyrgyz,Uzbek,English",
        "comment": "multilingual_ctc",
        "is_giga_am": 1,
    })
    m = onnx.load(src, load_external_data=False)
    print("fp32: ir", m.ir_version, "opsets", [(o.domain, o.version) for o in m.opset_import],
          "inputs", [(i.name, [d.dim_param or d.dim_value for d in i.type.tensor_type.shape.dim])
                     for i in m.graph.input])
    quantize_dynamic(model_input=src, model_output="./model.int8.onnx", weight_type=QuantType.QUInt8)
    pin_ir("./model.int8.onnx")
    m = onnx.load("./model.int8.onnx", load_external_data=False)
    print("int8: ir", m.ir_version, "opsets", [(o.domain, o.version) for o in m.opset_import],
          "meta", {p.key: p.value for p in m.metadata_props})
    print("int8 size: %.1f MB" % (os.path.getsize("model.int8.onnx") / 1e6))


if __name__ == "__main__":
    main()
