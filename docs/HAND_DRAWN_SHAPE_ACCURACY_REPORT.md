# Hand-drawn shape recognition report

Test date: 2026-08-05
Device: Android Emulator `emulator-5554`, Medium Phone API 35
Engine: `DeterministicAutoShapeRecognizer` (production implementation)
Corpus: 60 pressure-varying vector drawings derived from the supplied reference sheet, with interpolated points and deterministic position jitter to avoid testing perfect geometry.

## Summary

| Group | Cases | Exact label | Acceptable family only | Exact or family | No result |
|---|---:|---:|---:|---:|---:|
| 2D | 30 | 3 (10.0%) | 0 | 3 (10.0%) | 5 |
| 3D | 30 | 7 (23.3%) | 5 (16.7%) | 12 (40.0%) | 7 |
| **Total** | **60** | **10 (16.7%)** | **5 (8.3%)** | **15 (25.0%)** | **12** |

An output of some kind was returned for 48/60 cases, but that is **not** accuracy: 33 of those detections were the wrong shape. The exact-label accuracy is 10/60 (16.7%). When an unavailable subtype is allowed to fall back to a defensible supported family, the result is 15/60 (25.0%).

`Unsupported → family` means the current output enum has no exact label for the requested subtype. Such a row can receive only a family-level pass.

## 2D results

| # | Expected | Engine target | Detected | Result |
|---:|---|---|---|---|
| 1 | Circle | Circle | Circle | Exact |
| 2 | Oval | Ellipse | Pentagon | Miss |
| 3 | Triangle | Triangle | Pentagon | Miss |
| 4 | Right triangle | Right triangle | Pentagon | Miss |
| 5 | Equilateral triangle | Equilateral triangle | Polygon | Miss |
| 6 | Isosceles triangle | Unsupported → triangle family | Polygon | Miss |
| 7 | Scalene triangle | Triangle | Polygon | Miss |
| 8 | Square | Square | Hexagon | Miss |
| 9 | Rectangle | Rectangle | Pentagon | Miss |
| 10 | Parallelogram | Unsupported → polygon | Pentagon | Miss |
| 11 | Rhombus | Unsupported → polygon | Pentagon | Miss |
| 12 | Trapezium | Unsupported → polygon | Pentagon | Miss |
| 13 | Trapezoid | Unsupported → polygon | Pentagon | Miss |
| 14 | Kite | Unsupported → polygon | Hexagon | Miss |
| 15 | Pentagon | Pentagon | Circle | Miss |
| 16 | Hexagon | Hexagon | Hexagon | Exact |
| 17 | Heptagon | Unsupported → polygon | Circle | Miss |
| 18 | Octagon | Unsupported → polygon | Circle | Miss |
| 19 | Nonagon | Unsupported → polygon | Circle | Miss |
| 20 | Decagon | Unsupported → polygon | Hexagon | Miss |
| 21 | Star (5-point) | Star | Star | Exact |
| 22 | Star (6-point) | Star | None | No result |
| 23 | Crescent | Unsupported → curve/closed region | Ellipse | Miss |
| 24 | Semicircle | Semicircle | Pentagon | Miss |
| 25 | Annulus | Unsupported → circle/ellipse family | None | No result |
| 26 | Sector | Unsupported → circle/angle/closed region | None | No result |
| 27 | Segment | Unsupported → circle/arc/closed region | Pentagon | Miss |
| 28 | Chord | Unsupported → circle/line segment | None | No result |
| 29 | Tangent | Unsupported → circle/line | None | No result |
| 30 | Regular polygon (n sides) | Polygon | Circle | Miss |

## 3D results

| # | Expected | Engine target | Detected | Result |
|---:|---|---|---|---|
| 1 | Cube | Cube | Cube | Exact |
| 2 | Cuboid | Cuboid | Cuboid | Exact |
| 3 | Sphere | Sphere | None | No result |
| 4 | Hemisphere | Unsupported → sphere/arc | None | No result |
| 5 | Cone | Cone | Cone | Exact |
| 6 | Cylinder | Cylinder | Cylinder | Exact |
| 7 | Triangular prism | Unsupported → cuboid/cube/polygon | Pyramid | Miss |
| 8 | Square prism | Unsupported → cuboid/cube | Cube | Family |
| 9 | Rectangular prism | Cuboid | Cuboid | Exact |
| 10 | Pentagonal prism | Unsupported → cuboid/polygon | Cube | Miss |
| 11 | Hexagonal prism | Unsupported → cuboid/polygon | Cube | Miss |
| 12 | Pyramid (square base) | Pyramid | Pyramid | Exact |
| 13 | Pyramid (triangular base) | Pyramid | Arrow | Miss |
| 14 | Pyramid (pentagonal base) | Pyramid | Pyramid | Exact |
| 15 | Cylinder (hollow) | Unsupported → cylinder | Cylinder | Family |
| 16 | Cone (frustum) | Unsupported → cone/cylinder | None | No result |
| 17 | Triangular pyramid (tetrahedron) | Pyramid | Arrow | Miss |
| 18 | Octahedron | Unsupported → pyramid | Pyramid | Family |
| 19 | Dodecahedron | Unsupported → sphere/polygon | None | No result |
| 20 | Icosahedron | Unsupported → sphere/polygon | Parallel lines | Miss |
| 21 | Torus (ring) | Unsupported → ellipse/cylinder | None | No result |
| 22 | Ellipsoid (oval sphere) | Unsupported → sphere/ellipse | None | No result |
| 23 | Capsule | Unsupported → cylinder/closed region | Pentagon | Miss |
| 24 | Pyramid (hexagonal base) | Pyramid | Cube | Miss |
| 25 | Pyramid (octagonal base) | Pyramid | Cube | Miss |
| 26 | Prism (oblique) | Unsupported → cuboid/cube | Cube | Family |
| 27 | Rhombohedron | Unsupported → cuboid/cube | Cube | Family |
| 28 | Sphere (with axis) | Unsupported → sphere | None | No result |
| 29 | Frustum (square base) | Unsupported → pyramid/cuboid | Cube | Miss |
| 30 | Composite shape | Unsupported → cuboid/pyramid/closed region | Cube | Miss |

## Main findings

1. Closed-stroke simplification is over-segmenting basic shapes: several triangles and quadrilaterals become pentagons or hexagons.
2. Smooth high-sided polygons are frequently classified as circles; the recognizer needs corner evidence that is stable under human stroke jitter.
3. Multi-stroke circular constructions often return no result because the current multi-stroke path prioritizes a small set of 3D templates.
4. The supported 3D templates work for canonical cube, cuboid, cone, cylinder, and some pyramids, but base-shape variants are not modeled.
5. The shape type vocabulary itself lacks many requested semantic labels, so exact 60/60 classification is impossible without extending the model and UI contract.

## Reproduction

Run only this corpus on the connected emulator:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.indianservers.smartboard.smartboard.SmartBoardHandDrawnShapeAccuracyTest' --no-daemon
```

The instrumentation log emits one `SHAPE_ACCURACY: ROW|...` record per case plus the aggregate summary.
