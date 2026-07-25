# Atlas Audit Examples

## Artifact Set Found

Typical inputs:

- inventory JSON
- seam rows or seam markers
- band legend
- viewer/export summaries
- atlas-generated report text

## Confetti / Scatter Artifact

- primary classification: confetti / scatter artifact
- likely locus: worldgen logic
- one next slice: inspect the acceptance or selection stage that creates scattered outliers
- do not touch: viewer-only presentation fixes unless the artifact set proves the viewer is wrong

## Suspicious Seam / Abrupt Boundary

- primary classification: suspicious seam / abrupt boundary
- likely locus: worldgen logic or atlas validator drift, depending on whether multiple artifacts agree
- one next slice: inspect the seam-producing stage or the validator contract, but only one of them
- do not touch: broad cleanup, rebuilds, or unrelated biome logic

## Band Leakage

- primary classification: band leakage
- likely locus: worldgen logic
- one next slice: inspect the band assignment or latitude mapping that feeds downstream selection
- do not touch: downstream clamps unless upstream evidence is absent

## Validator Drift

- primary classification: validator drift
- likely locus: atlas validator drift
- one next slice: inspect the validator/report contract against the artifact source of truth
- do not touch: worldgen changes unless the artifact contradiction proves the validator is not the issue

## Viewer-Only / Report-Only Issue

- primary classification: viewer-only/report-only issue
- likely locus: viewer/report-only issue
- one next slice: inspect the export or presentation layer
- do not touch: worldgen logic unless the artifact evidence conflicts

## Clean / No Actionable Issue

- primary classification: clean / no actionable issue
- likely locus: inconclusive
- one next slice: none; report that the artifact set does not show a clear regression
- do not touch: speculative fixes or broad reroutes
