package com.nationeconomy.nation;

import java.util.Locale;
import java.util.Optional;

/**
 * Land permissions that can be granted to players with
 * {@code /nation allow access <player> <permission>}.
 */
public enum NationPermission {
    /** Break blocks inside the claim. */
    BREAK,
    /** Place blocks / fluids / boats inside the claim. */
    PLACE,
    /** Open containers (chests, barrels, hoppers, ...). */
    CHEST,
    /** Use doors, buttons, levers, interact with entities, ... */
    USE,
    /** Every permission at once. */
    ALL;

    /** Parses user input into a permission. */
    public static Optional<NationPermission> parse(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "break", "breakblocks", "mine" -> Optional.of(BREAK);
            case "place", "placeblocks", "build" -> Optional.of(PLACE);
            case "chest", "chests", "container", "containers", "search" -> Optional.of(CHEST);
            case "use", "useitems", "interact" -> Optional.of(USE);
            case "all", "everything" -> Optional.of(ALL);
            default -> Optional.empty();
        };
    }

    public String displayName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
