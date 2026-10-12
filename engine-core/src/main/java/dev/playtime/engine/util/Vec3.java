package dev.playtime.engine.util;

/** Immutable 3D vector used by the cable simulation and monster steering. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }

    public Vec3 subtract(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }

    public Vec3 scale(double s) { return new Vec3(x * s, y * s, z * s); }

    public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }

    public double lengthSq() { return x * x + y * y + z * z; }

    public double length() { return Math.sqrt(lengthSq()); }

    public double distanceTo(Vec3 o) { return subtract(o).length(); }

    public double distanceSq(Vec3 o) { return subtract(o).lengthSq(); }

    public Vec3 normalize() {
        double len = length();
        return len < 1.0e-9 ? ZERO : scale(1.0 / len);
    }

    /** Moves from this point towards {@code target} by at most {@code step}. */
    public Vec3 moveTowards(Vec3 target, double step) {
        Vec3 delta = target.subtract(this);
        double len = delta.length();
        if (len <= step || len < 1.0e-9) {
            return target;
        }
        return add(delta.scale(step / len));
    }

    /** Angle in degrees between two directions; 180 when either is zero-length. */
    public static double angleBetween(Vec3 a, Vec3 b) {
        double la = a.length(), lb = b.length();
        if (la < 1.0e-9 || lb < 1.0e-9) return 180.0;
        double cos = Math.max(-1.0, Math.min(1.0, a.dot(b) / (la * lb)));
        return Math.toDegrees(Math.acos(cos));
    }
}
