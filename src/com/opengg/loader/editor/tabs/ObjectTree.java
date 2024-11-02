package com.opengg.loader.editor.tabs;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.icons.*;
import com.opengg.core.world.WorldEngine;
import com.opengg.loader.BrickBench;
import com.opengg.loader.EditorEntity;
import com.opengg.loader.Util;
import com.opengg.loader.editor.EditorIcons;
import com.opengg.loader.editor.EditorState;
import com.opengg.loader.editor.EditorTheme;
import com.opengg.loader.editor.components.IconNodeRenderer;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

public class ObjectTree extends JPanel implements MouseListener, Scrollable {
    int rowHeight = 0;
    int forceRectTop = -1;
    int forceRectBot = -1;
    boolean forceType = false;
    public static final int toggleWidth = 18;
    public static final int iconDim = 20;
    private static final FlatSVGIcon eyeSVG = EditorIcons.visibleEye.derive(toggleWidth, 20);
    private static final FlatSVGIcon invisSVG = EditorIcons.invisibleEye.derive(toggleWidth, 20);
    private static Color disableColor;
    private static DefaultMutableTreeNode fullTree;
    private JTree tree;

    private List<String> ignore = List.of(
            "Gameplay",
            "Render/Materials",
            "Render/Textures",
            "Render/Portals",
            "Render/GenericCommand",
            "Render/Lightmaps",
            "Render/Meshes",
            // "Render/Lights",
            "Render/Transforms");

    public ObjectTree() {
        this.setLayout(new BorderLayout());

        fullTree = new DefaultMutableTreeNode(new TreeCategory("Objects"));
        tree = new JTree(fullTree);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(24);
        tree.setCellRenderer(new EditorEntityNodeRenderer());
        this.add(tree, BorderLayout.CENTER);

        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(toggleWidth, this.getHeight()));

        this.add(panel, BorderLayout.WEST);
        this.setFocusable(true);
        this.addMouseListener(this);

        rowHeight = tree.getRowHeight();

        EditorState.addMapChangeListener(m ->
            Thread.startVirtualThread(() -> 
                this.clearTreeFilterSearch()));
        EditorState.addMapReloadListener(m -> 
            Thread.startVirtualThread(() -> 
                this.makeNewTree(EditorState.getNamespace(EditorState.getActiveNamespace()))));
        EditorState.addVisibilityChangeListener(c -> this.setNodeVisibility(c.x(), c.y()));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        disableColor = UIManager.getDefaults().getColor("Button.disabledText");
    }

    @Override
    public Color getBackground() {
        if (tree != null) {
            return tree.getBackground();
        } else {
            return super.getBackground();
        }
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (tree.getRowCount() < 1)
            return;
        rowHeight = tree.getRowHeight();

        Rectangle vis = this.getVisibleRect();
        g.setColor(tree.getBackground());
        // g.setColor(getBackground());
        g.fillRect(vis.x, vis.y, toggleWidth, vis.height);

        int start = tree.getClosestRowForLocation(vis.x, vis.y);
        int end = tree.getClosestRowForLocation(vis.x, vis.y + vis.height + rowHeight);
        int yOff = tree.getRowBounds(start).y;
        for (int i = start; i <= end; i++) {
            if (((DefaultMutableTreeNode) tree.getPathForRow(i).getLastPathComponent())
                    .getUserObject() instanceof VisibilityToggleNode node) {
                if (forceRectBot != -1 && i <= forceRectBot && i >= forceRectTop) {
                    if (!forceType) {
                        invisSVG.paintIcon(this, g, 0, yOff);
                    }
                } else {
                    if (!node.isVisible) {
                        invisSVG.paintIcon(this, g, 0, yOff);
                    }
                }
            }
            yOff += rowHeight;
        }
        forceRectTop = -1;
        forceRectBot = -1;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        Point p = e.getPoint();
        if (p.x > 0 && p.x < toggleWidth) {
            var node = ((DefaultMutableTreeNode) tree.getClosestPathForLocation(e.getX(), e.getY())
                    .getLastPathComponent());
            if (node.getUserObject() instanceof VisibilityToggleNode vn) {
                var newVis = !vn.isVisible;
                forceType = newVis;
                forceRectTop = forceRectBot = -1;
                if (node.getChildCount() != 0) {
                    forceRectTop = tree.getClosestRowForLocation(e.getX(), e.getY());
                    forceRectBot = tree.getRowForPath(tree.getClosestPathForLocation(e.getX(), e.getY())
                            .pathByAddingChild(node.getChildAt(node.getChildCount() - 1)));
                }

                setNodeVisibility(node, newVis);
            }
        }
    }

    public Optional<DefaultMutableTreeNode> getNodeForPath(String path) {
        var nodes = path.split("/");
        var root = (DefaultMutableTreeNode) tree.getModel().getRoot();

        top: for (var node : nodes) {
            for (var e = root.children(); e.hasMoreElements();) {
                var next = e.nextElement();
                if (next.toString().equalsIgnoreCase(node)) {
                    root = (DefaultMutableTreeNode) next;
                    continue top;
                }
            }
            return Optional.empty(); // Failed to find
        }

        return Optional.of(root);
    }

    public void setNodeVisibility(String node, boolean visibility) {
        getNodeForPath(node).ifPresent(n -> setNodeVisibility(n, visibility));
    }

    public void toggleAll(boolean visible) {
        var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        setNodeVisibility(root, visible);
    }

    private void setNodeVisibility(DefaultMutableTreeNode node, boolean visibility) {
        node.preorderEnumeration().asIterator().forEachRemaining(sub -> applyVisibilityToNode((DefaultMutableTreeNode) sub, visibility));
        this.repaint();
    }

    private String getFullPath(DefaultMutableTreeNode node) {
        StringBuilder path = new StringBuilder();

        for (var parent : node.getPath()) {
            if (parent.getParent() != null) {
                path.append("/").append(switch (((DefaultMutableTreeNode) parent).getUserObject()) {
                    case EditorEntityNode mon -> mon.object.name();
                    case TreeCategory cg -> cg.name;
                    default -> throw new UnsupportedOperationException();
                });
            }
        }
        var outPath = path.toString();
        if(outPath.length() > 1){
            return outPath.substring(1);
        } else {
            return "";
        }
    }

    private void applyVisibilityToNode(DefaultMutableTreeNode node, boolean visible) {
        ((VisibilityToggleNode) node.getUserObject()).isVisible = visible;
        var path = getFullPath(node);
        EditorState.CURRENT.objectVisibilities.put(path, visible);

        var components = WorldEngine.findEverywhereByName(path);
        components.forEach(c -> c.setEnabled(visible));
    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    public void makeNewTree(Map<String, EditorEntity<?>> namespace) {
        fullTree = createRootNode(namespace);
        SwingUtilities.invokeLater(() -> createTree(fullTree));
    }

    public void filterExistingTree(String searchItem) {
        if (searchItem.trim().isEmpty()) {
            SwingUtilities.invokeLater(() -> createTree(fullTree));
        } else {
            var newNode = filterTree(fullTree, searchItem);
            SwingUtilities.invokeLater(() -> createTree(newNode));
        }
    }

    private void createTree(DefaultMutableTreeNode node) {
        this.remove(tree);

        tree = new JTree(node);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(24);
        tree.setCellRenderer(new EditorEntityNodeRenderer());

        JPanel top = this;
        tree.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int selRow = tree.getRowForLocation(e.getX(), e.getY());
                if (selRow != -1) {
                    var selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                    if(selectedNode != null) {
                        if (selectedNode.getUserObject() instanceof ObjectTree.EditorEntityNode mon) {
                            if (e.getClickCount() == 1) {
                                EditorState.selectObject(mon.object);
                            } else if (e.getClickCount() == 2 && mon.object.pos() != null) {
                                BrickBench.CURRENT.player.setPositionOffset(mon.object.pos().multiply(-1, 1, 1));
                            }
                        }
                    }
                }
                top.repaint();
            }
        });
        this.add(tree, BorderLayout.CENTER);
        this.repaint();
        this.validate();
    }

    private DefaultMutableTreeNode createRootNode(Map<String, EditorEntity<?>> namespace) {
        var root = new DefaultMutableTreeNode(new TreeCategory("Objects"));
        var nodes = new LinkedHashMap<String, DefaultMutableTreeNode>();

        for (var item : namespace.entrySet()) {
            if (ignore.stream().anyMatch(i -> item.getKey().contains(i))) {
                continue;
            }

            var path = item.getValue().path().substring(0, item.getValue().path().lastIndexOf('/'));
            var pathItems = List.of(path.split("/"));
            for (int i = 0; i < pathItems.size(); i++) {
                var pathSoFar = String.join("/", pathItems.subList(0, i + 1));
                var needsToAdd = !nodes.containsKey(pathSoFar);

                if (needsToAdd) {
                    var intermediateIcon = EditorIcons.objectTreeIconMap.getOrDefault(pathItems.get(i), null);
                    var intermediateNode = intermediateIcon == null
                            ? new DefaultMutableTreeNode(new TreeCategory(pathItems.get(i)))
                            : new DefaultMutableTreeNode(new TreeCategory(pathItems.get(i), intermediateIcon));

                    ((TreeCategory) intermediateNode.getUserObject()).isVisible = EditorState.CURRENT.objectVisibilities
                            .getOrDefault(pathSoFar, true);

                    if (i == 0) {
                        root.add(intermediateNode);
                    } else {
                        var parentPath = String.join("/", pathItems.subList(0, i));
                        nodes.get(parentPath).add(intermediateNode);
                    }
                    nodes.put(pathSoFar, intermediateNode);
                }
            }

            var nodeObject = new EditorEntityNode(item.getValue());
            var node = new DefaultMutableTreeNode(nodeObject);

            nodes.get(path).add(node);
            nodes.put(item.getKey(), node);
        }

        return root;
    }

    private Optional<DefaultMutableTreeNode> applySubVisibility(DefaultMutableTreeNode node, String searchItem) {
        var allGoodChildren = Util.stream(node.children().asIterator())
            .flatMap(c -> applySubVisibility((DefaultMutableTreeNode) c, searchItem).stream())
            .toList();

        if(!allGoodChildren.isEmpty()) {
            var nodeCopy = new DefaultMutableTreeNode(node.getUserObject());
            allGoodChildren.forEach(child -> nodeCopy.add(child));

            return Optional.of(nodeCopy);
        } else {
            var userObject = node.getUserObject();
            var nodeCopy = new DefaultMutableTreeNode(node.getUserObject());

            switch (userObject) {
                case EditorEntityNode entity -> {
                    if (entity.object.path().toLowerCase().contains(searchItem.toLowerCase(Locale.ROOT))) {
                        return Optional.of(nodeCopy);
                    } else {
                        for (var property : entity.object.properties()) {
                            if ((property instanceof EditorEntity.StringProperty ||
                                    property instanceof EditorEntity.EnumProperty ||
                                    property instanceof EditorEntity.EditorEntityProperty) &&
                                    property.stringValue().toLowerCase(Locale.ROOT)
                                            .contains(searchItem.toLowerCase(Locale.ROOT))) {
                                return Optional.of(nodeCopy);
                            }
                        }
                    }
                }
                case TreeCategory category -> {
                    if (category.toString().toLowerCase().contains(searchItem)) {
                        return Optional.of(nodeCopy);
                    }
                }
                default -> {
                    return Optional.empty();
                }
            }
            
            return Optional.empty();
        }
    }

    public DefaultMutableTreeNode filterTree(DefaultMutableTreeNode root, String searchItem) {
        var allGoodChildren = Util.stream(root.children().asIterator())
            .flatMap(c -> applySubVisibility((DefaultMutableTreeNode) c, searchItem).stream())
            .toList();
        
        var nodeCopy = new DefaultMutableTreeNode(root.getUserObject());
        allGoodChildren.forEach(child -> nodeCopy.add(child));

        return nodeCopy;
    }

    public void clearTreeFilterSearch() {
        EditorState.CURRENT.objectVisibilities.clear();
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return tree.getPreferredScrollableViewportSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return tree.getScrollableUnitIncrement(visibleRect, orientation, direction);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return tree.getScrollableBlockIncrement(visibleRect, orientation, direction);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    public static class EditorEntityNodeRenderer extends IconNodeRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            var node = (DefaultMutableTreeNode) value;

            if (node.getUserObject() instanceof VisibilityToggleNode c4) {
                this.setForeground(c4.isVisible ? this.getForeground() : disableColor);
            }

            return this;
        }
    }

    private abstract static sealed class VisibilityToggleNode {
        public boolean isVisible = true;
    }

    private static final class TreeCategory extends VisibilityToggleNode implements IconNodeRenderer.IconNode {
        public String name;
        public Icon icon;

        public TreeCategory(String name) {
            this(name, null);
        }

        public TreeCategory(String name, Icon icon) {
            this.name = name;
            this.icon = icon;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public Icon getIcon() {
            return icon;
        }
    }

    private static final class EditorEntityNode extends VisibilityToggleNode {
        public EditorEntity<?> object;

        public EditorEntityNode(EditorEntity<?> object) {
            this.object = object;
        }

        @Override
        public String toString() {
            return object.name().isEmpty() ? "Unnamed " + object.getClass().getSimpleName() : object.name();
        }
    }
}
