# Atlas Viewer adapter

`atlas_runner.py` connects this worktree's headless exporter to the shared
Latitude Atlas Viewer. The Viewer discovers the adapter without copying its
application into the worktree.

Select the existing port worktree in the Viewer's generation panel. The adapter
supports sampled Atlas generation; terrain-relief and pregenerated-world-map
capabilities are not advertised by this adapter.

For a direct small integration check:

```sh
python3 tools/atlas/atlas_runner.py generate --size itty --step 512 --seed 1 --no-viewer-open
```

Each job uses a separate disposable server directory and log. Viewer jobs share
an isolated build/cache pair and a generation lock. Empty preview servers keep
ticking, while Minecraft's watchdog remains enabled at its default setting.

Only validated exports are published to Viewer history. The manifest carries
explicit radius and diameter so older Viewer preset tables cannot change the
map's coordinate scale. Run-emitted policy remains partial where the exporter
cannot provide the Viewer's full policy contract; viewing a map does not certify
biome completeness or final generated-world correctness.
