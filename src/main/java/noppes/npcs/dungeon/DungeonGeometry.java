package noppes.npcs.dungeon;

import noppes.npcs.dungeon.DungeonDefinition.Piece;
import noppes.npcs.dungeon.DungeonDefinition.Shape;

/**
 * Raster of dungeon pieces in local XZ. Matches herdstone_dungeon.html
 * (1.8 SVG units = 1 block), not axis-aligned bounding boxes.
 */
public final class DungeonGeometry {
    private DungeonGeometry() {
    }

    public static boolean contains(final Piece rec, final int x, final int z) {
        switch (rec.shape) {
            case ELLIPSE:
                return insideEllipse(rec, x, z);
            case POLY:
                return insidePoly(rec.points, x + 0.5, z + 0.5);
            case PATH:
                return nearPolyline(rec.points, rec.thickness * 0.5, x + 0.5, z + 0.5);
            case RECT:
            default:
                return x >= rec.x && x < rec.x + rec.w && z >= rec.z && z < rec.z + rec.d;
        }
    }

    public static boolean perimeter(final Piece rec, final int x, final int z) {
        if (!contains(rec, x, z)) {
            return false;
        }
        return !contains(rec, x - 1, z) || !contains(rec, x + 1, z)
                || !contains(rec, x, z - 1) || !contains(rec, x, z + 1);
    }

    public static int[] bbox(final int[] xz, final int pad) {
        int minx = xz[0];
        int minz = xz[1];
        int maxx = minx;
        int maxz = minz;
        for (int i = 2; i + 1 < xz.length; i += 2) {
            if (xz[i] < minx) {
                minx = xz[i];
            }
            if (xz[i] > maxx) {
                maxx = xz[i];
            }
            if (xz[i + 1] < minz) {
                minz = xz[i + 1];
            }
            if (xz[i + 1] > maxz) {
                maxz = xz[i + 1];
            }
        }
        return new int[] { minx - pad, minz - pad, maxx - minx + 1 + pad * 2, maxz - minz + 1 + pad * 2 };
    }

    static boolean insideEllipse(final Piece rec, final int x, final int z) {
        final double cx = rec.x + (rec.w - 1) * 0.5;
        final double cz = rec.z + (rec.d - 1) * 0.5;
        final double rx = Math.max(0.5, rec.w * 0.5);
        final double rz = Math.max(0.5, rec.d * 0.5);
        final double dx = (x - cx) / rx;
        final double dz = (z - cz) / rz;
        return dx * dx + dz * dz <= 1.0;
    }

    static boolean insidePoly(final int[] pts, final double x, final double z) {
        if (pts == null || pts.length < 6) {
            return false;
        }
        boolean inside = false;
        final int n = pts.length;
        for (int i = 0, j = n - 2; i < n; j = i, i += 2) {
            final double zi = pts[i + 1];
            final double zj = pts[j + 1];
            final boolean cross = (zi > z) != (zj > z);
            if (cross) {
                final double xi = pts[i];
                final double xj = pts[j];
                final double xInt = (xj - xi) * (z - zi) / (zj - zi) + xi;
                if (x < xInt) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    static boolean nearPolyline(final int[] pts, final double radius, final double x, final double z) {
        if (pts == null || pts.length < 4) {
            return false;
        }
        final double r2 = radius * radius;
        for (int i = 0; i + 3 < pts.length; i += 2) {
            if (dist2ToSegment(x, z, pts[i], pts[i + 1], pts[i + 2], pts[i + 3]) <= r2) {
                return true;
            }
        }
        return false;
    }

    private static double dist2ToSegment(final double px, final double pz, final double ax, final double az,
                                         final double bx, final double bz) {
        final double vx = bx - ax;
        final double vz = bz - az;
        final double len2 = vx * vx + vz * vz;
        double t = 0.0;
        if (len2 > 1.0e-8) {
            t = ((px - ax) * vx + (pz - az) * vz) / len2;
            if (t < 0.0) {
                t = 0.0;
            } else if (t > 1.0) {
                t = 1.0;
            }
        }
        final double dx = px - (ax + t * vx);
        final double dz = pz - (az + t * vz);
        return dx * dx + dz * dz;
    }

    static boolean anyContains(final java.util.List<Piece> list, final int level, final int x, final int z) {
        for (int i = 0, n = list.size(); i < n; i++) {
            final Piece p = list.get(i);
            if (p.level == level && contains(p, x, z)) {
                return true;
            }
        }
        return false;
    }

    static boolean isWalk(final Piece rec) {
        switch (rec.kind) {
            case PATH:
            case CORRIDOR:
                return true;
            default:
                return false;
        }
    }

    static boolean isSolidRoom(final Piece rec) {
        if (rec.shape == Shape.PATH || isWalk(rec)) {
            return false;
        }
        switch (rec.kind) {
            case BORDER:
            case TOWER:
            case MARK:
            case SHAFT:
                return false;
            default:
                return true;
        }
    }
}
