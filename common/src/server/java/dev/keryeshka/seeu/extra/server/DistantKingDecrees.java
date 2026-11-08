package dev.keryeshka.seeu.extra.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bounded off-chunk proclamation logic for four Antarchy player-state decrees.
 *
 * The offloaded King is NOT instantiated here. No pathfinding, entity
 * spawning, terrain access, chunk tickets, damage hits or combat-only decrees.
 * All decisions and punishments run on the server's ordinary tick thread.
 */
final class DistantKingDecrees {
    static final double RANGE_BLOCKS = 32.0D * 16.0D;
    private static final double RANGE_SQUARED = RANGE_BLOCKS * RANGE_BLOCKS;
    private static final int INITIAL_DELAY = 120;
    private static final int ANNOUNCEMENT_TICKS = 40;
    private static final int RESPONSE_GRACE_TICKS = 30;
    private static final int ACTIVE_TICKS = 300;
    private static final int KNEEL_TICKS = 100;
    private static final int REPEAT_COOLDOWN_TICKS = 600;
    private static final double STAND_GROUND_ALLOWED_DRIFT_SQUARED = 6.0D * 6.0D;
    private static final ResourceKey<MobEffect> VITALITY_PENALTY = ResourceKey.create(
            Registries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("antarchy", "royal_vitality_penalty"));

    private final Map<ServerLevel, State> states = new IdentityHashMap<>();

    void tick(ServerLevel level, double kingX, double kingY, double kingZ) {
        long now = level.getGameTime();
        List<ServerPlayer> nearby = new ArrayList<>();
        // A cheap player-only scan. Never searches blocks or chunks.
        for (ServerPlayer player : level.players()) {
            if (targetable(player) && horizontalDistanceSquared(
                    player.getX(), player.getZ(), kingX, kingZ) <= RANGE_SQUARED) {
                nearby.add(player);
            }
        }

        State state = states.computeIfAbsent(level, ignored -> new State(now + INITIAL_DELAY));
        if (nearby.isEmpty()) {
            if (state.active != null) {
                finish(state);
                state.cooldownUntil = now + REPEAT_COOLDOWN_TICKS;
            }
            return;
        }

        if (state.active == null) {
            if (now < state.cooldownUntil) {
                return;
            }
            state.active = nextDecree(state.last);
            state.last = state.active;
            state.startedAt = now;
            state.expiresAt = now + ACTIVE_TICKS;
            state.participants.clear();
        }

        Set<UUID> present = new HashSet<>(nearby.size());
        for (ServerPlayer player : nearby) {
            UUID id = player.getUUID();
            present.add(id);
            Participant participant = state.participants.get(id);
            if (participant == null) {
                participant = new Participant(player, now);
                state.participants.put(id, participant);
                announce(player, state.active);
            }

            if (participant.completed || participant.violated) {
                continue;
            }
            long participantElapsed = now - participant.joinedAt;
            if (participantElapsed < ANNOUNCEMENT_TICKS) {
                continue;
            }
            if (now % 20L == 0L) {
                int seconds = (int) Math.max(0,
                        (state.active == Decree.KNEEL
                                ? (participant.joinedAt + ANNOUNCEMENT_TICKS + KNEEL_TICKS - now + 19) / 20
                                : (state.expiresAt - now + 19) / 20));
                player.displayClientMessage(Component.empty()
                        .append(Component.translatable(state.active.key)
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                        .append(Component.literal("  " + seconds)
                                .withStyle(seconds <= 3 ? ChatFormatting.RED : ChatFormatting.GOLD,
                                        ChatFormatting.BOLD)), true);
            }
            Evaluation result = evaluate(state.active, player, participant, now);
            if (result == Evaluation.COMPLETE) {
                participant.completed = true;
                player.connection.send(new ClientboundClearTitlesPacket(false));
                player.displayClientMessage(Component.translatable("decree.antarchy.obeyed")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
                notifyKingSound(player, "king_success", 0.8F, 1.0F);
            } else if (result == Evaluation.VIOLATED
                    && participantElapsed >= ANNOUNCEMENT_TICKS + RESPONSE_GRACE_TICKS) {
                participant.violated = true;
                punish(level, player);
            }
        }

        // Departed players are no longer bound by a distant command.
        state.participants.entrySet().removeIf(entry -> !present.contains(entry.getKey()));
        if (now >= state.expiresAt || (state.active == Decree.KNEEL
                && !state.participants.isEmpty()
                && state.participants.values().stream().allMatch(
                        participant -> participant.completed || participant.violated))) {
            finish(state);
            state.cooldownUntil = now + REPEAT_COOLDOWN_TICKS;
        }
    }

    /** Called before Antarchy's real King resumes combat AI. */
    void clear(ServerLevel level) {
        State state = states.remove(level);
        if (state != null) {
            finish(state);
        }
    }

    private static boolean targetable(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved()
                && !player.isSpectator() && !player.isCreative();
    }

    private static double horizontalDistanceSquared(double ax, double az, double bx, double bz) {
        double x = ax - bx, z = az - bz;
        return x * x + z * z;
    }

    private static Decree nextDecree(Decree previous) {
        Decree[] values = Decree.values();
        int index = ThreadLocalRandom.current().nextInt(values.length);
        if (values[index] == previous) {
            index = (index + 1 + ThreadLocalRandom.current().nextInt(values.length - 1)) % values.length;
        }
        return values[index];
    }

    private static Evaluation evaluate(Decree decree, ServerPlayer player, Participant participant, long now) {
        return switch (decree) {
            case KNEEL -> player.isCrouching() ? Evaluation.COMPLETE
                    : now >= participant.joinedAt + ANNOUNCEMENT_TICKS + KNEEL_TICKS
                    ? Evaluation.VIOLATED : Evaluation.COMPLIANT;
            case DO_NOT_RUN -> player.isSprinting() ? Evaluation.VIOLATED : Evaluation.COMPLIANT;
            case STAND_TALL -> player.isCrouching() ? Evaluation.VIOLATED : Evaluation.COMPLIANT;
            case STAND_YOUR_GROUND -> {
                if (player.isFallFlying()) {
                    // Antarchy resets its anchor during elytra flight.
                    participant.anchor = player.position();
                    yield Evaluation.COMPLIANT;
                }
                yield participant.anchor.distanceToSqr(player.position()) > STAND_GROUND_ALLOWED_DRIFT_SQUARED
                        ? Evaluation.VIOLATED : Evaluation.COMPLIANT;
            }
        };
    }

    private static void announce(ServerPlayer player, Decree decree) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable(decree.key)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable(decree.key + ".subtitle")
                        .withStyle(ChatFormatting.WHITE)));
        notifyKingSound(player, "king_decree", 1.15F, 1.0F);
    }

    private static void punish(ServerLevel level, ServerPlayer player) {
        // Use Antarchy's own vitality-penalty effect, not a new substitute.
        level.registryAccess().registryOrThrow(Registries.MOB_EFFECT)
                .getHolder(VITALITY_PENALTY)
                .ifPresent(holder -> player.addEffect(new MobEffectInstance(
                        holder, 20 * 30, 0, false, false, false)));
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 45, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("decree.antarchy.violated")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable("decree.antarchy.violated.subtitle")
                        .withStyle(ChatFormatting.YELLOW)));
        notifyKingSound(player, "king_judgement", 1.1F, 1.0F);
    }

    private static void notifyKingSound(ServerPlayer player, String id, float volume, float pitch) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath("antarchy", id);
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.containsKey(key)
                ? BuiltInRegistries.SOUND_EVENT.get(key) : SoundEvents.BELL_BLOCK;
        // The source is offloaded and cannot use Antarchy's normal projected
        // sound helper. Send the voice to its decree recipient, not to a fake
        // entity in a nonexistent chunk.
        player.playNotifySound(sound, SoundSource.HOSTILE, volume, pitch);
    }

    private static void finish(State state) {
        if (state.active == null) {
            return;
        }
        for (Participant participant : state.participants.values()) {
            if (!participant.violated && !participant.completed && participant.player.isAlive()) {
                participant.player.connection.send(new ClientboundClearTitlesPacket(false));
                participant.player.displayClientMessage(Component.empty(), true);
            }
        }
        state.participants.clear();
        state.active = null;
    }

    private enum Evaluation { COMPLIANT, VIOLATED, COMPLETE }

    private enum Decree {
        KNEEL("decree.antarchy.kneel"),
        DO_NOT_RUN("decree.antarchy.do_not_run"),
        STAND_TALL("decree.antarchy.stand_tall"),
        STAND_YOUR_GROUND("decree.antarchy.stand_your_ground");

        private final String key;
        Decree(String key) { this.key = key; }
    }

    private static final class Participant {
        private final ServerPlayer player;
        private final long joinedAt;
        private Vec3 anchor;
        private boolean violated;
        private boolean completed;

        private Participant(ServerPlayer player, long joinedAt) {
            this.player = player;
            this.joinedAt = joinedAt;
            this.anchor = player.position();
        }
    }

    private static final class State {
        private Decree active;
        private Decree last;
        private long startedAt;
        private long expiresAt;
        private long cooldownUntil;
        private final Map<UUID, Participant> participants = new HashMap<>();

        private State(long firstAllowedTick) {
            this.cooldownUntil = firstAllowedTick;
        }
    }
}
