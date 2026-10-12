package dev.playtime.engine.monster;

import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.List;

/** A patrol or chase route built from Monster Route Marker blocks (or written in JSON). */
public class RouteDefinition {
    public Id id;
    public List<Waypoint> points = new ArrayList<>();
    public boolean loop = true;

    public static class Waypoint {
        public BlockRef pos;
        public int waitTicks;
        /** Optional speed override for this leg (blocks per tick). */
        public Double speed;
        /** Animation to play while waiting here (e.g. "search"). */
        public String animation;

        public Waypoint() {}

        public Waypoint(BlockRef pos) {
            this.pos = pos;
        }
    }
}
