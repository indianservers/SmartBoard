package com.indianservers.smartboard.biology.data

import com.indianservers.smartboard.biology.future3d.Biology3DAssetStatus
import com.indianservers.smartboard.biology.future3d.Biology3DObjectType
import com.indianservers.smartboard.biology.future3d.Future3DObjectMetadata
import com.indianservers.smartboard.biology.model.*

/** Versioned offline catalogue. Detailed bodies can grow without changing hierarchy IDs or UI templates. */
object BundledBiologyCatalogue {
    const val SCHEMA_VERSION = 1

    private data class DomainSeed(val id: String, val title: String, val description: String, val icon: String, val topics: List<String>)
    private fun topics(value: String) = value.split(';').map(String::trim).filter(String::isNotBlank)

    private val seeds = listOf(
        DomainSeed("foundations", "Foundations of Biology", "How biologists study life, scale, classification and evidence.", "BIO", topics("Introduction to Biology;Characteristics of Living Organisms;Levels of Biological Organisation;Scientific Method in Biology;Biological Measurements;Units and Scales in Biology;Microscopy Basics;Observation and Experimentation;Classification of Living Organisms;Biological Terminology;Branches of Biology;History of Biological Discoveries;Laboratory Safety;Bioethics Foundations")),
        DomainSeed("cell-biology", "Cell Biology", "Cell structure, transport, communication, division and cellular fate.", "CELL", topics("Cell Theory;Prokaryotic Cells;Eukaryotic Cells;Plant Cell;Animal Cell;Cell Size and Scale;Plasma Membrane;Cell Wall;Cytoplasm;Nucleus;Nucleolus;Ribosomes;Endoplasmic Reticulum;Golgi Apparatus;Mitochondria;Chloroplasts;Lysosomes;Peroxisomes;Vacuoles;Centrosomes;Cytoskeleton;Cilia and Flagella;Cell Junctions;Extracellular Matrix;Membrane Transport;Diffusion;Osmosis;Facilitated Diffusion;Active Transport;Endocytosis;Exocytosis;Cell Signalling;Cell Communication;Cell Cycle;Mitosis;Meiosis;Cell Differentiation;Stem Cells;Cellular Ageing;Apoptosis;Necrosis;Cancer-cell Biology")),
        DomainSeed("biochemistry", "Biomolecules and Biochemistry", "Molecules, reactions and energy transformations that sustain life.", "CHEM", topics("Water and Biological Systems;Acids, Bases and Biological pH;Buffers;Carbohydrates;Monosaccharides;Disaccharides;Polysaccharides;Lipids;Fatty Acids;Triglycerides;Phospholipids;Steroids;Proteins;Amino Acids;Peptide Bonds;Protein Structure;Protein Folding;Protein Denaturation;Nucleic Acids;DNA;RNA;ATP;Vitamins;Minerals;Coenzymes;Enzymes;Enzyme Kinetics;Enzyme Inhibition;Bioenergetics;Oxidation and Reduction;Metabolic Pathways;Metabolomics Foundations;Cellular Respiration")),
        DomainSeed("genetics", "Genetics", "Inheritance from Mendelian patterns to populations and epigenetics.", "DNA", topics("Heredity;Mendelian Genetics;Monohybrid Crosses;Dihybrid Crosses;Test Crosses;Probability in Genetics;Chromosomes;Genes;Alleles;Genotype;Phenotype;Dominance;Incomplete Dominance;Codominance;Multiple Alleles;Polygenic Inheritance;Sex-linked Inheritance;Pedigree Analysis;Linkage;Crossing Over;Genetic Recombination;Chromosome Mapping;Mutations;Chromosomal Abnormalities;Population Genetics;Hardy-Weinberg Principle;Quantitative Genetics;Epigenetics;Genetic Imprinting;Genetic Counselling;Human Genetic Disorders")),
        DomainSeed("molecular-biology", "Molecular Biology", "Information flow, genome regulation and molecular technologies.", "GENE", topics("DNA Structure;DNA Replication;DNA Repair;RNA Structure;Transcription;RNA Processing;Translation;Genetic Code;Gene Regulation;Operons;Eukaryotic Gene Regulation;Non-coding RNA;MicroRNA;Chromatin;Histones;Epigenetic Regulation;Recombinant DNA;Restriction Enzymes;Cloning Vectors;Polymerase Chain Reaction;Gel Electrophoresis;DNA Sequencing;Genomics;Transcriptomics;Proteomics;CRISPR Foundations;Gene Editing;Molecular Diagnostics;Systems Biology")),
        DomainSeed("plant-biology", "Plant Biology", "Plant structure, transport, metabolism, growth and reproduction.", "LEAF", topics("Plant Characteristics;Plant Classification;Plant Cell;Plant Tissues;Meristematic Tissue;Permanent Tissue;Roots;Stems;Leaves;Flowers;Fruits;Seeds;Plant Vascular System;Xylem;Phloem;Water Absorption;Mineral Nutrition;Transpiration;Translocation;Photosynthesis;Light Reactions;Calvin Cycle;C3 Plants;C4 Plants;CAM Plants;Photorespiration;Plant Respiration;Plant Hormones;Tropisms;Nastic Movements;Photoperiodism;Vernalisation;Plant Reproduction;Pollination;Fertilisation;Seed Germination;Plant Growth and Development;Plant Stress Physiology;Plant Pathology;Plant Biotechnology")),
        DomainSeed("human-anatomy", "Human Anatomy", "Organisation and relationships of human tissues, organs and systems.", "BODY", topics("Anatomical Terminology;Body Planes;Body Cavities;Tissues;Skin and Integumentary System;Skeletal System;Bones;Joints;Muscular System;Nervous System;Brain;Spinal Cord;Peripheral Nerves;Sensory Organs;Endocrine System;Cardiovascular System;Human Heart;Blood Vessels;Blood;Lymphatic System;Immune Organs;Respiratory System;Digestive System;Liver;Gallbladder;Pancreas;Urinary System;Kidneys;Male Reproductive System;Female Reproductive System;Embryological Anatomy;Surface Anatomy;Histology;Neuroanatomy;Regional Anatomy;Clinical Anatomy")),
        DomainSeed("human-physiology", "Human Physiology", "Mechanisms that coordinate normal human body function.", "ECG", topics("Homeostasis;Membrane Physiology;Resting Membrane Potential;Action Potential;Synaptic Transmission;Muscle Physiology;Skeletal Muscle;Smooth Muscle;Cardiac Muscle;Blood Physiology;Haemostasis;Cardiac Cycle;Heart Sounds;Electrocardiogram;Cardiac Output;Blood Pressure;Blood Circulation;Microcirculation;Respiratory Mechanics;Gas Exchange;Oxygen Transport;Carbon-dioxide Transport;Regulation of Respiration;Digestion;Absorption;Gastrointestinal Motility;Liver Physiology;Kidney Function;Glomerular Filtration;Tubular Function;Fluid Balance;Electrolyte Balance;Acid-base Physiology;Endocrine Physiology;Reproductive Physiology;Pregnancy;Lactation;Temperature Regulation;Exercise Physiology;Sleep Physiology;Ageing Physiology")),
        DomainSeed("reproduction-development", "Reproduction and Development", "Reproductive strategies and development from gametes to adulthood.", "DEV", topics("Asexual Reproduction;Sexual Reproduction;Gametogenesis;Spermatogenesis;Oogenesis;Menstrual Cycle;Fertilisation;Implantation;Pregnancy;Placenta;Embryonic Development;Foetal Development;Germ Layers;Organogenesis;Birth;Lactation;Growth;Puberty;Human Development;Developmental Genetics;Congenital Abnormalities;Assisted Reproduction;Reproductive Health")),
        DomainSeed("microbiology", "Microbiology", "Microbial diversity, structure, growth, ecology and disease.", "MICRO", topics("Introduction to Microbiology;Bacteria;Archaea;Viruses;Viroids;Prions;Fungi;Protozoa;Algae;Bacterial Structure;Bacterial Growth;Bacterial Genetics;Microbial Metabolism;Microbial Culture;Staining Methods;Sterilisation;Disinfection;Antimicrobial Agents;Antibiotic Resistance;Pathogenicity;Virulence;Biofilms;Normal Microbiota;Medical Microbiology;Environmental Microbiology;Food Microbiology;Industrial Microbiology;Microbiome;Virology;Mycology;Parasitology")),
        DomainSeed("immunology", "Immunology", "Recognition, defence, immune memory and immune dysregulation.", "IMM", topics("Innate Immunity;Adaptive Immunity;Physical Barriers;Immune Cells;Antigens;Antibodies;Complement System;Inflammation;Phagocytosis;Major Histocompatibility Complex;B Cells;T Cells;Antigen Presentation;Humoral Immunity;Cell-mediated Immunity;Primary Immune Response;Secondary Immune Response;Vaccination;Hypersensitivity;Autoimmunity;Immunodeficiency;Transplant Immunology;Tumour Immunology;Immunological Tolerance;Cytokines;Monoclonal Antibodies;Immunotherapy")),
        DomainSeed("evolution", "Evolution", "Processes and evidence explaining biological change through time.", "EVOL", topics("Origin of Life;Evidence for Evolution;Fossils;Comparative Anatomy;Embryological Evidence;Molecular Evidence;Natural Selection;Artificial Selection;Adaptation;Variation;Mutation;Genetic Drift;Gene Flow;Speciation;Reproductive Isolation;Coevolution;Convergent Evolution;Divergent Evolution;Human Evolution;Phylogenetics;Molecular Evolution;Evolutionary Developmental Biology;Evolutionary Medicine")),
        DomainSeed("ecology", "Ecology", "Relationships among organisms, populations, communities and ecosystems.", "ECO", topics("Organism and Environment;Habitat;Niche;Population;Community;Ecosystem;Biome;Biosphere;Food Chains;Food Webs;Ecological Pyramids;Energy Flow;Productivity;Nutrient Cycles;Water Cycle;Carbon Cycle;Nitrogen Cycle;Phosphorus Cycle;Population Growth;Population Regulation;Species Interactions;Competition;Predation;Parasitism;Mutualism;Commensalism;Ecological Succession;Biodiversity;Biogeography;Landscape Ecology;Ecosystem Services;Conservation Ecology")),
        DomainSeed("environmental-biology", "Environmental Biology", "Human impacts, conservation and sustainable management of ecosystems.", "EARTH", topics("Natural Resources;Air Pollution;Water Pollution;Soil Pollution;Noise Pollution;Plastic Pollution;Eutrophication;Biomagnification;Climate Change;Global Warming;Ozone Depletion;Deforestation;Desertification;Habitat Loss;Invasive Species;Endangered Species;Wildlife Conservation;Protected Areas;Sustainable Development;Environmental Impact Assessment;Restoration Ecology;Environmental Toxicology;Waste Management;Renewable Biological Resources")),
        DomainSeed("zoology", "Zoology", "Animal diversity, structure, physiology, behaviour and evolution.", "ANML", topics("Animal Classification;Animal Tissues;Invertebrates;Porifera;Cnidaria;Platyhelminthes;Nematoda;Annelida;Arthropoda;Mollusca;Echinodermata;Chordata;Fish;Amphibians;Reptiles;Birds;Mammals;Comparative Anatomy;Animal Physiology;Animal Behaviour;Animal Reproduction;Developmental Zoology;Entomology;Parasitic Animals;Wildlife Biology")),
        DomainSeed("botany", "Botany", "Plant diversity, taxonomy, structure, uses and applied study.", "PLANT", topics("Algae;Bryophytes;Pteridophytes;Gymnosperms;Angiosperms;Plant Taxonomy;Plant Morphology;Plant Anatomy;Plant Physiology;Plant Embryology;Plant Ecology;Plant Genetics;Plant Breeding;Economic Botany;Ethnobotany;Palynology;Plant Pathology;Forestry;Horticulture")),
        DomainSeed("biotechnology", "Biotechnology", "Use of biological systems in medicine, agriculture and industry.", "LAB", topics("Biotechnology Foundations;Fermentation;Bioreactors;Cell Culture;Tissue Culture;Recombinant DNA Technology;Cloning;PCR;DNA Sequencing;Genetically Modified Organisms;Gene Therapy;CRISPR;Stem-cell Technology;Monoclonal Antibodies;Vaccine Technology;Industrial Biotechnology;Agricultural Biotechnology;Medical Biotechnology;Environmental Biotechnology;Bioinformatics;Synthetic Biology;Nanobiotechnology;Biosensors;Bioprocess Engineering;Regulatory and Ethical Issues")),
        DomainSeed("bioinformatics", "Bioinformatics and Computational Biology", "Computational analysis and visualisation of biological information.", "DATA", topics("Biological Databases;Sequence Databases;Protein Databases;Sequence Alignment;Pairwise Alignment;Multiple Sequence Alignment;BLAST Foundations;Genome Annotation;Phylogenetic Trees;Structural Bioinformatics;Protein Structure Prediction;Molecular Docking Foundations;Systems Biology;Network Biology;Transcriptomic Analysis;Proteomic Analysis;Single-cell Data Foundations;Machine Learning in Biology;Biological Data Visualisation;Computational Genomics")),
        DomainSeed("neuroscience", "Neuroscience", "Cells, circuits and systems supporting behaviour and cognition.", "NEUR", topics("Neurons;Glial Cells;Membrane Potential;Action Potentials;Synapses;Neurotransmitters;Neural Circuits;Brain Regions;Spinal Cord;Sensory Systems;Motor Systems;Autonomic Nervous System;Learning;Memory;Emotion;Sleep;Language;Cognition;Neurodevelopment;Neuroplasticity;Neurodegeneration;Neurological Disorders;Neuropharmacology;Computational Neuroscience")),
        DomainSeed("pathology", "Pathology and Disease Biology", "Biological mechanisms of injury, disease, diagnosis and prevention.", "PATH", topics("Cellular Injury;Inflammation;Healing;Infection;Immune Disorders;Genetic Disease;Metabolic Disease;Cardiovascular Disease;Respiratory Disease;Gastrointestinal Disease;Renal Disease;Endocrine Disease;Neurological Disease;Reproductive Disorders;Cancer Biology;Tumour Development;Metastasis;Epidemiology Foundations;Disease Prevention;Diagnostic Biology;Biomarkers;Precision Medicine")),
        DomainSeed("pharmacology", "Pharmacology Foundations", "Educational foundations of drug action and disposition, not treatment advice.", "DRUG", topics("Drug-receptor Interactions;Pharmacodynamics;Pharmacokinetics;Absorption;Distribution;Metabolism;Excretion;Dose-response Relationships;Therapeutic Index;Receptor Types;Enzyme Targets;Ion-channel Targets;Drug Tolerance;Drug Interactions;Adverse Effects;Antimicrobial Drugs;Cardiovascular Drugs;Nervous-system Drugs;Endocrine Drugs;Cancer Pharmacology;Pharmacogenomics")),
        DomainSeed("research-methods", "Research Methods and Advanced Biology", "Design, evidence, ethics, analysis and advanced laboratory methods.", "R&D", topics("Research Question Development;Hypothesis Formulation;Experimental Design;Controls;Variables;Sampling;Replication;Bias;Statistical Significance;Data Interpretation;Scientific Writing;Literature Review;Peer Review;Laboratory Notebooks;Research Ethics;Human-subject Research;Animal Research Ethics;Biosafety;Reproducibility;Omics Research;Advanced Microscopy;Flow Cytometry;Chromatography;Spectrophotometry;Electrophoresis;Cell Sorting;Mass Spectrometry Foundations;Postgraduate Research Skills")),
    )

    val catalogue: BiologyCatalogue by lazy { buildCatalogue() }

    private fun buildCatalogue(): BiologyCatalogue {
        val units = mutableListOf<BiologyUnit>(); val chapters = mutableListOf<BiologyChapter>(); val topicModels = mutableListOf<BiologyTopic>()
        seeds.forEach { seed ->
            seed.topics.chunked(18).forEachIndexed { unitIndex, unitTopics ->
                val unitId = "${seed.id}-unit-${unitIndex + 1}"
                val chapterIds = unitTopics.chunked(6).indices.map { "$unitId-chapter-${it + 1}" }
                units += BiologyUnit(unitId, seed.id, "${seed.title} · Unit ${unitIndex + 1}", "A guided sequence from ${unitTopics.first()} through ${unitTopics.last()}.", chapterIds, inferLevel(unitTopics.first()), unitTopics.size * 12, emptyList(), unitTopics.flatMap(::keywords).toSet())
                unitTopics.chunked(6).forEachIndexed { chapterIndex, chapterTopics ->
                    val chapterId = "$unitId-chapter-${chapterIndex + 1}"
                    val topicIds = chapterTopics.map { "${seed.id}-${slug(it)}" }
                    chapters += BiologyChapter(chapterId, unitId, chapterTopics.first() + " and related concepts", "Six or fewer connected topics presented as a manageable chapter.", topicIds, inferLevel(chapterTopics.first()), chapterTopics.size * 12, emptyList(), chapterTopics.flatMap(::keywords).toSet())
                    chapterTopics.forEach { title ->
                        val topicId = "${seed.id}-${slug(title)}"; val conceptId = "concept-$topicId"; val futureId = futureType(title)?.let { "future3d-$topicId" }
                        topicModels += BiologyTopic(topicId, chapterId, title, "A structured overview of $title in ${seed.title}.", listOf(conceptId), inferLevel(title), 12, emptyList(), keywords(title).toSet() + keywords(seed.title), futureId)
                    }
                }
            }
        }
        val topicByDomain = topicModels.groupBy { topic -> chapters.first { topic.chapterId == it.id }.let { chapter -> units.first { chapter.unitId == it.id }.domainId } }
        val completeTitles = setOf("Introduction to Biology", "Characteristics of Living Organisms", "Levels of Biological Organisation", "Cell Theory", "Plant Cell", "Animal Cell", "Diffusion", "Osmosis", "Mitosis", "Meiosis", "DNA Structure", "Human Heart", "Photosynthesis", "Bacteria", "Natural Selection", "Ecosystem", "Biotechnology Foundations", "Experimental Design")
        val diagrams = topicModels.mapNotNull { topic -> if (topic.future3DObjectId != null || topic.title in completeTitles) diagramFor(topic) else null }
        val diagramIds = diagrams.map { it.id }.toSet()
        val concepts = topicModels.map { topic ->
            val domainId = chapters.first { it.id == topic.chapterId }.let { chapter -> units.first { it.id == chapter.unitId }.domainId }
            val siblings = topicByDomain.getValue(domainId); val index = siblings.indexOfFirst { it.id == topic.id }
            val related = listOfNotNull(siblings.getOrNull(index - 1), siblings.getOrNull(index + 1)).map { "concept-${it.id}" }
            val status = if (topic.title in completeTitles) BiologyContentStatus.Complete else BiologyContentStatus.OverviewReady
            val diagramId = "diagram-${topic.id}".takeIf { it in diagramIds }
            BiologyConcept(
                id = topic.conceptIds.single(), topicId = topic.id, title = topic.title,
                summary = verifiedSummary(topic.title, seeds.first { it.id == domainId }.title), minimumLevel = topic.minimumLevel,
                estimatedMinutes = if (status == BiologyContentStatus.Complete) 18 else 7, prerequisites = emptyList(), keywords = topic.keywords,
                status = status, learningObjectives = listOf("Define ${topic.title} accurately.", "Place ${topic.title} within the wider biological system.", "Distinguish the core idea from a common oversimplification."),
                blocks = blocksFor(topic.title, status), plannedSections = listOf("Structure and mechanism", "Evidence and examples", "Comparisons", "Applications", "Advanced and research context"),
                relatedConceptIds = related, diagramIds = listOfNotNull(diagramId), future3DObjectId = topic.future3DObjectId,
                quizQuestionIds = listOf("quiz-${topic.id}"),
            )
        }
        val future3D = concepts.mapNotNull { concept ->
            val objectId = concept.future3DObjectId ?: return@mapNotNull null; val fallback = concept.diagramIds.firstOrNull() ?: return@mapNotNull null
            Future3DObjectMetadata(objectId, concept.id, futureType(concept.title)!!, Biology3DAssetStatus.Planned, "educational-default", listOf("overview", "major structures"), listOf(concept.title), listOf("Rotate", "Zoom", "Isolate part", "Show labels", "Cross-section"), fallback)
        }
        val quizzes = concepts.map { concept ->
            val domainTitle = seeds.first { seed -> concept.id.startsWith("concept-${seed.id}-") }.title
            val options = listOf(domainTitle) + seeds.map { it.title }.filterNot { it == domainTitle }.take(3)
            BiologyQuizQuestion(concept.quizQuestionIds.single(), concept.id, BiologyQuestionType.MultipleChoice, "Which Biology domain contains ${concept.title}?", options, setOf(0), "${concept.title} is catalogued in $domainTitle.", "Use the breadcrumb on the concept page.", concept.minimumLevel)
        }
        val domains = seeds.map { seed ->
            val domainUnits = units.filter { it.domainId == seed.id }
            BiologyDomain(seed.id, seed.title, seed.description, domainUnits.map { it.id }, BiologyLearningLevel.FOUNDATION, domainUnits.sumOf { it.estimatedMinutes }, emptyList(), seed.topics.flatMap(::keywords).toSet(), seed.icon)
        }
        return BiologyCatalogue(SCHEMA_VERSION, domains, units, chapters, topicModels, concepts, diagrams, glossary(concepts), quizzes, future3D)
    }

    private fun blocksFor(title: String, status: BiologyContentStatus): List<BiologyContentBlock> = buildList {
        add(BiologyContentBlock.Definition(title, "$title is studied as a defined biological structure, process or evidence-based concept.", BiologyLearningLevel.FOUNDATION))
        add(BiologyContentBlock.Paragraph("At school level, focus on what it is, where it occurs and its main biological role.", BiologyLearningLevel.CLASS_7))
        add(BiologyContentBlock.KeyFact("Biological models simplify reality; their limits should be stated when mechanisms are discussed.", BiologyLearningLevel.CLASS_11))
        if (status == BiologyContentStatus.Complete) {
            add(BiologyContentBlock.BulletGroup(listOf("Identify the components or stages.", "Connect structure with function.", "Use evidence to explain the outcome."), BiologyLearningLevel.CLASS_9))
            add(BiologyContentBlock.Misconception("A diagram is a literal picture at true scale.", "Most teaching diagrams are explanatory models and may not be to scale.", BiologyLearningLevel.CLASS_10))
            add(BiologyContentBlock.ResearchInsight("Current research tests mechanisms using controlled observations, quantitative measurement and reproducible analysis.", BiologyLearningLevel.POSTGRADUATE))
        }
    }

    private fun verifiedSummary(title: String, domain: String): String = "$title is a core concept within $domain. This page introduces its accepted biological meaning, context and learning pathway, with deeper mechanisms progressively disclosed by learning level."
    private fun diagramFor(topic: BiologyTopic) = BiologyDiagram("diagram-${topic.id}", topic.title, "A scientifically labelled 2D framework for ${topic.title}.", listOf(DiagramLabel("main", "Primary structure", "Identifies the principal structure or process region.", .5f, .5f)), "${topic.title} diagram. Primary structure is centred; detailed labels will be expanded in the diagram phase.")

    private fun glossary(concepts: List<BiologyConcept>): List<GlossaryTerm> {
        val requested = listOf("ATP", "Allele", "Antibody", "Apoptosis", "Cell", "Chromosome", "Ecosystem", "Enzyme", "Gene", "Homeostasis", "Mitosis", "Osmosis", "Protein", "Tissue")
        return requested.map { term ->
            val concept = concepts.firstOrNull { it.title.equals(term, true) || it.keywords.any { keyword -> keyword.equals(term, true) } }
            val domainId = concept?.let { item -> seeds.firstOrNull { item.id.startsWith("concept-${it.id}-") }?.id } ?: "foundations"
            GlossaryTerm("glossary-${slug(term)}", term, "$term is an important Biology term whose meaning depends on biological context.", "Advanced study connects $term to mechanisms, evidence and system-level consequences.", domainId, concept?.id, emptyList())
        }
    }

    private fun futureType(title: String): Biology3DObjectType? = when {
        title.contains("cell", true) -> Biology3DObjectType.CELL
        title in setOf("Mitochondria", "Chloroplasts", "Nucleus", "Ribosomes") -> Biology3DObjectType.ORGANELLE
        title.contains("DNA", true) || title.contains("RNA", true) -> Biology3DObjectType.DNA
        title in setOf("Human Heart", "Brain", "Kidneys", "Liver", "Pancreas", "Gallbladder") -> Biology3DObjectType.ORGAN
        title.contains("System", true) || title in setOf("Blood Circulation", "Digestive System") -> Biology3DObjectType.ORGAN_SYSTEM
        title in setOf("Bacteria", "Viruses", "Archaea", "Fungi", "Protozoa") -> Biology3DObjectType.MICROORGANISM
        title in setOf("Roots", "Stems", "Leaves", "Flowers", "Seeds") -> Biology3DObjectType.PLANT_STRUCTURE
        title in setOf("Proteins", "Enzymes", "ATP", "Carbohydrates", "Lipids") -> Biology3DObjectType.MOLECULE
        title in setOf("Ecosystem", "Food Chains", "Food Webs") -> Biology3DObjectType.ECOSYSTEM
        else -> null
    }

    private fun inferLevel(title: String): BiologyLearningLevel = when {
        listOf("omics", "bioinformatics", "pharmacokinetics", "electrophysiology", "mass spectrometry", "flow cytometry", "imprinting", "quantitative", "systems biology").any { title.contains(it, true) } -> BiologyLearningLevel.UNDERGRADUATE
        listOf("cell", "habitat", "food", "living", "plant", "animal", "water", "tissue").any { title.contains(it, true) } -> BiologyLearningLevel.FOUNDATION
        else -> BiologyLearningLevel.CLASS_11
    }

    private fun keywords(title: String): List<String> = title.lowercase().split(Regex("[^a-z0-9]+"), limit = 0).filter { it.length > 2 }
    private fun slug(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
