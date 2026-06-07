![Banner](https://cdn.modrinth.com/data/cached_images/ecc9576506051d99a826d7288e0a42c117b65b8a.png)

---

_**A world generation mod built around *geography* instead of randomness.**_

Latitude reorganizes Minecraft's biomes by latitude — jungles near the equator, tundra at the poles, and everything in between laid out the way it would actually be on a planet. The world has a center (the equator) and edges (the poles), and biomes shift as you travel between them.

Latitude doesn't add biomes of its own. It takes the biomes you already have — vanilla, **and now biomes from packs like Biomes O' Plenty, Terralith, and Promenade** — and places them where they'd geographically belong. You get a world that feels like it has real geography instead of random noise.

---

⚠️ *Latitude overhauls world generation and requires a **new world** to take effect.*

---

## 📦 Which version do I get?

Latitude runs on several Minecraft versions — **always download the file that matches your Minecraft version.**

| Minecraft | Latitude | What you get |
|---|---|---|
| **26.1.2** | **1.4 "Cohesive Horizons"** (latest) | Everything below, including custom biome support and the latest worldgen pass |
| 1.21.11 / 1.21.1 / 1.20.1 | 1.3.0 | Core latitude worldgen + compass/HUD — the 1.4 additions below are **planned to come to these versions** |

_This page has two parts: **① What's new in 1.4** (the latest version, Minecraft 26.1.2), then **② Core features** — the foundation that's in **every** version of Latitude. The 1.4 additions are being brought to the older versions over time._

---

# ① What's new in 1.4 — "Cohesive Horizons" *(Minecraft 26.1.2)*

This release is all about making the map read like a **coherent, Earth-like world** — and opening the climate system up to other biome mods. *(These items are on the latest version; older versions are on 1.3.0 — see the table above.)*

- **🧩 First-class custom biome support.** Biomes from **Biomes O' Plenty, Terralith, and Promenade** now sort themselves into the right climate bands automatically — no setup, no config. A built-in safety rail keeps biomes from landing in a climate they don't belong in, and missing mods are simply skipped (no hard dependencies). *(Advanced users and pack authors can add support for other biome mods via datapack biome tags.)*
- **🌎 A more believable climate map.** The equator now reads as a humid, *varied* rainforest belt — a mix of jungle, bamboo, sparse-jungle and savanna clearings, plus tropical biomes from any installed packs — instead of a desert-choked or single-biome equator. True arid country (desert, badlands) is pushed out to the **subtropics** where it belongs, grading into a natural jungle → savanna → desert transition toward the poleward edge.
- **🏜️ No more badlands at the equator.** Mesa/badlands is a subtropical landform on Earth — it no longer leaks into the deep tropics, and hot desert is thinned right at the equator (replaced by savanna clearings), while the subtropical arid belt is untouched.
- **🧹 Far less biome "confetti."** Secondary biomes form coherent patches instead of single-block speckles sprinkled through the dominant biome — cleaner, more readable regions.

---

# ② Core features — in every version of Latitude

_Everything below is Latitude's always-on foundation — present on every version, including 1.3.0. None of this is new to 1.4; it's what the mod does at its core._

## 🗺️ Climate Zones

Five climate bands radiate outward from the equator at the center of the world toward the poles at the edges:

| Zone | Character | Typical Biomes |
|---|---|---|
| 🌴 **Tropical** | Hot, humid, lush | Jungle, Bamboo Jungle, Mangrove Swamp, savanna clearings |
| ☀️ **Subtropical** | Warm and varied, drier toward the edges | Savanna, Desert, Badlands, Plains |
| 🌿 **Temperate** | Mild, forested, familiar | Forest, Birch Forest, Meadow, Swamp |
| 🌨️ **Subpolar** | Cold, stark, increasingly sparse | Taiga, Snowy Plains, Grove |
| ❄️ **Polar** | Frozen and forbidding | Snowy Plains, Ice Spikes, Frozen Ocean, Snowy Slopes |

Zones blend into each other gradually — no hard lines where jungle suddenly becomes tundra. You'll also get edge warnings as you push toward the poles and the east/west boundaries. Latitude is very much a living project, and biome balance keeps improving release over release.

---

## 🌐 World Sizes

You pick a world size when you create the world. This controls how far the equator-to-pole journey is:

| Size | Diameter | Feel |
|---|---|---|
| Itty Bitty | 7,500 × 7,500 | Equator to pole in an afternoon |
| Tiny | 10,000 × 10,000 | Small but varied |
| Small | 15,000 × 15,000 | Compact with room to breathe |
| **Regular** | **20,000 × 20,000** | **Recommended** |
| Large | 30,000 × 30,000 | Long-haul survival |
| Ginormous | 40,000 × 40,000 | Continental-scale trek |

_Everything scales with the size you pick — biome band widths, border behavior, latitude math. A "Tiny" world doesn't just cut off a "Regular" world; it compresses the whole climate system proportionally._

---

## 🌐 World Creation

Latitude hooks into the world creation screen directly. It's **on by default** — you'll see the size picker and climate toggle right there when you make a new world. You can turn it off if you want a vanilla world.

---

## 🧭 Compass & HUD

Latitude comes with a built-in navigation HUD:

- **Compass** — digital (cardinal, 8-way, or degrees) or analog with color themes
- **Latitude readout** — your current latitude, updating live
- **Climate zone indicator** — shows on zone crossing, or keep it on all the time
- Everything is customizable — size, color, opacity, position, background

### 🛠️ **HUD Studio:**
Press **F9** to open it (rebindable). Drag elements around on screen, snap to grid if you want. Press **`,`** to toggle the compass on/off.

---

## 🧩 Compatibility

Latitude works through Minecraft's biome-tag system, so it **never hard-depends** on other mods — anything not installed is simply ignored. It plays nicely with terrain-shaping mods like **Tectonic**, **Geophilic**, and **William Wyther's Overhauled Overworld**, and only **Fabric API** is required.

Biome packs can be sorted into the climate bands too — the latest version (1.4) adds **automatic support for Biomes O' Plenty, Terralith, and Promenade** (see *① What's new* above), and pack authors can add other mods via datapack tags.

> ℹ️ **A note on big biome-pack stacks:** each climate band draws from a finite pool, so the more biome packs you stack, the smaller each biome's share becomes — with several packs installed at once, you won't necessarily see *every* biome. The mix stays coherent and climate-appropriate; per-pack representation weighting is on the roadmap.

---

# 💛 About & Support

If you've ever thought vanilla biome placement felt random and disconnected — a mushroom island next to a desert next to a taiga — this fixes that. Latitude is great for long-term survival worlds, exploration-focused playthroughs, and modpacks that want geographic coherence without piling on new content.

> 🧭 **A solo passion project — and my first mod.** Thank you for playing with Latitude; I hope you enjoy it as much as I do! :D
>
> 🐛 **Found a bug, or want to follow development?** → **[GitHub](https://github.com/joolbits/latitude)**
>
> ⭐ **Enjoying Latitude?** Leave a ❤️ on **[Modrinth](https://modrinth.com/mod/latitude)** — it genuinely helps a solo dev!
