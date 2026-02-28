package org.openstreetmap.josm.plugins.centernode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;

public final class CenterNodeUtil {

    private CenterNodeUtil() {
    }

    public static Set<Node> getAllNodes(Collection<? extends OsmPrimitive> primitives) {
        Set<Node> nodes = new LinkedHashSet<>();
        for (OsmPrimitive p : primitives) {
            if (p instanceof Node) {
                nodes.add((Node) p);
            } else if (p instanceof Way) {
                nodes.addAll(((Way) p).getNodes());
            }
        }
        return nodes;
    }

    public static LatLon center(Collection<Node> nodes) {
        double lat = nodes.stream().mapToDouble(Node::lat).average().orElse(0);
        double lon = nodes.stream().mapToDouble(Node::lon).average().orElse(0);
        return new LatLon(lat, lon);
    }

    public static boolean isNodesOnly(Collection<? extends OsmPrimitive> selection) {
        return !selection.isEmpty()
            && selection.stream().allMatch(p -> p instanceof Node);
    }

    public static boolean isClosedWaysAndRelations(Collection<? extends OsmPrimitive> selection) {
        if (selection.isEmpty()) return false;
        for (OsmPrimitive p : selection) {
            if (p instanceof Way) {
                if (!((Way) p).isClosed()) return false;
            } else if (p instanceof Relation) {
                if (!"multipolygon".equals(p.get("type"))) return false;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Collects all member ways from a multipolygon relation.
     */
    public static List<Way> getRelationWays(Relation relation) {
        List<Way> ways = new ArrayList<>();
        for (RelationMember member : relation.getMembers()) {
            if (member.isWay()) {
                ways.add(member.getWay());
            }
        }
        return ways;
    }

    /**
     * Removes consecutive duplicate nodes from a list.
     * Public copy of Way::removeDouble.
     *
     * @see Way
     */
    public static List<Node> removeDouble(List<Node> nodes) {
        ArrayList<Node> copy = new ArrayList<>(nodes);
        Node last = null;
        int i = 0;
        while (i < copy.size() && copy.size() > 2) {
            Node n = copy.get(i);
            if (last == n) {
                copy.remove(i);
            } else {
                last = n;
                i++;
            }
        }
        return copy;
    }
}
