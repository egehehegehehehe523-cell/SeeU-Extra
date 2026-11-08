package dev.keryeshka.seeu.extra.server;

/** NeoForge Antarchy King mixin bridge: preserve idle heading across offload/restore. */
public interface KingFlightSteeringAccess {
    KingFlightMotion.State seeuExtra$flightState();
    void seeuExtra$restoreFlightState(KingFlightMotion.State state);
}
