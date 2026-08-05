![Banner](https://cdn.modrinth.com/data/cached_images/ecc9576506051d99a826d7288e0a42c117b65b8a.png)

---

_**A world generation mod built around *geography* instead of randomness.**_

Latitude reorganizes Minecraft's biomes by latitude — jungles near the equator, tundra at the poles, and everything in between laid out the way it would be on a real planet. The world has a center (the equator) and edges (the poles), and the climate shifts as you travel between them.

Latitude doesn't add biomes of its own. It takes the biomes you already have — vanilla, **plus packs like Biomes O' Plenty, Terralith, and Promenade** — and places them where they'd geographically belong. The result is a world that feels like it has real geography: coherent regions, believable transitions, and a reason to point your compass somewhere.

---

⚠️ *Latitude overhauls world generation and takes effect in **new worlds**. Existing Latitude worlds keep the generation they were created with.*

---

## 📦 Which version do I get?

**Always download the file that matches your Minecraft version.**

| Minecraft | Latitude | What you get |
|---|---|---|
| **26.2** | **1.5.0** — the final polish release of the 1.x line | Everything on this page |
| 26.1.x / 1.21.11 / 1.21.1 / 1.20.1 | 1.3.0 | Core latitude worldgen + compass & HUD |

---

## 🗺️ Climate Zones

Five climate bands radiate outward from the equator at the center of the world toward the poles at the edges:

| Zone | Character | Typical Biomes |
|---|---|---|
| 🌴 **Tropical** | Hot, humid, lush | Jungle, Bamboo Jungle, Mangrove Swamp, savanna clearings |
| ☀️ **Subtropical** | Warm and varied, drier toward the edges | Savanna, Desert, Badlands, Plains |
| 🌿 **Temperate** | Mild, forested, familiar | Forest, Birch Forest, Meadow, Swamp |
| 🌨️ **Subpolar** | Cold, stark, increasingly sparse | Taiga, Snowy Plains, Grove |
| ❄️ **Polar** | Frozen and forbidding | Snowy Plains, Ice Spikes, Frozen Ocean |

Zones blend gradually — no hard lines where jungle suddenly becomes tundra. The equator reads as a humid, *varied* rainforest belt; true arid country sits out in the subtropics where it belongs, grading through a natural jungle → savanna → desert transition; and coherent regions replace biome "confetti."

**When you create a world, you choose the climate where your journey begins** — any of the five zones, or Random — from Latitude's own create-world screen, with a live Atlas preview of your world.

---

## ⛰️ Mountains feel like mountains

New in 1.5, on by default: climb high enough and forests thin out into a real **tree line**, ground gives way to **exposed alpine rock**, and the peaks above wear **snow caps whose snowline follows latitude** — lowest near the poles, highest in the dry subtropics, and absent at the equator, just like Earth's. The snowline is noise-warped rather than a flat contour, so caps sit unevenly along a ridge the way real snow does.

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

Latitude is **on by default** in the create-world screen; you can switch back to a vanilla or superflat world right there.

---

## 🥶 The world has edges — and they bite

A planet-shaped map means the frontier is a real place:

- **Push toward the poles** and the cold turns hostile: visibility collapses into a whiteout, frost sets in, and the final stretch will kill the unprepared.
- **Push east or west** and storms build at the edge of the map, with a fair warning before you're in trouble.
- Zone and hemisphere titles announce your crossings, and the loading screen tells you which climate zone you're entering — new worlds and old saves alike.

---

## 🧭 Compass & HUD

Latitude ships a built-in navigation HUD:

- **Compass** — digital (cardinal, 8-way, or degrees) or analog with six color themes
- **Latitude readout** — your current latitude, updating live
- **Biome & zone detail** — where you are and what climate you're in, with its own text scaling
- Everything customizable — size, color, opacity, position

**HUD Studio:** press **F9** in-game (rebindable) to open a live editing screen — drag the title, compass, and location readout wherever you want, snap-to-grid or free. A quick keybind toggles the compass on and off.

For server operators, release builds also include a small `/latitude` command set (`here`, `explainHere`, `probe`, `tpLat`, `tpBand`) for inspecting and navigating the climate map.

---

## 🧩 Compatibility

Latitude works through Minecraft's biome-tag system and **never hard-depends** on other mods — anything not installed is simply ignored. Only **Fabric API** is required. It plays well with terrain-shaping mods like **Tectonic**, **Geophilic**, and **William Wyther's Overhauled Overworld**.

**Custom biome packs are first-class citizens.** Biomes from **Biomes O' Plenty, Terralith, and Promenade** sort themselves into the right climate bands automatically — no setup, no config — with a safety rail that keeps biomes out of climates they don't belong in. In 1.5 the selection was rebuilt to be *provider-fair*: big packs no longer crowd each other out, and every world's mix follows its own seed. Pack authors can add support for other mods via datapack biome tags.

_**Lithosphere** compatibility is in active development._

> ℹ️ **A note on big biome-pack stacks:** each climate band draws from a finite pool, so the more packs you stack, the smaller each biome's share becomes — you won't necessarily see *every* biome from every pack. The mix stays coherent and climate-appropriate; per-pack representation weighting is on the roadmap.

---

## 🔭 What's next — Latitude 2.0 🌏

**Latitude 2.0 is in production.** The larger overhaul being showcased publicly — with its more elaborate mechanics — is a separate line and still in development: expanded biome support, broader worlds, and more earthlike generation shaped by continents, oceans, climate, and exploration. And all the cats you could ever want!!! 🐈‍⬛🐈

**1.5 is the final polish release of the 1.x line**, and the best version of this idea to date.

![A Minecraft screenshot showcasing a sprawling landscape generated by the Latitude mod. A wide, blue river winds through a rolling green and dirt valley, stretching toward steep cliffs with scattered trees under a bright, clear sky. In the immediate foreground, a grassy hillside is unexpectedly populated by over a dozen cats of various breeds (black, tuxedo, and tabby). At the top center of the screen, the mod's custom HUD element displays the location readout: "54°S, 82% Subpolar". The player's hotbar is visible at the bottom of the frame, showing a compass and an item slot.](https://cdn.modrinth.com/data/PBwujSsd/images/f1b2df2468701f17876f61ba61c1648c8f5ffc90.jpeg)

---

# 💛 About & Support

If you've ever thought vanilla biome placement felt random and disconnected — a mushroom island next to a desert next to a taiga — this fixes that. Latitude is great for long-term survival worlds, exploration-focused playthroughs, and modpacks that want geographic coherence without piling on new content.

> 🧭 **A solo passion project — and my first mod.** Thank you for playing with Latitude; I hope you enjoy it as much as I do! :D
>
> 🐛 **Found a bug, or want to follow development?** → **[GitHub](https://github.com/peetsamods/latitude)**
>
> ⭐ **Enjoying Latitude?** Leave a ❤️ on [Modrinth](https://modrinth.com/mod/latitude) — it genuinely helps a solo dev!

---

<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/KC8ZCujnBjo" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

**➢ Follow my mod development: https://www.youtube.com/@peetsamods**

---

## 🌍 A friendly guide to Latitude

Curious about the bigger idea behind the project? [Read the Latitude Field Guide](https://latitude-slabbed-field-guide.joolcode.chatgpt.site/#latitude) for a plain-English look at the world, the current work, and what is coming next.
