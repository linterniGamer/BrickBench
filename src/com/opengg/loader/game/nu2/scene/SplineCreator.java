package com.opengg.loader.game.nu2.scene;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.opengg.core.engine.OpenGG;
import com.opengg.core.math.Vector3f;
import com.opengg.core.render.SceneRenderUnit;
import com.opengg.core.render.objects.DrawnObject;
import com.opengg.core.render.objects.TextureRenderable;
import com.opengg.core.render.texture.Texture;
import com.opengg.core.system.Allocator;
import com.opengg.core.world.components.Component;
import com.opengg.core.world.WorldEngine;
import com.opengg.loader.BrickBench;
import com.opengg.loader.EditorEntity;
import com.opengg.loader.components.EditorEntityRenderComponent;
import com.opengg.loader.editor.EditorState;

public class SplineCreator extends EditorEntityRenderComponent implements EditorEntity<SplineCreator> {
    private SplineContents contents;
    private final Consumer<SplineContents> onCreate;

    public enum SplineType {
        B_SPLINE,
        RAW_SPLINE
    }

    public record SplineContents(SplineType type, List<Vector3f> values, int divisions) {

    }

    public static SplineCreator create(Consumer<SplineContents> onCreate) {
        return create(new SplineContents(SplineType.RAW_SPLINE, List.of(), 10), onCreate);
    }

    public static SplineCreator create(SplineContents initial, Consumer<SplineContents> onCreate) {
        var splineCreator = new SplineCreator(initial, onCreate);
        EditorState.selectTemporaryObject(splineCreator);

        OpenGG.asyncExec(() -> splineCreator.updateContents(splineCreator.getContents()));
    
        return splineCreator;
    }

    public SplineCreator(SplineContents contents, Consumer<SplineContents> onCreate) {
        super("SplineCreator_" + UUID.randomUUID(), new SceneRenderUnit.UnitProperties().shaderPipeline("xFixOnly"));
        this.onCreate = onCreate;
        this.contents = contents;
    }

    private void updateContents(SplineContents newContents) {
        var realPoints = switch (newContents.type()) {
            case B_SPLINE -> newContents.values();
            case RAW_SPLINE -> newContents.values();
        };

        var buf = Allocator.allocFloat(realPoints.size() * 8);
        for (var point : realPoints) {
            buf.put(point.x).put(point.y).put(point.z).put(1).put(1).put(1).put(1).put(1);
        }
        buf.flip();

        var drawnObject = DrawnObject.create(buf);
        drawnObject.setRenderType(DrawnObject.DrawType.LINE_STRIP);

        this.contents = newContents;
        this.setRenderable(new TextureRenderable(drawnObject, Texture.ofColor(Color.GREEN)));
        EditorState.selectTemporaryObject(this);
    }

    private SplineContents getContents() {
        return contents;
    }

    @Override
    public Optional<EditorEntityRenderComponent> getSelectionComponent() {
        return Optional.of(this);
    }

    @Override
    public String name() {
        return getName();
    }

    @Override
    public String path() {
        return "SplineEditors/" + name();
    }

    @Override
    public Map<String, Runnable> getButtonActions() {
        return Map.of(
                "Apply", () -> onCreate.accept(this.getContents()));
    }

    @Override
    public List<Property> properties() {
        var typeProperty = new EnumProperty<>("Spline type", contents.type, true);
        
        var listProperty = new ListProperty("Vertices",
            IntStream.range(0, contents.values().size())
                    .mapToObj(p -> new VectorProperty(Integer.toString(p), contents.values().get(p), true, true))
                    .collect(Collectors.toList()),
            true,
            () -> {
                var newList = new ArrayList<>(contents.values());
                newList.add(BrickBench.CURRENT.player.getPosition());

                OpenGG.asyncExec(() -> updateContents(
                    new SplineContents(contents.type(), List.copyOf(newList), newList.size())));
            },
            (a, idx) -> {
                var newList = new ArrayList<>(contents.values());
                newList.remove((int) idx);

                OpenGG.asyncExec(() -> updateContents(
                    new SplineContents(contents.type(), List.copyOf(newList), newList.size())));
            });

        return switch (contents.type()) {
            case RAW_SPLINE -> List.of(
                    typeProperty,
                    listProperty);
            case B_SPLINE -> List.of(
                    typeProperty,
                    new IntegerProperty("Point count", contents.divisions(), true),
                    listProperty); 
        };
    }

    @Override
    public void applyPropertyEdit(String propName, Property newValue) {
        switch (newValue) {
            case VectorProperty vProp -> {
                var index = Integer.parseInt(propName);
                var listCopy = new ArrayList<Vector3f>(contents.values());
                listCopy.set(index, vProp.value());

                OpenGG.asyncExec(() -> updateContents(
                        new SplineContents(contents.type(), List.copyOf(listCopy), listCopy.size())));
            }
            case null, default -> {
            }
        }
    }
}
