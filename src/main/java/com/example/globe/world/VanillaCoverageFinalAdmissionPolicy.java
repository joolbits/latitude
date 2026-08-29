package com.example.globe.world;

/**
 * Pure compatibility policy for a saved vanilla-coverage reservation at its final column.
 * Descriptor geography is authoritative; an ID's namespace is never admission evidence.
 */
final class VanillaCoverageFinalAdmissionPolicy {
    enum Decision {
        PRESERVE_EXACT,
        DEFER_TO_WARM_PROVINCE,
        DEFER_TO_WETLAND,
        DEFER_TO_PHYSICAL_TERRAIN,
        DEFER_TO_POLAR_COLD,
        REJECT_DESCRIPTOR
    }

    enum LatitudeZone {
        TROPICAL,
        SUBTROPICAL,
        TEMPERATE,
        SUBPOLAR,
        POLAR
    }

    enum PhysicalTerrain {
        LOWLAND,
        UPLAND
    }

    record Facts(
            LatitudeZone authoritativeZone,
            LatitudeZone selectionZone,
            ProvinceAuthority.Province warmProvince,
            PhysicalTerrain physicalTerrain,
            boolean ordinaryWetlandClaim,
            boolean finalWetlandCompatible,
            boolean aridClaim,
            boolean polarColdConflict) {
        Facts {
            if (authoritativeZone == null || selectionZone == null || physicalTerrain == null) {
                throw new IllegalArgumentException("missing final-admission fact");
            }
        }
    }

    private VanillaCoverageFinalAdmissionPolicy() {
    }

    static Decision decide(
            BiomeDescriptorLedger.Descriptor descriptor,
            BiomeRoute savedRoute,
            Facts facts) {
        if (descriptor == null || savedRoute == null || facts == null
                || !descriptor.routes().contains(savedRoute)) {
            return Decision.REJECT_DESCRIPTOR;
        }
        if (facts.polarColdConflict() || !routeOwnsZone(savedRoute, facts.authoritativeZone())
                || !routeOwnsZone(savedRoute, facts.selectionZone())) {
            return Decision.DEFER_TO_POLAR_COLD;
        }

        boolean routeUpland = isUplandRoute(savedRoute);
        boolean physicalUpland = facts.physicalTerrain() == PhysicalTerrain.UPLAND;
        if (physicalUpland != routeUpland) {
            return Decision.DEFER_TO_PHYSICAL_TERRAIN;
        }

        boolean descriptorWetland = descriptor.terrain() == BiomeDescriptorLedger.Terrain.WETLAND
                || descriptor.water() == BiomeDescriptorLedger.Water.WETLAND
                || descriptor.water() == BiomeDescriptorLedger.Water.COASTAL_BRACKISH
                || descriptor.family() == BiomeDescriptorLedger.Family.WETLAND;
        boolean routeWetland = savedRoute == BiomeRoute.TEMPERATE_WETLAND
                || savedRoute == BiomeRoute.SUBPOLAR_WETLAND;
        if (descriptorWetland != routeWetland) {
            return Decision.REJECT_DESCRIPTOR;
        }
        if (routeWetland) {
            if (!facts.finalWetlandCompatible()) {
                return Decision.DEFER_TO_WETLAND;
            }
        } else if (facts.ordinaryWetlandClaim()) {
            return Decision.DEFER_TO_WETLAND;
        }

        if (savedRoute == BiomeRoute.TROPICAL_HUMID_LOWLAND
                && facts.warmProvince() != ProvinceAuthority.Province.WARM_WET) {
            return Decision.DEFER_TO_WARM_PROVINCE;
        }
        if (savedRoute == BiomeRoute.SUBTROPICAL_HUMID_LOWLAND
                && facts.warmProvince() == ProvinceAuthority.Province.WARM_DRY) {
            return Decision.DEFER_TO_WARM_PROVINCE;
        }
        if ((savedRoute == BiomeRoute.WARM_TRANSITION || savedRoute == BiomeRoute.WARM_UPLAND)
                && facts.warmProvince() == ProvinceAuthority.Province.WARM_WET) {
            return Decision.DEFER_TO_WARM_PROVINCE;
        }
        if ((savedRoute == BiomeRoute.ARID_LOWLAND || savedRoute == BiomeRoute.ARID_UPLAND)
                && !facts.aridClaim()) {
            return Decision.DEFER_TO_WARM_PROVINCE;
        }
        return Decision.PRESERVE_EXACT;
    }

    /**
     * The cold-upland exact-preservation escape, resolved against the descriptor's OWN upland route.
     *
     * <p>This asked for COLD_UPLAND unconditionally until the windswept family moved to
     * SUBPOLAR_UPLAND. {@link #decide} rejects on its first line when the descriptor does not carry
     * the route it is handed, so after that move this could never preserve the one family its
     * callers act on: a reserved windswept column whose physical terrain reads UPLAND but whose raw
     * mountain sample is false would lose its guaranteed identity to the band fallback -- the
     * reservation eroded on exactly the high ground it exists to describe. The two inputs really can
     * disagree, because the caller's mountain flag is the raw sample while the facts carry
     * raw-or-measured upland.
     */
    static Decision decideExactColdUplandPreservation(
            BiomeDescriptorLedger.Descriptor descriptor,
            Facts facts) {
        if (descriptor == null) {
            return Decision.REJECT_DESCRIPTOR;
        }
        BiomeRoute upland = descriptor.routes().contains(BiomeRoute.SUBPOLAR_UPLAND)
                ? BiomeRoute.SUBPOLAR_UPLAND
                : BiomeRoute.COLD_UPLAND;
        return decide(descriptor, upland, facts);
    }

    static boolean mayPreserveCustomInWarmProvince(
            BiomeDescriptorLedger.Descriptor descriptor,
            ProvinceAuthority.Province province) {
        if (descriptor == null || province == null) return false;
        return switch (province) {
            case WARM_WET -> descriptor.family() == BiomeDescriptorLedger.Family.JUNGLE
                    || descriptor.family() == BiomeDescriptorLedger.Family.FOREST
                    || descriptor.family() == BiomeDescriptorLedger.Family.WETLAND;
            case WARM_MEDIUM -> descriptor.family() != BiomeDescriptorLedger.Family.JUNGLE
                    && descriptor.family() != BiomeDescriptorLedger.Family.WETLAND
                    && descriptor.family() != BiomeDescriptorLedger.Family.POLAR
                    && descriptor.family() != BiomeDescriptorLedger.Family.TAIGA;
            case WARM_DRY -> descriptor.family() == BiomeDescriptorLedger.Family.ARID
                    || descriptor.family() == BiomeDescriptorLedger.Family.SAVANNA;
            default -> false;
        };
    }

    /**
     * Every route whose descriptors are UPLAND terrain, including SUBPOLAR_UPLAND (2026-08-18).
     *
     * <p>The V5 admission compares this against the column's measured physical terrain and defers
     * whenever the two disagree. Omitting the windswept family's new route would make every one of
     * its saved identities read as a LOWLAND route standing on an UPLAND column, so
     * {@code DEFER_TO_PHYSICAL_TERRAIN} would fire on exactly the mountains the route exists to
     * describe — the guaranteed windswept province would lose its own admission.
     */
    private static boolean isUplandRoute(BiomeRoute route) {
        return route == BiomeRoute.TEMPERATE_UPLAND
                || route == BiomeRoute.SUBPOLAR_UPLAND
                || route == BiomeRoute.COLD_UPLAND
                || route == BiomeRoute.WARM_UPLAND
                || route == BiomeRoute.ARID_UPLAND;
    }

    private static boolean routeOwnsZone(BiomeRoute route, LatitudeZone zone) {
        return switch (route) {
            case TROPICAL_HUMID_LOWLAND -> zone == LatitudeZone.TROPICAL;
            case SUBTROPICAL_HUMID_LOWLAND, WARM_TRANSITION, WARM_UPLAND,
                    ARID_LOWLAND, ARID_UPLAND -> zone == LatitudeZone.SUBTROPICAL;
            case TEMPERATE_LOWLAND, TEMPERATE_WETLAND, TEMPERATE_UPLAND ->
                    zone == LatitudeZone.TEMPERATE;
            // SUBPOLAR_UPLAND is subpolar and ONLY subpolar (2026-08-18) — unlike COLD_UPLAND below,
            // which still spans both cold zones. That difference is the whole point of the split:
            // the windswept family this route carries is banned at the pole, so a saved windswept
            // identity whose column resolves to POLAR must lose its admission here rather than be
            // preserved into the band the re-route removed it from.
            case SUBPOLAR_WETLAND, SUBPOLAR_LOWLAND, SUBPOLAR_UPLAND -> zone == LatitudeZone.SUBPOLAR;
            case POLAR_LOWLAND -> zone == LatitudeZone.POLAR;
            case COLD_UPLAND -> zone == LatitudeZone.SUBPOLAR || zone == LatitudeZone.POLAR;
            case CAVE_SHALLOW, CAVE_DEEP -> false;
        };
    }
}
