# PosFormer Android export

This tooling is restricted to the confirmed education/academic deployment. Review the upstream
PosFormer terms before using the code or weights in another distribution:

- https://github.com/SJTU-DeepVisionLab/PosFormer
- https://arxiv.org/abs/2407.07764

The generated model is intentionally not committed to the application APK. Export and validate it
outside Android, then provision it as an optional model pack.

## Requirements

- Python with PyTorch, ONNX and ONNX Runtime
- `einops`
- Official PosFormer repository and `best.ckpt`

The exporter does not require legacy PyTorch Lightning. It supplies inference-only compatibility
classes and loads only the official checkpoint supplied by the operator.

## Export

```powershell
python tools/posformer/export_posformer_onnx.py `
  --source build/posformer-source `
  --checkpoint build/posformer-source/lightning_logs/version_0/checkpoints/best.ckpt `
  --output build/posformer-export
```

Expected outputs:

- `posformer_encoder.onnx`
- `posformer_decoder.onnx`
- `dictionary.txt`
- `model_metadata.json`

`model_metadata.json` records the education-only restriction, source, checkpoint step, decoder
configuration, byte sizes and SHA-256 hashes.

## Acceptance gate

The current Android integration is shadow-only. Before PosFormer can affect the primary result:

1. Implement official bidirectional beam-10 decoding parity.
2. Compare PyTorch and ONNX logits/tokens on at least 200 saved complex handwriting fixtures.
3. Run the targeted superscript, fraction, radical, matrix and multiline audit sets.
4. Run a 100-recognition native-memory soak test on emulator and physical education hardware.
5. Confirm no previously passing TexTeller result changes without a documented consensus margin.
