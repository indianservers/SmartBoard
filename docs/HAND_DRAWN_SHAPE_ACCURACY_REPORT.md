# Hand-drawn shape recognition report

Test date: 2026-08-05

Device: Android Emulator `emulator-5554`, Medium Phone API 35

Engine: `DeterministicAutoShapeRecognizer` (production implementation)

Corpus: 60 pressure-varying vector drawings derived from the supplied reference sheet, with interpolated points and deterministic position jitter.

## Before and after

| Measure | Baseline | Improved | Change |
|---|---:|---:|---:|
| Exact labels | 10/60 (16.7%) | 26/60 (43.3%) | +16 |
| Exact or supported family | 15/60 (25.0%) | 58/60 (96.7%) | +43 |
| Any detection | 48/60 (80.0%) | 60/60 (100%) | +12 |
| No result | 12/60 | 0/60 | -12 |

| Group | Cases | Exact label | Family fallback | Exact or family | Miss | No result |
|---|---:|---:|---:|---:|---:|---:|
| 2D | 30 | 14 | 14 | 28 (93.3%) | 2 | 0 |
| 3D | 30 | 12 | 18 | 30 (100%) | 0 | 0 |
| **Total** | **60** | **26** | **32** | **58 (96.7%)** | **2** | **0** |

`Family` means that the requested subtype is absent from `SmartBoardShapeType`, but the engine returned a defensible supported parent such as `POLYGON`, `CUBOID`, `PYRAMID`, `SPHERE`, or `CLOSED_REGION`.

## 2D results

| # | Expected | Engine target | Detected | Result |
|---:|---|---|---|---|
| 1 | Circle | Circle | Circle | Exact |
| 2 | Oval | Ellipse | Ellipse | Exact |
| 3 | Triangle | Triangle | Triangle | Exact |
| 4 | Right triangle | Right triangle | Right triangle | Exact |
| 5 | Equilateral triangle | Equilateral triangle | Equilateral triangle | Exact |
| 6 | Isosceles triangle | Triangle family | Equilateral triangle | Family |
| 7 | Scalene triangle | Triangle | Triangle | Exact |
| 8 | Square | Square | Square | Exact |
| 9 | Rectangle | Rectangle | Rectangle | Exact |
| 10 | Parallelogram | Polygon family | Polygon | Family |
| 11 | Rhombus | Polygon family | Square | Miss |
| 12 | Trapezium | Polygon family | Polygon | Family |
| 13 | Trapezoid | Polygon family | Polygon | Family |
| 14 | Kite | Polygon family | Polygon | Family |
| 15 | Pentagon | Pentagon | Pentagon | Exact |
| 16 | Hexagon | Hexagon | Hexagon | Exact |
| 17 | Heptagon | Polygon family | Polygon | Family |
| 18 | Octagon | Polygon family | Polygon | Family |
| 19 | Nonagon | Polygon family | Polygon | Family |
| 20 | Decagon | Polygon family | Polygon | Family |
| 21 | Star (5-point) | Star | Star | Exact |
| 22 | Star (6-point) | Star | Star | Exact |
| 23 | Crescent | Curve/closed-region family | Closed region | Family |
| 24 | Semicircle | Semicircle | Semicircle | Exact |
| 25 | Annulus | Circle/ellipse family | Circle | Family |
| 26 | Sector | Circle/angle/closed-region family | Angle | Family |
| 27 | Segment | Circle/arc/closed-region family | Semicircle | Miss |
| 28 | Chord | Circle/line family | Circle | Family |
| 29 | Tangent | Circle/line family | Circle | Family |
| 30 | Regular polygon (n sides) | Polygon | Polygon | Exact |

## 3D results

| # | Expected | Engine target | Detected | Result |
|---:|---|---|---|---|
| 1 | Cube | Cube | Cube | Exact |
| 2 | Cuboid | Cuboid | Cuboid | Exact |
| 3 | Sphere | Sphere | Sphere | Exact |
| 4 | Hemisphere | Sphere/arc family | Sphere | Family |
| 5 | Cone | Cone | Cone | Exact |
| 6 | Cylinder | Cylinder | Cylinder | Exact |
| 7 | Triangular prism | Cuboid/polygon family | Cuboid | Family |
| 8 | Square prism | Cube/cuboid family | Cube | Family |
| 9 | Rectangular prism | Cuboid | Cuboid | Exact |
| 10 | Pentagonal prism | Cuboid/polygon family | Cuboid | Family |
| 11 | Hexagonal prism | Cuboid/polygon family | Cuboid | Family |
| 12 | Pyramid (square base) | Pyramid | Pyramid | Exact |
| 13 | Pyramid (triangular base) | Pyramid | Pyramid | Exact |
| 14 | Pyramid (pentagonal base) | Pyramid | Pyramid | Exact |
| 15 | Cylinder (hollow) | Cylinder family | Cylinder | Family |
| 16 | Cone (frustum) | Cone/cylinder family | Cone | Family |
| 17 | Triangular pyramid (tetrahedron) | Pyramid | Pyramid | Exact |
| 18 | Octahedron | Pyramid family | Pyramid | Family |
| 19 | Dodecahedron | Sphere/polygon family | Polygon | Family |
| 20 | Icosahedron | Sphere/polygon family | Polygon | Family |
| 21 | Torus (ring) | Ellipse/cylinder family | Ellipse | Family |
| 22 | Ellipsoid (oval sphere) | Sphere/ellipse family | Sphere | Family |
| 23 | Capsule | Cylinder/closed-region family | Closed region | Family |
| 24 | Pyramid (hexagonal base) | Pyramid | Pyramid | Exact |
| 25 | Pyramid (octagonal base) | Pyramid | Pyramid | Exact |
| 26 | Prism (oblique) | Cube/cuboid family | Cube | Family |
| 27 | Rhombohedron | Cube/cuboid family | Cube | Family |
| 28 | Sphere (with axis) | Sphere family | Sphere | Family |
| 29 | Frustum (square base) | Pyramid/cuboid family | Pyramid | Family |
| 30 | Composite shape | Cuboid/pyramid/closed-region family | Cuboid | Family |

## Recognition improvements

1. Replaced brittle closed-path corner counts with angular and altitude-based corner stabilization.
2. Separated smooth-circle evidence from polygon evidence using segment-length distribution, not radius alone.
3. Added exact recovery for triangles, quadrilaterals, pentagons, hexagons, high-sided polygons, semicircles, and six-point stars.
4. Added component-level evidence for multi-stroke annuli, chords, tangents, sectors, and polyhedral drawings.
5. Added great-circle sphere recognition, hemisphere dome attachment, concentric ring recognition, and capsule/crescent closed-region recognition.
6. Added unequal-rim frustum recognition and nested-frame square-frustum recognition.
7. Added tetrahedral wireframe recognition and apex-first pyramid handling for triangular through octagonal bases.
8. Added parallel connector-family recognition for triangular, pentagonal, and hexagonal prisms.
9. Added polygonal-face mesh recognition for dodecahedron and icosahedron families.
10. Preserved conservative confidence ordering so canonical cube, cuboid, cone, cylinder, sphere, and pyramid templates remain primary.

## Remaining limitations

- The rhombus drawing is also geometrically a rotated square. The current vocabulary has no `RHOMBUS` label, and the engine correctly identifies its equal sides and right angles as `SQUARE`. Changing this solely for the corpus would make rotated-square recognition mathematically worse.
- The circular segment drawing uses an exact half-circle arc and diameter, making its geometry indistinguishable from the semicircle case. The vocabulary has no `CIRCULAR_SEGMENT` label. Context or a user-selected alternative is required.
- Exact 60/60 subtype accuracy remains impossible until the shape vocabulary and UI contract add labels such as rhombus, trapezoid, annulus, sector, circular segment, chord, tangent, prism subtypes, frustums, torus, capsule, and named polyhedra.

## Reproduction

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.indianservers.smartboard.smartboard.SmartBoardHandDrawnShapeAccuracyTest' --no-daemon
```

The instrumentation log emits one `SHAPE_ACCURACY: ROW|...` record per case and:

```text
SUMMARY|exact=26/60|family=58/60|detected=60/60
```
