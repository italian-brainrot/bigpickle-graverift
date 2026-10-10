package com.bigpickle.graverift.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common configuration. Every value has a sensible vanilla balanced default and only exists so that
 * pack authors can tune the mechanic without touching code.
 *
 * <p>The config is a {@code COMMON} config, which means it is loaded on both the logical client and
 * the logical server and is safe to read from gameplay code.</p>
 */
public final class GraveRiftConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue RIFT_LIFETIME_TICKS = BUILDER
            .comment("How long a Grave Rift stays open, in ticks. 1200 ticks = 60 seconds.")
            .defineInRange("riftLifetimeTicks", 1200, 20, 72_000);

    public static final ModConfigSpec.DoubleValue MOB_DAMAGE = BUILDER
            .comment("Damage (in half-hearts) dealt to the creature the Grave Rift is used on. 10 = 5 hearts.")
            .defineInRange("mobDamage", 10.0D, 0.0D, 1_000.0D);

    public static final ModConfigSpec.IntValue HUNGER_AMPLIFIER = BUILDER
            .comment("Amplifier of the Hunger effect applied to the rift owner while the rift is open. 14 = Hunger XV.")
            .defineInRange("ownerHungerAmplifier", 30, 0, 255);

    public static final ModConfigSpec.IntValue RETURN_WEAKNESS_TICKS = BUILDER
            .comment("Duration of the Weakness effect applied when a player is returned by the rift. 12000 ticks = 10 minutes.")
            .defineInRange("returnWeaknessTicks", 12_000, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue REMOVE_EXPERIENCE = BUILDER
            .comment("Whether entering the rift removes all of the player's experience.")
            .define("removeExperience", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private GraveRiftConfig() {
    }
}
