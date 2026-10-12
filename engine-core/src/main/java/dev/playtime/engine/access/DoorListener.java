package dev.playtime.engine.access;

/** Notified when a door physically opens or closes so the game layer can animate it. */
public interface DoorListener {
    void doorChanged(DoorDefinition door, boolean open);
}
