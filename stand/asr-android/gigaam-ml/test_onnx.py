#!/usr/bin/env python3
"""Decode test/*.wav with model.int8.onnx the way sherpa-onnx does it (64-mel GigaAM fbank, greedy CTC).

Run under .venv117 (onnxruntime==1.17.1 == the car's stock libonnxruntime.so) to prove the export
loads and decodes on that runtime. Usage: test_onnx.py [model.int8.onnx] [wav...]
"""
import glob
import os
import sys
import time

import kaldi_native_fbank as knf
import numpy as np
import onnxruntime as ort
import soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "model.int8.onnx")
WAVS = sys.argv[2:] or sorted(glob.glob(os.path.join(HERE, "test", "*.wav")))
TOKENS = os.path.join(os.path.dirname(MODEL), "tokens.txt")


def create_fbank():
    opts = knf.FbankOptions()
    opts.frame_opts.dither = 0
    opts.frame_opts.remove_dc_offset = False
    opts.frame_opts.preemph_coeff = 0
    opts.frame_opts.window_type = "hann"
    opts.frame_opts.round_to_power_of_two = False
    opts.mel_opts.low_freq = 0
    opts.mel_opts.high_freq = 8000
    opts.mel_opts.num_bins = 64
    return knf.OnlineFbank(opts)


def features(audio):
    fb = create_fbank()
    fb.accept_waveform(16000, audio)
    fb.input_finished()
    return np.stack([np.array(fb.get_frame(i)) for i in range(fb.num_frames_ready)]).astype(np.float32)


def main():
    print("onnxruntime", ort.__version__)
    so = ort.SessionOptions()
    so.intra_op_num_threads = 3
    sess = ort.InferenceSession(MODEL, sess_options=so, providers=["CPUExecutionProvider"])
    meta = sess.get_modelmeta().custom_metadata_map
    print("meta:", meta)
    for i in sess.get_inputs():
        print(" in ", i)
    for o in sess.get_outputs():
        print(" out", o)
    id2tok = {}
    with open(TOKENS, encoding="utf-8") as f:
        for line in f:
            fields = line.rstrip("\n").split(" ")
            if len(fields) == 2 and fields[0] == "":
                id2tok[int(fields[1])] = " "
            else:
                id2tok[int(fields[-1])] = " ".join(fields[:-1])
    blank = int(meta.get("vocab_size", len(id2tok))) - 1
    for wav in WAVS:
        audio, sr = sf.read(wav, dtype="float32", always_2d=True)
        assert sr == 16000, (wav, sr)
        audio = audio[:, 0]
        x = features(audio)
        t0 = time.time()
        lp = sess.run(None, {sess.get_inputs()[0].name: x.T[None], sess.get_inputs()[1].name: np.array([x.shape[0]], dtype=np.int64)})[0][0]
        dt = time.time() - t0
        ids = lp.argmax(-1).tolist()
        out, prev = [], -1
        for i in ids:
            if i != blank and i != prev:
                out.append(id2tok.get(i, "?"))
            prev = i
        print("%-8s %5.2fs audio, decode %4.0f ms: %s" % (os.path.basename(wav), len(audio) / 16000, dt * 1000, "".join(out)))


if __name__ == "__main__":
    main()
