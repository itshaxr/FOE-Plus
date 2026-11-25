package dannypx.foe.entity;

import dannypx.foe.FishOnMCExtras;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class FishingHookEntityModel<T extends FishingHookRenderState> extends EntityModel<T> {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "fishing_hook"), "main");
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "textures/entity/fishing_hook.png");

    public static final RenderType RENDER_LAYER = RenderTypes.entityTranslucent(TEXTURE);

    private static final float ANGLE_180_DEGREES = (float) (1f * Math.PI);

    public FishingHookEntityModel(@NotNull ModelPart root) {
        super(root);
    }

    /// The round bobber's nub and hook (its round body is drawn on its own: see [#submitRoundBody]).
    public static final ModelLayerLocation ROUND_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "fishing_hook_round"), "main");

    public static LayerDefinition generateRoundModel() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        partDefinition.addOrReplaceChild("top", CubeListBuilder.create().texOffs(12, 0).addBox(-.5f, 3f, -.5f, 1f, 1f, 1f), PartPose.ZERO);
        partDefinition.addOrReplaceChild("hook1", CubeListBuilder.create().texOffs(12, 0).addBox(-.5f, -1f, -.5f, 1f, 1f, 1f), PartPose.ZERO);
        partDefinition.addOrReplaceChild("hook2", CubeListBuilder.create().texOffs(12, 0).addBox(-.5f, -2f, -.5f, 1f, 1f, 1f), PartPose.ZERO);
        partDefinition.addOrReplaceChild("hook3", CubeListBuilder.create().texOffs(12, 0).addBox(-.5f, -3f, -.5f, 1f, 1f, 1f), PartPose.ZERO);
        partDefinition.addOrReplaceChild("hook4", CubeListBuilder.create().texOffs(12, 0).addBox(-1.5f, -3f, -.5f, 1f, 1f, 1f), PartPose.ZERO);
        partDefinition.addOrReplaceChild("hook5", CubeListBuilder.create().texOffs(12, 0).addBox(-2.5f, -2f, -.5f, 1f, 1f, 1f), PartPose.ZERO);
        return LayerDefinition.create(meshDefinition, 16, 9);
    }

    /// The round bobber's body: a ball where the block's body is (centred 1.5 pixels up, a little wider than the
    /// block so it looks as big), wrapped in the preset's side faces: the four sides go round it and run from its
    /// top to its bottom, so stripes, bands and dots stay where they are on the block.
    public static void submitRoundBody(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
                                       RenderType renderType, int light) {
        collector.submitCustomGeometry(poseStack, renderType, (pose, vc) -> {
            final int LAT = 10, LON = 20;
            final float R = 1.75f / 16f, CY = 1.5f / 16f;
            for (int i = 0; i < LAT; i++) {
                double t0 = Math.PI * i / LAT, t1 = Math.PI * (i + 1) / LAT;
                for (int j = 0; j < LON; j++) {
                    double p0 = 2 * Math.PI * j / LON, p1 = 2 * Math.PI * (j + 1) / LON;
                    // outward-facing, anticlockwise seen from outside
                    roundVertex(vc, pose, t0, p0, R, CY, light, j / (float) LON, i / (float) LAT);
                    roundVertex(vc, pose, t1, p0, R, CY, light, j / (float) LON, (i + 1) / (float) LAT);
                    roundVertex(vc, pose, t1, p1, R, CY, light, (j + 1) / (float) LON, (i + 1) / (float) LAT);
                    roundVertex(vc, pose, t0, p1, R, CY, light, (j + 1) / (float) LON, i / (float) LAT);
                }
            }
        });
    }

    /// One point on the ball: `theta` down from the top, `phi` round; `fu` round (0-1), `fv` down (0-1).
    private static void roundVertex(com.mojang.blaze3d.vertex.VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                                    double theta, double phi, float r, float cy, int light, float fu, float fv) {
        float nx = (float) (Math.sin(theta) * Math.cos(phi)), ny = (float) Math.cos(theta), nz = (float) (Math.sin(theta) * Math.sin(phi));
        // the side faces' strip: 12 wide (the four faces), 3 tall, stored upside down (its top row is the bottom)
        float u = 12f * fu / 16f, v = (6f - 3f * fv) / 9f;
        vc.addVertex(pose, nx * r, cy + ny * r, nz * r).setColor(-1).setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    public static LayerDefinition generateModel() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();

        partDefinition.addOrReplaceChild("top", CubeListBuilder.create().texOffs(12, 0).addBox(-.5f, 3f, -.5f, 1f, 1f, 1f), PartPose.ZERO);
        partDefinition.addOrReplaceChild("fishing_hook", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, 0f, -1.5f, 3f, 3f, 3f), PartPose.offsetAndRotation(0f, 3f, 0f, ANGLE_180_DEGREES, 0f, 0f));
        partDefinition.addOrReplaceChild("hook1", CubeListBuilder.create().texOffs(12, 0).addBox(-.5f, -1f, -.5f, 1f, 1f, 1f), PartPose.ZERO);
        partDefinition.addOrReplaceChild("hook2", CubeListBuilder.create().texOffs(12, 0).addBox(-.5f, -2f, -.5f, 1f, 1f, 1f), PartPose.ZERO);
        partDefinition.addOrReplaceChild("hook3", CubeListBuilder.create().texOffs(12, 0).addBox(-.5f, -3f, -.5f, 1f, 1f, 1f), PartPose.ZERO);
        partDefinition.addOrReplaceChild("hook4", CubeListBuilder.create().texOffs(12, 0).addBox(-1.5f, -3f, -.5f, 1f, 1f, 1f), PartPose.ZERO);
        partDefinition.addOrReplaceChild("hook5", CubeListBuilder.create().texOffs(12, 0).addBox(-2.5f, -2f, -.5f, 1f, 1f, 1f), PartPose.ZERO);

        return LayerDefinition.create(meshDefinition, 16, 9);
    }

    @Override
    public void setupAnim(T object) {
    }


}
