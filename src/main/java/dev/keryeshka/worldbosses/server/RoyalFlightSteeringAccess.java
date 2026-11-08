package dev.keryeshka.worldbosses.server;

/** NeoForge Antarchy King mixin bridge: preserve idle heading across offload/restore. */
public interface RoyalFlightSteeringAccess {
    RoyalFlightMotion.State seeuExtra$flightState();
    void seeuExtra$restoreFlightState(RoyalFlightMotion.State state);
}
