package org.openstreetmap.josm.plugins.centernode;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeNodesCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.command.SelectCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Shortcut;

public class ReplaceWithCenterNodeAction extends JosmAction {

    private static final String ACTION_NAME = tr("Replace with Center Node");
    private static final String ICON_NAME = "replacewithcenternode";

    public ReplaceWithCenterNodeAction() {
        super(
            ACTION_NAME,
            ICON_NAME,
            tr("Replace the selected nodes (minimum two) with a node at the center / average. Selecting a way uses all of its nodes."),
            Shortcut.registerShortcut(
                "tools:replacewithcenternode",
                tr("Tool: {0}", ACTION_NAME),
                KeyEvent.VK_C, Shortcut.ALT_CTRL_SHIFT
            ),
            true
        );
    }

    @Override
    protected void updateEnabledState() {
        updateEnabledStateOnCurrentSelection();
    }

    @Override
    protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
        if (selection == null || selection.isEmpty()) {
            setEnabled(false);
            return;
        }
        if (CenterNodeUtil.isNodesOnly(selection)) {
            setEnabled(selection.size() >= 2);
        } else {
            setEnabled(CenterNodeUtil.isClosedWaysAndRelations(selection));
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!isEnabled()) return;

        DataSet ds = getLayerManager().getEditDataSet();
        Collection<OsmPrimitive> selected = ds.getSelected();

        if (CenterNodeUtil.isNodesOnly(selected)) {
            performNodesOnly(ds, selected);
        } else {
            performWaysAndRelations(ds, selected);
        }
    }

    private void performNodesOnly(DataSet ds, Collection<OsmPrimitive> selected) {
        Set<Node> selectedNodes = CenterNodeUtil.getAllNodes(selected);
        LatLon center = CenterNodeUtil.center(selectedNodes);

        Iterator<Node> it = selectedNodes.iterator();
        Node first = it.next();
        List<Node> others = new ArrayList<>();
        while (it.hasNext()) {
            others.add(it.next());
        }

        List<Command> commands = new ArrayList<>();
        commands.add(new MoveCommand(first, center));

        for (Way way : ds.getWays()) {
            boolean nodesChanged = false;
            List<Node> newWayNodes = new ArrayList<>();
            for (Node node : way.getNodes()) {
                if (others.contains(node)) {
                    nodesChanged = true;
                    newWayNodes.add(first);
                } else {
                    newWayNodes.add(node);
                }
            }

            if (!nodesChanged) continue;

            long distinctCount = newWayNodes.stream().distinct().count();
            if (distinctCount == 1) {
                commands.add(new DeleteCommand(ds, way));
            } else {
                commands.add(new ChangeNodesCommand(ds, way, CenterNodeUtil.removeDouble(newWayNodes)));
            }
        }

        commands.add(new DeleteCommand(ds, others));
        commands.add(new SelectCommand(ds, Arrays.asList(first)));

        UndoRedoHandler.getInstance().add(new SequenceCommand(ACTION_NAME, commands));
    }

    private void performWaysAndRelations(DataSet ds, Collection<OsmPrimitive> selected) {
        List<Command> commands = new ArrayList<>();
        List<OsmPrimitive> resultNodes = new ArrayList<>();
        Set<Node> nodesToDelete = new LinkedHashSet<>();
        Set<Way> keptWays = new LinkedHashSet<>();

        for (OsmPrimitive p : selected) {
            Set<Node> nodes = new LinkedHashSet<>();
            Set<Way> elementWays = new LinkedHashSet<>();
            Map<String, String> tags;

            if (p instanceof Way) {
                Way way = (Way) p;
                nodes.addAll(way.getNodes());
                elementWays.add(way);
                tags = way.getKeys();
            } else {
                Relation rel = (Relation) p;
                List<Way> relWays = CenterNodeUtil.getRelationWays(rel);
                for (Way w : relWays) {
                    nodes.addAll(w.getNodes());
                    elementWays.add(w);
                }
                tags = rel.getKeys();
                tags.remove("type"); // don't copy type=multipolygon to the node
            }

            if (nodes.size() < 2) continue;

            // Create a new node at the center with tags applied
            Node centerNode = new Node(CenterNodeUtil.center(nodes));
            centerNode.setKeys(tags);
            commands.add(new AddCommand(ds, centerNode));
            resultNodes.add(centerNode);

            // Delete the relation if present
            if (p instanceof Relation) {
                commands.add(new DeleteCommand(ds, p));
            }

            // Delete ways, but preserve tagged MP member ways (strip moved tags instead)
            boolean isRelation = p instanceof Relation;
            for (Way way : elementWays) {
                if (isRelation && !way.getKeys().isEmpty()) {
                    // Tagged MP member way — keep it, strip tags we moved to the node
                    keptWays.add(way);
                    for (String key : tags.keySet()) {
                        if (way.hasKey(key)) {
                            commands.add(new ChangePropertyCommand(way, key, null));
                        }
                    }
                } else {
                    nodesToDelete.addAll(way.getNodes());
                    commands.add(new DeleteCommand(ds, way));
                }
            }
        }

        // Delete orphaned nodes — skip any node still referenced by a way in the dataset
        Set<Way> deletedWays = new LinkedHashSet<>();
        for (OsmPrimitive p : selected) {
            if (p instanceof Way) {
                deletedWays.add((Way) p);
            } else if (p instanceof Relation) {
                deletedWays.addAll(CenterNodeUtil.getRelationWays((Relation) p));
            }
        }
        deletedWays.removeAll(keptWays);

        nodesToDelete.removeIf(node -> {
            for (OsmPrimitive referrer : node.getReferrers()) {
                if (referrer instanceof Way && !deletedWays.contains(referrer)) {
                    return true;
                }
            }
            return false;
        });
        if (!nodesToDelete.isEmpty()) {
            commands.add(new DeleteCommand(ds, nodesToDelete));
        }

        if (!resultNodes.isEmpty()) {
            commands.add(new SelectCommand(ds, resultNodes));
            UndoRedoHandler.getInstance().add(new SequenceCommand(ACTION_NAME, commands));
        }
    }
}
