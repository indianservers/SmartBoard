#!/usr/bin/env python3
"""Export the official academic PosFormer checkpoint into Android ONNX stages.

The upstream project targets PyTorch Lightning 1.4.9. This exporter deliberately
stubs only LightningModule and the serialized ModelCheckpoint callback; inference
layers remain the unmodified official PosFormer PyTorch modules.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import types
from pathlib import Path

import torch
from torch import nn


class _LightningModule(nn.Module):
    @property
    def device(self) -> torch.device:
        value = next(self.parameters(), None)
        if value is not None:
            return value.device
        value = next(self.buffers(), None)
        return value.device if value is not None else torch.device("cpu")


def _install_compatibility_modules(source_root: Path) -> object:
    lightning = types.ModuleType("pytorch_lightning")
    callbacks = types.ModuleType("pytorch_lightning.callbacks")
    checkpoint_module = types.ModuleType("pytorch_lightning.callbacks.model_checkpoint")
    model_checkpoint = type("ModelCheckpoint", (), {})
    model_checkpoint.__module__ = checkpoint_module.__name__
    checkpoint_module.ModelCheckpoint = model_checkpoint
    callbacks.model_checkpoint = checkpoint_module
    lightning.callbacks = callbacks
    lightning.LightningModule = _LightningModule
    sys.modules[lightning.__name__] = lightning
    sys.modules[callbacks.__name__] = callbacks
    sys.modules[checkpoint_module.__name__] = checkpoint_module

    dictionary = source_root / "Pos_Former" / "datamodule" / "dictionary.txt"

    class Vocabulary:
        PAD_IDX = 0
        SOS_IDX = 1
        EOS_IDX = 2

        def __init__(self) -> None:
            words = ["<pad>", "<sos>", "<eos>"]
            words.extend(line.strip() for line in dictionary.read_text().splitlines() if line.strip())
            self.idx2word = dict(enumerate(words))

        def __len__(self) -> int:
            return len(self.idx2word)

    vocabulary = Vocabulary()
    data_module = types.ModuleType("Pos_Former.datamodule")
    data_module.vocab = vocabulary
    data_module.vocab_size = len(vocabulary)
    data_module.label_make_muti = types.SimpleNamespace()
    sys.modules[data_module.__name__] = data_module

    # Decoder.forward only needs these base classes as nn.Module containers.
    # Beam search is intentionally implemented on Android around the exported
    # decoder step, so importing the upstream training metrics is unnecessary.
    generation_module = types.ModuleType("Pos_Former.utils.generation_utils")
    generation_module.DecodeModel = _LightningModule
    generation_module.PosDecodeModel = _LightningModule
    sys.modules[generation_module.__name__] = generation_module
    return vocabulary


def _load_checkpoint(path: Path) -> dict:
    # The caller explicitly supplies the official checkpoint. Its pickle globals
    # were audited and are limited to tensors, OrderedDict and ModelCheckpoint.
    return torch.load(path, map_location="cpu", weights_only=False)


class _DecoderStep(nn.Module):
    def __init__(self, decoder: nn.Module) -> None:
        super().__init__()
        self.decoder = decoder

    def forward(
        self,
        feature: torch.Tensor,
        feature_mask: torch.Tensor,
        token_ids: torch.Tensor,
    ) -> torch.Tensor:
        logits, _ = self.decoder(feature, feature_mask, token_ids)
        return logits


def _sub_state(state: dict, prefix: str) -> dict:
    return {key.removeprefix(prefix): value for key, value in state.items() if key.startswith(prefix)}


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True, help="Official PosFormer repository root")
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    source = args.source.resolve()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    sys.path.insert(0, str(source))
    vocabulary = _install_compatibility_modules(source)

    from Pos_Former.model.decoder import Decoder
    from Pos_Former.model.encoder import Encoder
    from Pos_Former.model.transformer.arm import MaskBatchNorm2d

    # Upstream uses boolean advanced indexing to apply BatchNorm only to valid
    # image positions. ONNX exports that assignment with invalid broadcasting.
    # In eval mode BatchNorm is a fixed channel-wise affine transform, so this
    # formulation is numerically equivalent on valid pixels and explicitly
    # restores zero at padding pixels.
    def onnx_safe_masked_batch_norm(
        module: MaskBatchNorm2d,
        values: torch.Tensor,
        mask: torch.Tensor,
    ) -> torch.Tensor:
        batch_norm = module.bn
        scale = batch_norm.weight / torch.sqrt(batch_norm.running_var + batch_norm.eps)
        bias = batch_norm.bias - batch_norm.running_mean * scale
        normalized = values * scale[None, :, None, None] + bias[None, :, None, None]
        return normalized.masked_fill(mask, 0.0)

    MaskBatchNorm2d.forward = onnx_safe_masked_batch_norm

    parameters = {
        "d_model": 256,
        "growth_rate": 24,
        "num_layers": 16,
        "nhead": 8,
        "num_decoder_layers": 3,
        "dim_feedforward": 1024,
        "dropout": 0.3,
        "dc": 32,
        "cross_coverage": True,
        "self_coverage": True,
    }
    encoder = Encoder(
        d_model=parameters["d_model"],
        growth_rate=parameters["growth_rate"],
        num_layers=parameters["num_layers"],
    ).eval()
    decoder = Decoder(
        d_model=parameters["d_model"],
        nhead=parameters["nhead"],
        num_decoder_layers=parameters["num_decoder_layers"],
        dim_feedforward=parameters["dim_feedforward"],
        dropout=parameters["dropout"],
        dc=parameters["dc"],
        cross_coverage=parameters["cross_coverage"],
        self_coverage=parameters["self_coverage"],
    ).eval()

    checkpoint = _load_checkpoint(args.checkpoint.resolve())
    state = checkpoint["state_dict"]
    encoder_status = encoder.load_state_dict(_sub_state(state, "model.encoder."), strict=True)
    decoder_status = decoder.load_state_dict(_sub_state(state, "model.decoder."), strict=True)
    if encoder_status.missing_keys or encoder_status.unexpected_keys:
        raise RuntimeError(f"Encoder state mismatch: {encoder_status}")
    if decoder_status.missing_keys or decoder_status.unexpected_keys:
        raise RuntimeError(f"Decoder state mismatch: {decoder_status}")

    encoder_path = output / "posformer_encoder.onnx"
    decoder_path = output / "posformer_decoder.onnx"
    sample_image = torch.ones(1, 1, 64, 256, dtype=torch.float32)
    sample_mask = torch.zeros(1, 64, 256, dtype=torch.bool)
    with torch.no_grad():
        sample_feature, sample_feature_mask = encoder(sample_image, sample_mask)
        sample_tokens = torch.tensor([[1, 8, 9, 2]], dtype=torch.long)

    torch.onnx.export(
        encoder,
        (sample_image, sample_mask),
        encoder_path,
        input_names=["image", "image_mask"],
        output_names=["feature", "feature_mask"],
        dynamic_axes={
            "image": {2: "image_height", 3: "image_width"},
            "image_mask": {1: "image_height", 2: "image_width"},
            "feature": {1: "feature_height", 2: "feature_width"},
            "feature_mask": {1: "feature_height", 2: "feature_width"},
        },
        opset_version=17,
        dynamo=False,
    )
    torch.onnx.export(
        _DecoderStep(decoder).eval(),
        (sample_feature, sample_feature_mask, sample_tokens),
        decoder_path,
        input_names=["feature", "feature_mask", "token_ids"],
        output_names=["logits"],
        dynamic_axes={
            "feature": {1: "feature_height", 2: "feature_width"},
            "feature_mask": {1: "feature_height", 2: "feature_width"},
            "token_ids": {1: "sequence_length"},
            "logits": {1: "sequence_length"},
        },
        opset_version=17,
        dynamo=False,
    )

    vocabulary_path = output / "dictionary.txt"
    vocabulary_path.write_text(
        "\n".join(vocabulary.idx2word[index] for index in range(3, len(vocabulary))) + "\n"
    )
    artifacts = [encoder_path, decoder_path, vocabulary_path]
    metadata = {
        "model": "PosFormer",
        "upstream": "https://github.com/SJTU-DeepVisionLab/PosFormer",
        "useRestriction": "Education and academic research only",
        "checkpointEpoch": checkpoint.get("epoch"),
        "checkpointGlobalStep": checkpoint.get("global_step"),
        "vocabularySize": len(vocabulary),
        "decoder": {"beamSize": 10, "maximumTokens": 200},
        "artifacts": [
            {"name": item.name, "bytes": item.stat().st_size, "sha256": _sha256(item)}
            for item in artifacts
        ],
    }
    (output / "model_metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
    print(json.dumps(metadata, indent=2))


if __name__ == "__main__":
    main()
