package org.openstreetmap.josm.plugins.centernode;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SelectCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Shortcut;

public class AddCenterNodeAction extends JosmAction {

    private static final String ACTION_NAME = tr("Add Center Node");
    private static final String ICON_NAME = "addcenternode";

    public AddCenterNodeAction() {
        super(
            ACTION_NAME,
            ICON_NAME,
            tr("Add a node at the center / average of the selected nodes (minimum two). Selecting a way uses all of its nodes as well."),
            Shortcut.registerShortcut(
                "tools:addcenternode",
                tr("Tool: {0}", ACTION_NAME),
                KeyEvent.VK_C, Shortcut.ALT_SHIFT
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
            Set<Node> selectedNodes = CenterNodeUtil.getAllNodes(selected);
            Node centerNode = new Node(CenterNodeUtil.center(selectedNodes));
            UndoRedoHandler.getInstance().add(new SequenceCommand(ACTION_NAME, Arrays.asList(
                new AddCommand(ds, centerNode),
                new SelectCommand(ds, Arrays.asList(centerNode))
            )));
        } else {
            // Closed ways and multipolygon relations — one center node per element
            List<Command> commands = new ArrayList<>();
            List<OsmPrimitive> centerNodes = new ArrayList<>();
            for (OsmPrimitive p : selected) {
                Set<Node> nodes = new LinkedHashSet<>();
                if (p instanceof Way) {
                    nodes.addAll(((Way) p).getNodes());
                } else if (p instanceof Relation) {
                    for (Way w : CenterNodeUtil.getRelationWays((Relation) p)) {
                        nodes.addAll(w.getNodes());
                    }
                }
                if (nodes.size() < 2) continue;
                Node centerNode = new Node(CenterNodeUtil.center(nodes));
                commands.add(new AddCommand(ds, centerNode));
                centerNodes.add(centerNode);
            }
            if (!centerNodes.isEmpty()) {
                commands.add(new SelectCommand(ds, centerNodes));
                UndoRedoHandler.getInstance().add(new SequenceCommand(ACTION_NAME, commands));
            }
        }
    }
}
