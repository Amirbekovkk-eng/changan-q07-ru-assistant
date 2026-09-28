#!/usr/bin/env python3
"""
Q07 APK regression gate.

Usage:
  python3 tools/q07_validate.py STOCK.apk OUTPUT.apk [cycles]

The validator is deliberately boring and repetitive: the same invariant set is
executed N times to catch flaky tooling/filesystem behaviour. It never writes
either APK.
"""
from __future__ import annotations
import hashlib, os, re, subprocess, sys, zipfile

EXPECTED_STOCK_SHA256 = "261c3d9f042f2d66851a82364663ff5962267a719b6412e3518670240ca616f4"
EXPECTED_PACKAGE = "com.incall.apps.speechassistant"
EXPECTED_VERSION_CODE = "20260318"
EXPECTED_VERSION_NAME = "V01.4074"
REQUIRED_DEX = ["classes.dex"] + [f"classes{i}.dex" for i in range(2, 7)]
Q07_FORBIDDEN_A06 = [b"SrBaseSession"]
Q07_REQUIRED_STOCK_STRINGS = [
    b"Lcom/incall/apps/speechassistant/nlu/NluManager;",
    b"onFinalAsrResult",
    b"Lcom/incall/apps/speechassistant/application/VoiceApp;",
    b"Lcom/incall/apps/speechassistant/controller/SpeechInterfaceImpl;",
    b"sendSpeechData",
    b"speechStart",
    b"speechEnd",
]
Q07_BRIDGE_STRINGS = [
    b"Lcom/stand/bridge/RuBridge;",
    b"Q07_NLU receiver registered",
    b"com.stand.NLU",
]

def sha256_file(path):
    h=hashlib.sha256()
    with open(path,"rb") as f:
        for b in iter(lambda:f.read(1024*1024), b""): h.update(b)
    return h.hexdigest()

def cert_fingerprint(apk):
    apksigner=os.environ.get("APKSIGNER")
    if not apksigner:
        sdk=os.environ.get("ANDROID_HOME","")
        bt=os.path.join(sdk,"build-tools","34.0.0","apksigner")
        apksigner=bt if os.path.exists(bt) else "apksigner"
    p=subprocess.run([apksigner,"verify","--print-certs",apk],text=True,capture_output=True)
    if p.returncode:
        raise RuntimeError("apksigner verify failed for %s: %s"%(apk,p.stderr.strip()))
    m=re.search(r"SHA-1 digest:\s*([0-9A-Fa-f:]+)",p.stdout)
    if not m:
        raise RuntimeError("SHA-1 certificate fingerprint not found for "+apk+"\n"+p.stdout)
    return m.group(1).lower()

def aapt_badging(apk):
    sdk=os.environ.get("ANDROID_HOME","")
    candidates=[]
    if sdk:
        candidates += [os.path.join(sdk,"build-tools","34.0.0","aapt2"),
                       os.path.join(sdk,"build-tools","34.0.0","aapt")]
    candidates += ["aapt2","aapt"]
    for exe in candidates:
        try:
            p=subprocess.run([exe,"dump","badging",apk],text=True,capture_output=True)
            if p.returncode==0:
                return p.stdout
        except FileNotFoundError:
            pass
    return ""

def check(stock,out):
    assert os.path.isfile(stock), f"stock APK missing: {stock}"
    assert os.path.isfile(out), f"output APK missing: {out}"
    assert sha256_file(stock)==EXPECTED_STOCK_SHA256, "stock SHA-256 mismatch"
    with zipfile.ZipFile(stock) as zs, zipfile.ZipFile(out) as zo:
        assert zs.testzip() is None, "stock ZIP integrity failure"
        assert zo.testzip() is None, "output ZIP integrity failure"
        sn=set(zs.namelist()); on=set(zo.namelist())
        for d in REQUIRED_DEX:
            assert d in sn, f"stock missing {d}"
        assert "classes7.dex" in on, "output missing classes7.dex"
        assert "classes7.dex" not in sn, "stock unexpectedly contains classes7.dex"
        # Q07 build must not alter the manifest.
        assert zs.read("AndroidManifest.xml")==zo.read("AndroidManifest.xml"), "manifest bytes changed"
        # Stock application dex files are immutable except the documented classes5 hook.
        for d in REQUIRED_DEX:
            if d != "classes5.dex":
                assert zs.read(d)==zo.read(d), f"{d} changed unexpectedly"
        stock_blob=b"".join(zs.read(d) for d in REQUIRED_DEX)
        out_blob=b"".join(zo.read(d) for d in REQUIRED_DEX)
        for needle in Q07_FORBIDDEN_A06:
            assert needle not in stock_blob, f"forbidden A06-only hook symbol present in Q07 stock: {needle!r}"
        for needle in Q07_REQUIRED_STOCK_STRINGS:
            assert needle in stock_blob, f"required Q07 stock symbol/string missing: {needle!r}"
        bridge=zo.read("classes7.dex")
        for needle in Q07_BRIDGE_STRINGS:
            assert needle in bridge, f"bridge artifact missing required marker: {needle!r}"
        patched5=zo.read("classes5.dex")
        if b"q07SpeechData" in patched5 or b"q07SpeechStart" in patched5 or b"q07SpeechEnd" in patched5:
            for needle in (b"q07SpeechStart",b"q07SpeechData",b"q07SpeechEnd"):
                assert needle in patched5, f"partial Q07 audio hook injection: missing {needle!r}"
        assert zs.read("classes5.dex") != patched5, "classes5.dex was not patched"
        # No unexpected non-signature payloads may appear in the minimal Q07 stage-1 rebuild.
        allowed_prefixes=("assets/gigaam/","assets/tera/","lib/arm64-v8a/")
        extra=[x for x in on-sn
               if x!="classes7.dex" and not x.startswith("META-INF/")
               and not any(x.startswith(p) for p in allowed_prefixes)]
        assert not extra, "unexpected output entries: "+", ".join(sorted(extra)[:20])
    stock_cert=cert_fingerprint(stock)
    out_cert=cert_fingerprint(out)
    assert stock_cert==out_cert, f"certificate mismatch: stock={stock_cert} output={out_cert}"
    badging=aapt_badging(stock)
    if badging:
        assert f"package: name='{EXPECTED_PACKAGE}'" in badging, "stock package mismatch"
        assert f"versionCode='{EXPECTED_VERSION_CODE}'" in badging, "stock versionCode mismatch"
        assert f"versionName='{EXPECTED_VERSION_NAME}'" in badging, "stock versionName mismatch"
    return stock_cert

def main():
    if len(sys.argv)<3:
        print("usage: q07_validate.py STOCK.apk OUTPUT.apk [cycles]",file=sys.stderr); return 2
    stock,out=sys.argv[1:3]
    cycles=int(sys.argv[3]) if len(sys.argv)>3 else 100
    if cycles<1: raise SystemExit("cycles must be >= 1")
    cert=None
    for i in range(1,cycles+1):
        cert=check(stock,out)
        if i in (1,10,25,50,75,100) or i==cycles:
            print(f"[q07-validate] cycle {i}/{cycles}: PASS cert={cert}")
    print(f"[q07-validate] ALL {cycles} CYCLES PASS")
    return 0

if __name__=="__main__":
    raise SystemExit(main())
