package com.opengg.loader.game.nu2.scene;

import com.opengg.core.math.Vector3f;
import com.opengg.loader.MapEntity;
import com.opengg.loader.Util;
import com.opengg.loader.editor.EditorState;
import com.opengg.loader.loading.MapLoader;
import com.opengg.loader.loading.MapWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record Spline(String name, List<Vector3f> points, int address) implements MapEntity<Spline> {
    @Override
    public Vector3f pos() {
        return points.get(0);
    }

    @Override
    public String path() {
        return "Splines/" + name;
    }
    
    public void applySplineContentsEdits(SplineCreator.SplineContents editorContents) {
        if (points.size() != editorContents.values().size()) {
            var diff = editorContents.values().size() - points.size();
            try {
                if (diff > 0) {
                    SceneFileWriter.addSpace(address + 8, diff * 12);
                } else {
                    SceneFileWriter.removeSpace(address + 8, -diff * 12);
                }

                MapWriter.applyPatch(MapWriter.WritableObject.SPLINE, address, Util.littleEndian((short) editorContents.values().size()));
                EditorState.updateMap(MapLoader.reloadIndividualFile("gsc"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        var newBuffer = ByteBuffer.allocate(editorContents.values().size() * 3 * Float.BYTES);

        for (var value : editorContents.values()) {
            newBuffer.put(value.toLittleEndianByteBuffer());
        }

        newBuffer.flip();

        MapWriter.applyPatch(MapWriter.WritableObject.SCENE, address + 8, newBuffer);
        EditorState.selectObject(this);
    }

    @Override
    public Map<String, Runnable> getButtonActions() {
        return Map.of(
            "Edit spline", () -> SplineCreator.create(
                new SplineCreator.SplineContents(SplineCreator.SplineType.RAW_SPLINE, points, points.size()),
                this::applySplineContentsEdits)
        );
    }

    @Override
    public void applyPropertyEdit(String propName, Property newValue) {
        switch (newValue) {
            case VectorProperty vProp -> {
                var index = Integer.parseInt(propName);
                var offset = address + 8 + (12 * index);
                MapWriter.applyPatch(MapWriter.WritableObject.SPLINE, offset, vProp.value().toLittleEndianByteBuffer());
            }
            case null, default -> {
            }
        }
    }

    @Override
    public List<Property> properties() {
        return List.of(
                new StringProperty("Name",name(), false, 128),
                new IntegerProperty("Vertex count", points.size(), false),
                new ListProperty("Vertices",
                        IntStream.range(0, points.size())
                                .mapToObj(p -> new VectorProperty(Integer.toString(p), points.get(p), true, false))
                                .collect(Collectors.toList()), false)
        );
    }
}
