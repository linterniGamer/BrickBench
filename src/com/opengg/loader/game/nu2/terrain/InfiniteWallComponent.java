package com.opengg.loader.game.nu2.terrain;

import com.opengg.core.engine.Resource;
import com.opengg.core.math.Vector3f;
import com.opengg.core.render.Renderable;
import com.opengg.core.render.SceneRenderUnit;
import com.opengg.core.render.internal.opengl.OpenGLRenderer;
import com.opengg.core.render.objects.DrawnObject;
import com.opengg.core.render.objects.TextureRenderable;
import com.opengg.core.render.texture.Texture;
import com.opengg.core.system.Allocator;
import com.opengg.loader.components.EditorEntityRenderComponent;

public class InfiniteWallComponent extends EditorEntityRenderComponent {
    private static Texture wallt;
    public InfiniteWallComponent(InfiniteWall wall) {
        super(wall, new SceneRenderUnit.UnitProperties().shaderPipeline("infwall"));

        if(wall.wall().isEmpty()) return;

        var newMesh = Allocator.allocFloat(wall.wall().size()*8);
        var last = wall.wall().get(0);
        var UV = 0;
        for(var piece : wall.wall()){
            var distFromLast = piece.xz().distanceTo(last.xz());
            UV += distFromLast;
            newMesh.put(piece.toFloatArray()).put(new Vector3f(0,1,0).toFloatArray()).put(UV).put(0);
            last = piece;
        }

        newMesh.flip();
        if(wallt == null){
            wallt = Texture.create(Texture.config().wrapType(Texture.WrapType.REPEAT).minimumFilter(Texture.FilterType.NEAREST), Resource.getTextureData("wall.png"));
        }

        Renderable render = new TextureRenderable(DrawnObject.create(newMesh).setRenderType(DrawnObject.DrawType.LINE_STRIP),wallt);
        this.setRenderable(() -> {
            OpenGLRenderer.getOpenGLRenderer().setBackfaceCulling(false);
            render.render();
        });
        this.setUpdateEnabled(false);
    }
}
