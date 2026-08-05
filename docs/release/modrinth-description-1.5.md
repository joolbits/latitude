![Banner](https://cdn.modrinth.com/data/cached_images/ecc9576506051d99a826d7288e0a42c117b65b8a.png)

---

_**Your Minecraft world becomes a planet.**_

Vanilla scatters its biomes at random — a desert beside a taiga beside a mushroom island, with no rhyme and no *where*. Latitude replaces the shuffle with **geography**. Your world gets an equator at its heart, tropics around it, temperate country beyond that, and frozen poles at the far edge of the map. Climate isn't a dice roll anymore; it's a **place on the planet**.

That changes how it feels to play. North actually means something. A long journey passes through climates in the right order — jungle thinning into savanna, savanna drying into desert, forests going birch, then spruce, then snow. When your HUD reads **54°S**, you know exactly how far you are from home, and what the land will look like when you get where you're going.

Latitude doesn't add biomes of its own. It takes the biomes you already have — vanilla, **plus packs like Biomes O' Plenty, Terralith, and Promenade** — and puts them where they'd belong on a real planet.

---

⚠️ *Latitude overhauls world generation and takes effect in **new worlds**. Existing Latitude worlds keep the generation they were created with.*

---

## 🗺️ A world with real geography

Five climate bands radiate outward from the equator at the center of the world to the poles at its edges:

| Zone | Character | Typical Biomes |
|---|---|---|
| 🌴 **Tropical** | Hot, humid, lush | Jungle, Bamboo Jungle, Mangrove Swamp, savanna clearings |
| ☀️ **Subtropical** | Warm and varied, drier toward the edges | Savanna, Desert, Badlands, Plains |
| 🌿 **Temperate** | Mild, forested, familiar | Forest, Birch Forest, Meadow, Swamp |
| 🌨️ **Subpolar** | Cold, stark, increasingly sparse | Taiga, Snowy Plains, Grove |
| ❄️ **Polar** | Frozen and forbidding | Snowy Plains, Ice Spikes, Frozen Ocean |

Zones blend gradually — no hard line where jungle becomes tundra. The equator reads as a humid, *varied* rainforest belt; true arid country sits out in the subtropics where it belongs; and biomes form coherent regions you can name and come back to, not single-block confetti.

---

## ⛰️ Mountains feel like mountains

Climb, and the world behaves the way a planet should. Forests thin out into a real **tree line**. Above it, the ground breaks into **exposed alpine rock**. And the peaks wear **snow caps whose snowline follows latitude** — hugging the ground near the poles, retreating to only the highest summits in the dry subtropics, and vanishing entirely at the equator, just like Earth's. The snowline wanders like real snow does, so no two ridgelines are capped the same way.

---

## 🥶 The world has edges — and they bite

A planet-shaped map makes the frontier a real place, with real stakes:

- **Push toward the poles** and the cold turns against you — visibility collapses into a whiteout, frost creeps in, and the final stretch will kill the unprepared.
- **Push east or west** and storms build at the rim of the map, with fair warning before you're in real trouble.
- Zone and hemisphere titles announce your crossings, and even the loading screen knows where you are — it greets you with the climate zone you're loading into.

Reaching a pole isn't a coordinate. It's an **expedition**.

---

## 🌐 Your planet, your scale

Pick a world size when you create it — this sets how far the equator-to-pole journey is:

| Size | Diameter | Feel |
|---|---|---|
| Itty Bitty | 7,500 × 7,500 | Equator to pole in an afternoon |
| Tiny | 10,000 × 10,000 | Small but varied |
| Small | 15,000 × 15,000 | Compact with room to breathe |
| **Regular** | **20,000 × 20,000** | **Recommended** |
| Large | 30,000 × 30,000 | Long-haul survival |
| Ginormous | 40,000 × 40,000 | Continental-scale trek |

_Everything scales with the size you pick — band widths, border behavior, latitude math. A smaller world doesn't cut off a bigger one; it compresses the whole climate system proportionally._

**And you choose where on the planet your story starts.** Latitude's create-world screen shows a live Atlas of your world — pick any of the five climate zones as your starting latitude, or leave it to fate with Random. Start easy in the temperate belt, or spawn at the frozen rim and earn your way to warmth. Latitude is on by default when you create a world, and you can switch back to vanilla right there.

---

## 🧭 Navigate like an explorer

Latitude ships a built-in navigation HUD, because on a planet, knowing *where you are* matters:

- **Compass** — digital (cardinal, 8-way, or degrees) or analog with six color themes
- **Latitude readout** — your position on the planet, updating live
- **Biome & zone detail** — what the land is, and what climate it belongs to
- Everything customizable — size, color, opacity, position

**HUD Studio:** press **F9** in-game (rebindable) to open a live editing screen — drag the title, compass, and location readout wherever you want, snap-to-grid or free. A quick keybind toggles the compass on and off.

For server operators, release builds include a small `/latitude` command set (`here`, `explainHere`, `probe`, `tpLat`, `tpBand`) for inspecting and navigating the climate map.

---

## 🧩 Plays well with others

Latitude works through Minecraft's biome-tag system and **never hard-depends** on other mods — anything not installed is simply ignored. Only **Fabric API** is required. It pairs nicely with terrain-shaping mods like **Tectonic**, **Geophilic**, and **William Wyther's Overhauled Overworld**.

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

If you've ever thought vanilla biome placement felt random and disconnected — this fixes that. Latitude is built for long-term survival worlds, exploration-focused playthroughs, and modpacks that want a world that feels like a real place without piling on new content.

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
