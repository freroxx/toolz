**# Background Remover — Model Audit (BR-MODELS, 2026 revamp)

> Renamed from `MODELS.md` → `BR-MODELS.md`. Code references
> (`BackgroundModel.kt:24`, `BackgroundModel.kt:116`) point here.

Every model below runs **fully on-device** and is **downloaded on-demand** from the
Model Hub. Nothing ships inside the APK. Each entry pins a SHA-256 hash enforced at
download time (`data/media/ModelDownloadManager.kt`) and cached via a
`.sha256.ok` marker so large files are never re-hashed on cold start.

One runtime: **ONNX Runtime Mobile** (`com.microsoft.onnxruntime:onnxruntime-android`
1.29.0, MIT, ~+32 MB arm64-v8a via ABI splits) for all quality tiers. The former LiteRT
Instant fallback was removed: Fast ONNX at 4.4 MB covers the tiny/fast role at
strictly better quality.

## Shipped lineup

**Pro is the default for new installs** (`BackgroundModel.default() = PRO_DETAIL`,
quality-first lineup); Ultra is the opt-in max-quality tier. Ultra runs at the full
native 1024 — the BiRefNet export fixes its input at [1,3,1024,1024] (verified from
the file with the `onnx` package), so ORT rejects any other feed size and
tiled/downscaled inference is not possible with this export. Devices under 6 GB total
RAM (or under 1.5 GB free at run time, `BackgroundRemoverViewModel.hasUltraHeadroom`)
are gated out before inference with an honest redirect to Pro instead of a native
abort. Ultra skips the NNAPI execution provider (doomed ~8 s compile then per-op CPU
fallback) and goes straight to XNNPACK → CPU (`data/media/OnnxInferenceEngine.kt`).

| Tier | Stored file | Download URL basename | Size | SHA-256 | License |
|---|---|---|---|---|---|
| Fast | `u2netp.onnx` | `u2netp.onnx` ([rembg releases](https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx), [U-2-Net](https://github.com/xuebinqin/U-2-Net)) | 4,574,861 B | `309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8` | Apache-2.0 — [LICENSE](https://github.com/xuebinqin/U-2-Net/blob/master/LICENSE) |
| Portrait | `rvm_mobilenetv3_fp32.onnx` | `rvm_mobilenetv3_fp32.onnx` ([RVM v1.0.0](https://github.com/PeterL1n/RobustVideoMatting/releases/download/v1.0.0/rvm_mobilenetv3_fp32.onnx)) | 14,975,696 B | `88d4531297118f595bf2fd60f6f566aec2e559393802d1f436c380f0cbbd2828` | GPL-3.0 — [LICENSE.txt](https://github.com/PeterL1n/RobustVideoMatting/blob/master/LICENSE.txt) |
| Pro (default) | `isnet-general-use.onnx` | `isnet-general-use.onnx` ([rembg releases](https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-general-use.onnx), [DIS/ISNet](https://github.com/xuebinqin/DIS)) | 178,648,008 B | `60920e99c45464f2ba57bee2ad08c919a52bbf852739e96947fbb4358c0d964a` | redistributed via rembg (MIT project) — verify DIS terms before commercial reuse |
| Ultra | `birefnet-general-bb_swin_v1_tiny-epoch_232.onnx` (lowercase stored name; URL basename is `BiRefNet-general-bb_swin_v1_tiny-epoch_232.onnx`) | [rembg releases](https://github.com/danielgatis/rembg/releases/download/v0.0.0/BiRefNet-general-bb_swin_v1_tiny-epoch_232.onnx) ([BiRefNet](https://github.com/ZhengPeng7/BiRefNet)) | 224,005,088 B | `5600024376f572a557870a5eb0afb1e5961636bef4e1e22132025467d0f03333` | MIT — [LICENSE](https://github.com/ZhengPeng7/BiRefNet/blob/main/LICENSE) |

Source of truth for the table: `data/media/BackgroundModel.kt`
(`downloadUrl`, `fileName`, `expectedSha256`, `expectedSizeBytes`, `inputSize`,
`gatedOnWifi`, `onnxPostSigmoid`, `warnSlowDevice`).

Model Hub behavior (`data/media/ModelDownloadManager.kt`, UI in
`ui/screens/media/BackgroundRemoverViewModel.kt`):
- Files live in `filesDir/models/`. Streams to `.tmp` then atomic rename; resume via
  `Range` (servers answering 200 restart from scratch; 403/416 on a resumed signed CDN
  URL retries once from scratch).
- Integrity: `expectedSizeBytes` sanity (slack 8 KB >5 MB, else 2 KB; quarantine when
  wildly off), HTML sniff (content-type + magic bytes), pinned SHA-256 quarantine on
  mismatch with ` stagnant `. Missing `.sha256.ok` triggers one re-verification.
- Network: Pro/Ultra set `gatedOnWifi = true` — metered connections require explicit
  consent (`downloadNeedsMeteredConsent`). Free-space precheck
  (`need + 16 MB`) before starting. Dedicated OkHttp client without logging interceptor
  (shared client would buffer 178 MB into RAM). Timeouts: connect 30 s, read 5 min,
  write 60 s. `User-Agent: Toolz-ModelHub/1.0`, `Accept-Encoding: identity`.
- Inference timeouts: 120 s Ultra/Pro, 60 s others. Ultra session is closed
  post-inference to release native RSS before CPU matting.

Preprocessing (bugs here are silent quality killers, so it's explicit per model —
`data/media/OnnxInferenceEngine.kt`, wired in
`BackgroundRemoverViewModel.runOnnxInference`):
- Fast (`fast_general`, 320): 0..1 RGB → ImageNet mean `[0.485, 0.456, 0.406]`, std
  `[0.229, 0.224, 0.225]` (`IMAGENET_PREPROCESS_320`, rembg `U2netpSession` recipe).
- Pro (`pro_detail`, 1024): 0..1 RGB → mean `[0.5, 0.5, 0.5]`, std `[1.0, 1.0, 1.0]`
  (`ISNET_PREPROCESS_1024`, rembg `DisSession` + upstream DIS training recipe).
  ImageNet stats here shift every activation and collapse the mask — this was the Pro
  "empty output" bug (mask↔u2netp IoU 0.53 → 0.94 after fixing, measured 2026-09-10).
- Ultra (`ultra_birefnet`, 1024 fixed): 0..1 RGB → ImageNet mean/std
  (`IMAGENET_PREPROCESS_1024`, rembg `BiRefNetSessionGeneral` recipe).
- Portrait (`portrait_rvm`, long edge capped at 512, aspect kept): 0..1 RGB directly,
  single still-image pass with zero `[1,1,1,1]` recurrent states (`r1i..r4i`) and
  `downsample_ratio = (256 / max(w,h)).coerceIn(0.25, 1.0)` computed from the fed
  resolution (upstream ONNX contract, `OnnxInferenceEngine.runRvm`).
- Post (`data/media/MaskDecoder.kt`): rembg-parity min-max stretch on all single-mask
  ONNX outputs (guarded: flat input → zeros, never NaN); sigmoid first for raw-logit
  exports (BiRefNet family — Ultra ships on this path via `onnxPostSigmoid = true`).
  RVM alpha is calibrated and exempt from both. Confidence is re-analysed after post
  (`MaskQualityAnalyzer`) so the matting engine gets the cooked profile.
- Matting (`util/BackgroundRemoverEngine.kt`): bounded 1440px refine, 1–2 guided-filter
  passes + gradient refinement + colour decontamination; Ultra/Pro/Portrait always run
  the full pipeline.

## Evaluated but not shipped (2026-09-10, measured on Ryzen 5600 CPU)

| Model | Size | License | Result |
|---|---|---|---|
| `BiRefNet-general-bb_swin_v1_tiny-epoch_232.onnx` (rembg releases) | 224,005,088 B | MIT (BiRefNet repo) | ✅ SHIPPED as Ultra (2026-09-10): ImageNet norm + sigmoid + min-max, single `output_image`. See shipped table. |
| `BiRefNet-general-epoch_244.onnx` and full/portrait/HR/massive/COD/DIS variants | 973–1099 MB | MIT | Excluded: phone-hostile size and inference time, verified from release asset bytes. |
| `bria-rmbg-2.0.onnx` | 1024 MB | bria-rmbg (source-available, non-commercial gating) | Excluded: size + NC license incompatible with Play-distributed GPL app. |
