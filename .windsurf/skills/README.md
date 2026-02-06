# Latitude — Windsurf Skills Index

Use this index to pick the *right* skill quickly. If multiple apply, prefer the one that prevents guessing and enforces evidence-first debugging.

---

## Core worldgen / biome logic

### latitude-biome-authority
**Use when:** assigning/validating vanilla biome inclusion or latitude eligibility.  
**Guarantees:** no invented biome tables; missing biomes are treated as bugs.

### latitude-generation-order-rules
**Use when:** reasoning about pipeline order (biomes → surface rules → features → guards).  
**Guarantees:** no invented worldgen order; evidence required for reordering.

### latitude-biome-selection-contract
**Use when:** “F3 says warm biome but cold artifacts exist” contradictions.  
**Guarantees:** correct interpretation of base vs out biomes.

### latitude-zone-math-authority
**Use when:** radius/t/zone math, warm-band checks, thresholds, any climate gating.  
**Guarantees:** no hardcoded radius; one source of truth for thresholds.

### latitude-write-path-guards
**Use when:** impossible blocks appear (snow/powder snow in warm bands, etc.).  
**Guarantees:** identify write path first; fix at choke point; no biome reassignment.

---

## Debugging rules

### latitude-debug-instrumentation-rules
**Use when:** adding logs, counters, overlays, sanity pings, stack traces.  
**Guarantees:** gated, throttled, removable instrumentation (OFF by default).

---

## Releases / backports / repo hygiene

### latitude-release-discipline
**Use when:** building, tagging, uploading to Modrinth, multi-target releases.  
**Guarantees:** correct branch/version/jar/tag hygiene; jar contents verified.

### latitude-compat-backport-playbook
**Use when:** porting fixes between 1.21.11 and 1.21.1 (or other 1.21.x).  
**Guarantees:** cherry-pick discipline; minimal proof; version/tag correctness.

### latitude-repo-hygiene
**Use when:** large diffs, extracted sources, tool folders, accidental commits.  
**Guarantees:** forbidden folders never ship; cleanup commands provided.

---

## UI / rendering

### latitude-ui-and-hud-lifecycle
**Use when:** screens, HUD overlays, anything that can open/render in-world.  
**Guarantees:** no auto-open regressions; proper gating & screen rules.

### latitude-rendering-compat-rules
**Use when:** Sodium/Iris rendering issues (storm wall, fog, world-space debug).  
**Guarantees:** shader-safe patterns; no raw GL state calls; debug toggles.

---

## Design guardrails

### latitude-player-facing-invariants
**Use when:** changes that affect the “feel” of Latitude or core promises.  
**Guarantees:** prevents technically-correct but experience-breaking changes.

---

## Quick selection cheatsheet

- **Wrong block appears (snow in tropics):** `latitude-write-path-guards` → then `latitude-zone-math-authority` 
- **F3 vs blocks contradict:** `latitude-biome-selection-contract` 
- **Need to add logs/counters:** `latitude-debug-instrumentation-rules` 
- **Backport fix:** `latitude-compat-backport-playbook` 
- **Tag/build/upload:** `latitude-release-discipline` 
- **Screen opened unexpectedly:** `latitude-ui-and-hud-lifecycle` 
- **Shaders/Sodium rendering weirdness:** `latitude-rendering-compat-rules` 
