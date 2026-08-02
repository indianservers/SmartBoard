package com.indianservers.smartboard.biology.future3d

enum class Biology3DObjectType { CELL, ORGANELLE, ORGAN, ORGAN_SYSTEM, MOLECULE, PROTEIN, DNA, MICROORGANISM, PLANT_STRUCTURE, ANIMAL, EMBRYO, ECOSYSTEM, LAB_EQUIPMENT }
enum class Biology3DAssetStatus { Planned, InReview, Ready }

data class Future3DObjectMetadata(
    val objectId: String,
    val conceptId: String,
    val objectType: Biology3DObjectType,
    val assetStatus: Biology3DAssetStatus = Biology3DAssetStatus.Planned,
    val defaultCameraPreset: String? = null,
    val supportedLayers: List<String>,
    val supportedLabels: List<String>,
    val futureInteractions: List<String>,
    val fallbackDiagramId: String,
)

const val FUTURE_3D_MESSAGE = "Interactive 3D model planned for a future update."
