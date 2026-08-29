package com.example.globe.client.create;

/**
 * The "Vanilla" door on the Select World screen: a one-click route straight to vanilla's own
 * create-world screen, skipping Latitude's.
 *
 * <p>Placement is not this class's concern -- it shares {@link VanillaFooterLayoutPolicy}'s
 * narrow-to-fit arithmetic with the escape-hatch footer, narrowing Play, Create, and the door
 * together to share the row's OWN existing width rather than appending extra width beyond it. An
 * earlier version of this door instead appended a fixed-width button past Create's edge and
 * refused to show it when there was no room; reported live as absent at a window that looked
 * perfectly ordinary on screen. The cause: vanilla's GUI Scale setting shrinks the game's own
 * notion of screen width independently of the window's actual on-screen size, and a manually-set
 * high scale (5x, in the report) reaches that "no room" condition at a window most players would
 * never call narrow. Narrowing within the row's own footprint has no such threshold -- there is
 * nothing left to refuse, because nothing beyond what the row already claims is ever requested.</p>
 */
public final class VanillaWorldListDoorPolicy {
    /**
     * Dims the door so it reads as secondary to Create New World before anyone reads the label --
     * unaffected by the narrowing redesign; the door is still a secondary route, even sized to
     * match its row-mates.
     */
    public static final float ALPHA = 0.7f;

    private VanillaWorldListDoorPolicy() {
    }
}
