# Workspace Cleaner Examples

## Tracked Dirty Files

Classify tracked changes conservatively:

- source files: likely source file
- logs and crash reports: runtime junk
- generated reports: generated artifact
- world or save files: world/state data

## Untracked Files

Use `git ls-files --others --exclude-standard` to inventory untracked files.

- if it is clearly a log, crash report, temp output, or generated report: likely safe manual cleanup candidate
- if it is a world file or run artifact: risky items to preserve
- if it is a source-like file or unclear path: unknown: review manually

## Common Clutter Buckets

- `run/`: usually runtime junk or world/state data
- `logs/`: usually runtime junk
- `latest.log`: usually runtime junk
- `crash-reports/`: usually runtime junk
- temp/export/output folders: usually generated artifact
- local world files: world/state data
- generated atlas/report artifacts: generated artifact
- OS-specific junk: usually safe manual cleanup candidate if clearly disposable
- editor temp files: usually safe manual cleanup candidate if clearly disposable

## Risky Items To Preserve

Preserve items that can carry state or hide real work:

- world or save files
- stateful run artifacts
- unknown files that could be source
- generated exports that might be evidence

## Safe Manual Cleanup Candidates

Usually safe to remove manually, but never automatically:

- logs
- crash reports
- obvious temp files
- clearly disposable editor artifacts
- stale generated reports after review

## Recommendation Style

- low noise: a few obvious junk files, no source ambiguity
- medium noise: clutter exists alongside some review-first items
- high noise: lots of runtime clutter, world/state files, or mixed unknowns
