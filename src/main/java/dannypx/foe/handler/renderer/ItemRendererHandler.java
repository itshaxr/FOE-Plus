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
    /// The same star in white (with its shading), tinted the colour of the held amulet.
    private final Identifier petItemMarkerTint = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/pet_item_tint");
    /// Amulet colour per pet stack (slot stacks are replaced when the held item changes), so the
    /// held item isn't decoded every frame. -1 = no colour, use the gold star.
    private final java.util.Map<ItemStack, Integer> amuletColors = new java.util.IdentityHashMap<>();
    { dannypx.foe.handler.logic.MemoryGuard.register("amuletColours", amuletColors::size, amuletColors::clear); }
    // Max pet stat markers, ported from FOECollab (AGPL-3.0)
    private final Identifier maxLuckMarker = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/max_luck");
    private final Identifier maxScaleMarker = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/max_scale");
    private final Identifier maxPerfectMarker = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/max_hundred");   // FOE's 💯, in both icon styles
    private final Identifier rarityMarker = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/rarity");
    /// FOE's rarity marker: a small corner in the top left, for the FOE icon style.
    private final Identifier rarityMarkerFoe = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/rarity_foe");

    /// True when the markers should look like FOE's.
    private static boolean foeIcons() {
        return Configs.rendererConfig.iconStyle.get() == dannypx.foe.type.IconStyle.FOE;
    }
    //endregion

    //region Methods
    public void drawRarityMarker(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        if(!Configs.rendererConfig.showRarityMarker.get()) {
            return;
        }

        Pair<Boolean, TagObject> validateItem = ValidateItem.isServerItem(stack, true);

        String rarity = validateItem.value2().getRarity();
        if(!rarity.isBlank() && !this.checkIfBlacklisted(validateItem.value2())) {
            // the marker's colour, worked out once per rarity (it was every item, every frame)
            String key = rarity.toLowerCase(Locale.US);
            Integer known = rarityColors.get(key);
            int color = known != null ? known : ((java.util.function.Function<String, Integer>) r -> {
                Component rarityComponent = Component.literal(ConstantDataHandler.instance().getConstantData().fishData
                        .getOrDefault(FishTagObject.RARITY, new HashMap<>())
                        .getOrDefault(r, Component.empty()).getString().trim());
                if(rarityComponent.getString().isBlank()) rarityComponent = Component.literal(validateItem.value2().getRarityComponent().getString());
                return rarityComponent.getString().isEmpty() ? -1 : GlyphHelper.getColor(rarityComponent.getString()).orElse(-1);
            }).apply(key);
            // kept only once found (before the server's data arrives there's none yet)
            if (known == null && color != -1) rarityColors.put(key, color);
            if(color != -1) guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, foeIcons() ? rarityMarkerFoe : rarityMarker, x, y, 16, 16, ARGB.color(0xFFFFFF, color));
        }
    }

    private final java.util.Map<String, Integer> rarityColors = new java.util.concurrent.ConcurrentHashMap<>();
    { dannypx.foe.handler.logic.MemoryGuard.register("rarityColours", rarityColors::size, rarityColors::clear); }

    /// The blacklist setting, split once for each time it changes.
    private String blacklistText = null;
    private List<String> blacklist = List.of();

    private boolean checkIfBlacklisted(TagObject tagObject) {
        String text = Configs.rendererConfig.blackListItems.get();
        if (text.isBlank()) return false;
        if (!text.equals(blacklistText)) {
            blacklistText = text;
            blacklist = Arrays.stream(text.split(",")).map(String::trim).toList();
        }
        return blacklist.contains(tagObject.getType());
    }

    private final java.util.Map<Integer, Component> counts = new java.util.concurrent.ConcurrentHashMap<>(), smallCounts = new java.util.concurrent.ConcurrentHashMap<>();
    { dannypx.foe.handler.logic.MemoryGuard.register("stackCounts", () -> counts.size() + smallCounts.size(), () -> { counts.clear(); smallCounts.clear(); }); }

    public void drawStackCount(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        this.drawStackCount(guiGraphics, font, stack, x, y, true);
    }

    public void drawStackCount(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y, boolean isSmall) {
        Pair<Boolean, TagObject> validatedItem = ValidateItem.isServerItem(stack, true);

        int count = Configs.rendererConfig.showStackCountOnBait.get()
                ? validatedItem.value2().getCount()
                : stack.getCount();
        if (count <= 1) return;
        // the number's text, made once for each count (it was every item, every frame)
        java.util.Map<Integer, Component> made = isSmall ? smallCounts : counts;
        if (made.size() > 512) made.clear();     // bait counts tick down every cast: don't keep them all
        Component countComponent = made.computeIfAbsent(count, n -> isSmall
                ? TextHelper.literal(TextHelper.smallCaps(TextHelper.shortenNumber(n, 0)))
                : TextHelper.literal(TextHelper.shortenNumber(n, 0)));
        int countWidth = font.width(countComponent);

        {
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

    private int amuletColor(ItemStack petStack, PetTagObject pet) {
        Integer cached = amuletColors.get(petStack);
        if (cached != null) return cached;
        if (amuletColors.size() >= 512) amuletColors.clear();
        int rgb = dannypx.foe.item.PetHeldItem.nameColor(dannypx.foe.item.PetHeldItem.decodeUncached(pet));
        amuletColors.put(petStack, rgb);
        return rgb;
    }

    public void drawPetItemEquipped(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        if(!Configs.rendererConfig.showPetEquippedMarker.get()) {
            return;
        }

        Pair<Boolean, PetTagObject> validatedPet = ValidateItem.isPet(stack);

        // Only for a held item (an amulet): a skin or trail alone gets no marker.
        if(validatedPet.value1() && validatedPet.value2().contains(PetTagObject.ITEM)) {
            // The star in the amulet's colour, in both icon styles.
            int rgb = amuletColor(stack, validatedPet.value2());
            if (rgb < 0) guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, petItemMarker, x, y, 16, 16, CommonColors.WHITE);
            else guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, petItemMarkerTint, x, y, 16, 16, 0xFF000000 | rgb);
        }
    }

    /// Clover = max luck, anchor = max scale, 💯 = both, a perfect pet (climate AND location base at 100%).
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

    private static final Identifier MAX_ROLL_DOT = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/max_roll_dot");
    private final Map<String, Identifier> rollIcons = new HashMap<>();

    /// A bright purple frame round a rod part rolled at its rarity's max, and round a rod with two
    /// different max stats (on the slot's edge, so it doesn't cover the item).
    public void drawMaxRodPart(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        boolean icons = Configs.rendererConfig.showRodPartRollIcon.get();
        boolean marker = Configs.rendererConfig.showMaxRodPartMarker.get();
        if (!icons && !marker) return;
        dannypx.foe.helper.RodPartMax.Info info = dannypx.foe.helper.RodPartMax.infoFor(dannypx.foe.helper.ItemStackHelper.getTag(stack));
        // What the part (or each part of a rod) is calibrated for: small icons in the bottom-right
        // corner, stacked upwards, all purple for a stat rolled at its max, half purple when
        // only one of two parts with that stat has it maxed (maxed ones at the bottom).
        boolean drewMaxIcon = false;
        if (icons) {
            java.util.List<dannypx.foe.helper.RodPartMax.Roll> rolls = info.rolls();
            int n = Math.min(3, rolls.size());
            int size = n >= 3 ? 5 : 6;
            for (int i = 0; i < n; i++) {
                dannypx.foe.helper.RodPartMax.Roll roll = rolls.get(i);
                String icon = dannypx.foe.helper.RodPartMax.iconFor(roll.kind());
                String key = icon + roll.suffix();
                Identifier id = rollIcons.computeIfAbsent(key, k -> Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/roll_" + k));
                // flush with the item's bottom-right corner, inside the slot
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x + 16 - size, y + 16 - size * (i + 1), size, size, CommonColors.WHITE);
                drewMaxIcon |= roll.maxed() || roll.half();
            }
        }
        // With the icons off (or none to show), a purple dot in the top-right corner instead
        // (4x4, from an 8x8 sprite so it stays round).
        if (!marker || drewMaxIcon || info.mark() == dannypx.foe.helper.RodPartMax.Mark.NONE) return;
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, MAX_ROLL_DOT, x + 12, y, 4, 4, CommonColors.WHITE);
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

    /// FOE's rating marker: a small corner in the bottom right of a pet slot, in the rating's colour.
    /// Ported from FOECollab (AGPL-3.0), its pet percent marker.
    private final Identifier ratingMarkerFoe = Identifier.fromNamespaceAndPath(FishOnMCExtras.MOD_ID, "icons/rating_foe");

    /// Package-private for tests: the colour of a pet's rating (RGB): the server's own colour for the rating
    /// word when it has one, otherwise FOE's colour for the rating's percentage (0-1).
    static int ratingColor(Component rating, float percent) {
        if (rating != null) {
            java.util.Optional<Integer> c = rating.visit((style, text) -> !text.isBlank() && style.getColor() != null
                    ? java.util.Optional.of(style.getColor().getValue() & 0xFFFFFF) : java.util.Optional.empty(), net.minecraft.network.chat.Style.EMPTY);
            if (c.isPresent()) return c.get();
        }
        float p = percent * 100f;
        return p <= 20f ? 0x74403B : p < 30f ? 0xFF5555 : p < 40f ? 0xFCFC54 : p < 50f ? 0xFCA800 : p < 60f ? 0x54FC54
                : p < 80f ? 0x00A800 : p < 90f ? 0x54FCFC : p < 100f ? 0xFC54FC : 0xA800A8;
    }

    /// Rating letter (S, A, B...) in the bottom-right of a pet slot, shown at all times like FOECollab; with FOE's
    /// icons, FOE's small corner in the rating's colour instead.
    /// While the inspect key is held, drawPetRating shows the full percentage instead.
    public void drawPetRatingLetter(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        if(!Configs.rendererConfig.showPetRatingLetter.get()) {
            return;
        }

        Pair<Boolean, PetTagObject> validatedPet = ValidateItem.isPet(stack);
        if(validatedPet.value1()) {
            Component ratingComponent = validatedPet.value2().getRatingComponent();
            if (foeIcons()) {
                if (ratingComponent.getString().isBlank() && validatedPet.value2().getTotalPercent() <= 0f) return;
                int rgb = ratingColor(ratingComponent, validatedPet.value2().getTotalPercent());
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ratingMarkerFoe, x, y, 16, 16, 0xFF000000 | rgb);
                return;
            }
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
