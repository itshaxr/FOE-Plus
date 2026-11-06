package dannypx.foe.handler.renderer;

import dannypx.foe.FishOnMCExtras;
import dannypx.foe.handler.Handler;
import dannypx.foe.handler.store.ConstantDataHandler;
import dannypx.foe.helper.GlyphHelper;
import dannypx.foe.helper.GuiGraphicsHelper;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.item.*;
import dannypx.foe.handler.logic.SearchHandler;
import dannypx.foe.type.StringStyle;
import dannypx.foe.type.tuple.Pair;
import dannypx.foe.config.Configs;
import java.util.*;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.CommonColors;
import net.minecraft.world.item.ItemStack;

public class ItemRendererHandler extends Handler {
    private static ItemRendererHandler INSTANCE = new ItemRendererHandler();

    public static ItemRendererHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new ItemRendererHandler();
        }
        return INSTANCE;
    }

    //region Fields
    private final Identifier petItemMarker = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/pet_item");
    // Max pet stat markers, ported from FOECollab (AGPL-3.0)
    private final Identifier maxLuckMarker = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/max_luck");
    private final Identifier maxScaleMarker = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/max_scale");
    private final Identifier maxPerfectMarker = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/max_perfect");
    private final Identifier rarityMarker = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/rarity");
    //endregion

    //region Methods
    public void drawRarityMarker(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        if(!Configs.rendererConfig.showRarityMarker.get()) {
            return;
        }

        Pair<Boolean, TagObject> validateItem = ValidateItem.isServerItem(stack, true);

        if(!this.checkIfBlacklisted(validateItem.value2())
                && !validateItem.value2().getRarity().isBlank()
        ) {
            Component rarityComponent = Component.literal(ConstantDataHandler.instance().getConstantData().fishData
                    .getOrDefault(FishTagObject.RARITY, new HashMap<>())
                    .getOrDefault(validateItem.value2().getRarity().toLowerCase(Locale.US), Component.empty()).getString().trim());
            if(rarityComponent.getString().isBlank()) rarityComponent = Component.literal(validateItem.value2().getRarityComponent().getString());

            if(!Objects.equals(rarityComponent, Component.empty())) {
                int color = GlyphHelper.getColor(rarityComponent.getString()).orElse(-1);
                if(color != -1) guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, rarityMarker, x, y, 16, 16, ARGB.color(0xFFFFFF, color));
            }
        }
    }

    private boolean checkIfBlacklisted(TagObject tagObject) {
        if(!Configs.rendererConfig.blackListItems.get().isBlank()) {
            List<String> blacklistedItems = Arrays.stream(Configs.rendererConfig.blackListItems.get().split(",")).map(String::trim).toList();
            return blacklistedItems.contains(tagObject.getType());
        }
        return false;
    }

    public void drawStackCount(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        this.drawStackCount(guiGraphics, font, stack, x, y, true);
    }

    public void drawStackCount(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y, boolean isSmall) {
        Pair<Boolean, TagObject> validatedItem = ValidateItem.isServerItem(stack, true);

        int count = Configs.rendererConfig.showStackCountOnBait.get()
                ? validatedItem.value2().getCount()
                : stack.getCount();
        Component countComponent = isSmall
                ? TextHelper.literal(TextHelper.smallCaps(TextHelper.shortenNumber(count, 0)))
                : TextHelper.literal(TextHelper.shortenNumber(count, 0));
        int countWidth = font.width(countComponent);

        if(count > 1) {
            if(isSmall) {
                GuiGraphicsHelper.drawString(guiGraphics, font, countComponent,
                        x + 19 - 2 - countWidth, y + 6 + 4,
                        StringStyle.SHADOW, StringStyle.MIDDLE, StringStyle.SMALL_CAPS);
            } else {
                GuiGraphicsHelper.drawString(guiGraphics, font, countComponent,
                        x + 19 - 2 - countWidth, y + 6 + 3,
                        StringStyle.SHADOW);
            }
        }
    }

    public void drawSearchItem(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        if(SearchHandler.instance().isOnScreen()
                && SearchHandler.instance().filterItem(stack)) {
            guiGraphics.hLine(x, x + 16, y, CommonColors.RED);
            guiGraphics.hLine(x, x + 16, y + 16, CommonColors.RED);
            guiGraphics.vLine(x, y, y + 16, CommonColors.RED);
            guiGraphics.vLine(x + 16, y, y + 16, CommonColors.RED);
        }
    }

    public void drawPetItemEquipped(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        if(!Configs.rendererConfig.showPetEquippedMarker.get()) {
            return;
        }

        Pair<Boolean, PetTagObject> validatedPet = ValidateItem.isPet(stack);

        // Only for a held item (an amulet): a skin or trail alone gets no marker.
        if(validatedPet.value1() && validatedPet.value2().contains(PetTagObject.ITEM)) {
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, petItemMarker, x, y, 16, 16, CommonColors.WHITE);
        }
    }

    /// Clover = max luck, anchor = max scale, combined icon = both (climate AND location base at 100%).
    /// Ported from FOECollab's ItemMarkerHandler.
    public void drawMaxPetStats(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        if(!Configs.rendererConfig.showMaxPetStatsMarker.get()) {
            return;
        }

        Pair<Boolean, PetTagObject> validatedPet = ValidateItem.isPet(stack);
        if(!validatedPet.value1()) {
            return;
        }
        PetTagObject pet = validatedPet.value2();
        boolean maxLuck = pet.getClimatePercentMaxLuck() >= 1.0f && pet.getLocationPercentMaxLuck() >= 1.0f;
        boolean maxScale = pet.getClimatePercentMaxScale() >= 1.0f && pet.getLocationPercentMaxScale() >= 1.0f;

        Identifier marker = maxLuck && maxScale ? maxPerfectMarker
                : maxLuck ? maxLuckMarker
                : maxScale ? maxScaleMarker
                : null;
        if(marker != null) {
            int iconSize = 7;
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, marker, x, y + 16 - iconSize, iconSize, iconSize, CommonColors.WHITE);
        }
    }

    private static final int MAX_ROD_PART_BORDER = 0xFFC040FF;

    /// A bright purple frame round a rod part rolled at its rarity's max, and round a rod with two
    /// different max stats (on the slot's edge, so it doesn't cover the item).
    public void drawMaxRodPart(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        if (!Configs.rendererConfig.showMaxRodPartMarker.get() || stack.isEmpty()) return;
        if (dannypx.foe.helper.RodPartMax.markFor(dannypx.foe.helper.ItemStackHelper.getTag(stack)) == dannypx.foe.helper.RodPartMax.Mark.NONE) return;
        int c = MAX_ROD_PART_BORDER;
        guiGraphics.fill(x - 1, y - 1, x + 17, y, c);
        guiGraphics.fill(x - 1, y + 16, x + 17, y + 17, c);
        guiGraphics.fill(x - 1, y, x, y + 16, c);
        guiGraphics.fill(x + 16, y, x + 17, y + 16, c);
    }

    private final Identifier selectedSlotMarker = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/selected_slot");

    /// Gold ring behind the pet you have equipped, like FOE. Drawn before the item so it sits behind it.
    public void drawEquippedPetHighlight(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        if(!Configs.rendererConfig.showEquippedPetHighlight.get() || stack.isEmpty()) return;
        if(!dannypx.foe.handler.logic.InventoryHandler.instance().hasPet()) return;

        Pair<Boolean, PetTagObject> validatedPet = ValidateItem.isPet(stack);
        if(!validatedPet.value1()) return;
        java.util.UUID equipped = dannypx.foe.handler.logic.InventoryHandler.instance().getCurrentPet().getID();
        if(equipped != null && equipped.equals(validatedPet.value2().getID())) {
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, selectedSlotMarker, x, y, 16, 16, 0x99FFAA00);
        }
    }

    public void drawFishSize(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        Pair<Boolean, FishTagObject> validatedFish = ValidateItem.isFish(stack);

        if(validatedFish.value1()
                && !validatedFish.value2().getFishSize().isBlank()
        ) {
            Component sizeComponent = ConstantDataHandler.instance().getConstantData().fishData
                    .getOrDefault(FishTagObject.FISH_SIZE, new HashMap<>())
                    .getOrDefault(validatedFish.value2().getFishSize().toLowerCase(Locale.US), Component.empty());
            if(sizeComponent.getString().isBlank()) sizeComponent = validatedFish.value2().getFishSizeComponent();

            if(!sizeComponent.getString().isEmpty()) {
                sizeComponent = TextHelper.substring(sizeComponent, 0, 1);

                guiGraphics.drawString(font, sizeComponent, x + 17 - font.width(sizeComponent), y + 18 - font.lineHeight, CommonColors.WHITE, true);
            }
        }
    }

    public void drawPetRating(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        Pair<Boolean, PetTagObject> validatedPet = ValidateItem.isPet(stack);

        if(validatedPet.value1()
                && !validatedPet.value2().getRatingComponent().getString().isBlank()
        ) {
            Component ratingComponent = validatedPet.value2().getRatingComponent();

            if(!ratingComponent.getString().isEmpty()) {
                Component result = TextHelper.replace(
                        ratingComponent,
                        ratingComponent.getString().substring(1),
                        TextHelper.smallNumber(
                                TextHelper.shortenNumber(validatedPet.value2().getTotalPercent() * 100, 0)
                        )
                );

                ratingComponent = TextHelper.substring(
                        result,
                        1
                );

                guiGraphics.drawString(font, ratingComponent, x + 17 - font.width(ratingComponent), y + 18 - font.lineHeight - 1, CommonColors.WHITE, true);
            }
        }
    }

    /// Rating letter (S, A, B...) in the bottom-right of a pet slot, shown at all times like FOECollab.
    /// While the inspect key is held, drawPetRating shows the full percentage instead.
    public void drawPetRatingLetter(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        if(!Configs.rendererConfig.showPetRatingLetter.get()) {
            return;
        }

        Pair<Boolean, PetTagObject> validatedPet = ValidateItem.isPet(stack);
        if(validatedPet.value1()) {
            Component ratingComponent = validatedPet.value2().getRatingComponent();
            if(!ratingComponent.getString().isBlank()) {
                Component letter = TextHelper.substring(ratingComponent, 0, 1);
                guiGraphics.drawString(font, letter, x + 17 - font.width(letter), y + 18 - font.lineHeight, CommonColors.WHITE, true);
            }
        }
    }

    public void drawArmorQuality(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        Pair<Boolean, ArmorTagObject> validatedArmor = ValidateItem.isArmor(stack);

        if(validatedArmor.value1()
                && !validatedArmor.value2().getQualityComponent().getString().isBlank()
        ) {
            Component qualityArmor = validatedArmor.value2().getQualityComponent();
            Component qualityRaw = TextHelper.substring(qualityArmor, 0, qualityArmor.getString().length() - 1);
            Component qualityComponent = Component.literal(TextHelper.smallCaps(qualityRaw.getString())).setStyle(qualityArmor.getStyle());

            if(!qualityComponent.getString().isEmpty()) {
                guiGraphics.drawString(font, qualityComponent, x + 17 - font.width(qualityComponent), y + 17 - font.lineHeight, CommonColors.WHITE, true);
            }
        }
    }

    public void drawBaitStackerMarker(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        if(minecraft.player != null) {
            ItemStack cursorItem = minecraft.player.containerMenu.getCarried();

            if(!cursorItem.isEmpty()
                    && cursorItem != stack
            ) {
                Pair<Boolean, TagObject> validatedCursor = ValidateItem.isType(cursorItem);

                if(validatedCursor.value1()
                        && (
                                validatedCursor.value2().getType().equals("bait")
                                || validatedCursor.value2().getType().equals("lure")
                )) {
                    Pair<Boolean,TagObject> validatedItem = ValidateItem.isType(stack);

                    if(validatedItem.value1()
                            && (
                                    validatedItem.value2().getType().equals("bait")
                                    || (
                                            validatedItem.value2().getType().equals("lure")
                                            && validatedCursor.value2().getString("size").equals(validatedItem.value2().getString("size"))
                                            && validatedCursor.value2().getString("color").equals(validatedItem.value2().getString("color"))
                                    )
                            )
                            && validatedCursor.value2().getString("name").equals(validatedItem.value2().getString("name"))
                    ) {
                        Component baitStackIcon = Component.literal("+").withStyle(ChatFormatting.GREEN);
                        guiGraphics.drawString(font, baitStackIcon, x + 17 - font.width(baitStackIcon), y - 1, CommonColors.GREEN, true);
                    }
                }
            }
        }
    }
    //endregion

    //region Dev
    /// Field, Pair<Value, Tooltip>
    protected Map<String, Pair<MutableComponent, MutableComponent>> _getFields() {
        return Map.of(
        );
    }
    //endregion
}
