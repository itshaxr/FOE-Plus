package dannypx.foe.placeholder.registry;

import com.google.gson.*;
import dannypx.foe.config.Configs;
import dannypx.foe.handler.fetch.*;
import dannypx.foe.handler.logic.*;
import dannypx.foe.handler.store.*;
import dannypx.foe.helper.KeyBindHelper;
import dannypx.foe.helper.TextHelper;
import dannypx.foe.item.FishTagObject;
import dannypx.foe.item.PetTagObject;
import dannypx.foe.item.TagObject;
import dannypx.foe.item.ValidateItem;
import dannypx.foe.placeholder.evaluator.PlaceholderColorCodes;
import dannypx.foe.placeholder.evaluator.PlaceholderEvaluationException;
import dannypx.foe.placeholder.functions.PlaceholderValue;
import dannypx.foe.type.custom_value.*;
import dannypx.foe.type.tuple.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static dannypx.foe.placeholder.registry.PlaceholderTreeNode.*;

public class PlaceholderRegistry {
    private static final Map<String, PlaceholderTreeNode> ROOTS = new HashMap<>();

    public static void init() {
        //region Placeholders
        register(
                node("boss_bar")
                        .branch(node("location").valueComponent(BossEventContext::getLocation)
                                .description("Returns the current location.")
                        )
                        .branch(node("weather").valueComponent(BossEventContext::getWeather)
                                .description("Returns the current weather.")
                        )
                        .branch(node("time").valueComponent(BossEventContext::getTime)
                                .description("Returns the server time.")
                        )
                        .branch(node("temperature").valueComponent(BossEventContext::getTemperature)
                                .description("Returns the current temperature at location.")
                        )
                        .branch(node("sub_location").valueComponent(BossEventContext::getSubLocation).allowEmpty()
                                .description("Returns the current sub location if available.")
                        )
                        .branch(node("community_goal")
                                .branch(node("current").valueComponent(BossEventContext::getCommunityGoalCurrent)
                                        .description("Returns the current amount of fishes caught for the community goal.")
                                )
                                .branch(node("max").valueComponent(BossEventContext::getCommunityGoalMax)
                                        .description("Returns the amount needed of fishes caught for the community goal.")
                                )
                        )
        );

        register(
                node("player")
                        .branch(node("name").valueString(PlayerContext::getName)
                                .description("Returns the player name.")
                        )
                        .branch(node("level").valueNumber(PlayerContext::getLevel)
                                .description("Returns the player fishing level.")
                        )
                        .branch(node("level_progress").valueNumber(PlayerContext::getLevelProgress)
                                .description("Returns the player fishing percentage to next fishing level.")
                        )
                        .branch(node("pos")
                                .branch(node("x").valueNumber(PlayerContext::getPosX)
                                        .description("Returns the player x coordinate")
                                )
                                .branch(node("y").valueNumber(PlayerContext::getPosY)
                                        .description("Returns the player y coordinate")
                                )
                                .branch(node("z").valueNumber(PlayerContext::getPosZ)
                                        .description("Returns the player z coordinate")
                                )
                        )
                        .branch(node("fps").valueNumber(PlayerContext::getFps)
                                .description("Returns the screens FPS.")
                        )
                        .branch(node("is_fishing").valueBoolean(PlayerContext::isFishing)
                                .description("Returns whether the player is fishing.")
                        )
        );

        register(
                node("scoreboard")
                        .branch(node("level").valueComponent(ScoreboardContext::getLevel)
                                .description("Returns the player fishing level (Formatted).")
                        )
                        .branch(node("wallet").valueNumber(ScoreboardContext::getWallet)
                                .description("Returns the players money balance.")
                        )
                        .branch(node("credits").valueNumber(ScoreboardContext::getCredits)
                                .description("Returns the players credit balance.")
                        )
                        .branch(node("catches").valueNumber(ScoreboardContext::getCatches)
                                .description("Returns the players total catches.")
                        )
                        .branch(node("location_min").valueNumber(ScoreboardContext::getLocationMin)
                                .description("Returns the unique fish caught of the current location.")
                        )
                        .branch(node("location_max").valueNumber(ScoreboardContext::getLocationMax)
                                .description("Returns the unique fish amount needed of the current location.")
                        )
                        .branch(node("catch_rate").valueString(ScoreboardContext::getCatchRate)
                                .description("Returns the players catch rate.")
                        )
                        .branch(node("crew").valueString(ScoreboardContext::getCrew)
                                .description("Returns the current crew name if available.")
                        )
                        .branch(node("crew_nearby").valueComponent(ScoreboardContext::getCrewNearby)
                                .description("Returns whether the player is near a crew member.")
                        )
                        .branch(node("version").valueString(ScoreboardContext::getVersion)
                                .description("Returns the server version.")
                        )
                        .branch(node("date").valueString(ScoreboardContext::getDate)
                                .description("Returns the server date.")
                        )
        );

        register(
                node("tab")
                        .branch(node("player_name").valueComponent(TabOverlayContext::getPlayerName)
                                .description("Returns the player name and rank.")
                        )
                        .branch(node("instance").valueString(TabOverlayContext::getInstance)
                                .description("Returns the instance lobby number.")
                        )
                        .branch(node("is_in_instance").valueBoolean(TabOverlayContext::getIsInInstance)
                                .description("Returns whether the player is in a fishing instance.")
                        )
        );

        register(
                node("title")
                        .branch(node("title").valueComponent(TitleContext::getTitle)
                                .description("Returns the last title shown.")
                        )
                        .branch(node("subtitle").valueComponent(TitleContext::getSubTitle)
                                .description("Returns the last subtitle shown.")
                        )
        );

        register(
                node("connection")
                        .branch(node("is_on_server").valueBoolean(ConnectionContext::getIsOnServer)
                                .description("Returns whether the player is on the server.")
                        )
                        .branch(node("was_on_server").valueBoolean(ConnectionContext::getWasOnServer)
                                .description("Returns whether the player joined the server at least once.")
                        )
        );

        register(
                node("inventory")
                        .branch(node("empty_slots").valueNumber(InventoryContext::getEmptySlots)
                                .description("Returns the number of empty slots in inventory.")
                        )
                        .branch(node("fishing_rod")
                                .branch(node("line")
                                        .branch(node("name").valueComponent(InventoryContext::getFishingRodLineName)
                                                .description("Returns the name of the fishing line.")
                                        )
                                        .branch(node("lore").branch(nodeIndex().valueComponent(InventoryContext::getFishingRodLineLore))
                                                .description("Returns the lore line of the specified index.")
                                        )
                                        .branch(nodeStringArray().value(InventoryContext::getFishingRodLineNbt)
                                                .description("Returns the custom data NBT value of the specified values.")
                                        )
                                )
                                .branch(node("reel")
                                        .branch(node("name").valueComponent(InventoryContext::getFishingRodReelName)
                                                .description("Returns the name of the fishing reel.")
                                        )
                                        .branch(node("lore").branch(nodeIndex().valueComponent(InventoryContext::getFishingRodReelLore))
                                                .description("Returns the lore line of the specified index.")
                                        )
                                        .branch(nodeStringArray().value(InventoryContext::getFishingRodReelNbt)
                                                .description("Returns the custom data NBT value of the specified values.")
                                        )
                                )
                                .branch(node("pole")
                                        .branch(node("name").valueComponent(InventoryContext::getFishingRodPoleName)
                                                .description("Returns the name of the fishing pole.")
                                        )
                                        .branch(node("lore").branch(nodeIndex().valueComponent(InventoryContext::getFishingRodPoleLore))
                                                .description("Returns the lore line of the specified index.")
                                        )
                                        .branch(nodeStringArray().value(InventoryContext::getFishingRodPoleNbt)
                                                .description("Returns the custom data NBT value of the specified values.")
                                        )
                                )
                                .branch(node("name").valueComponent(InventoryContext::getFishingRodName)
                                        .description("Returns the name of the fishing rod.")
                                )
                                .branch(node("lore").branch(nodeIndex().valueComponent(InventoryContext::getFishingRodLore))
                                        .description("Returns the lore line of the specified index.")
                                )
                                .branch(nodeStringArray().value(InventoryContext::getFishingRodNbt)
                                        .description("Returns the custom data NBT value of the specified values.")
                                )
                        )
                        .branch(node("pet")
                                .branch(node("name").valueComponent(InventoryContext::getPetName)
                                        .description("Returns the name of the current equipped pet.")
                                )
                                .branch(node("level").valueNumber(InventoryContext::getPetLevel)
                                        .description("Returns the level of the current equipped pet.")
                                )
                                .branch(node("level_progress").valueNumber(InventoryContext::getPetLevelProgress)
                                        .description("Returns the level progress to the next level of the current equipped pet.")
                                )
                                .branch(node("rating").valueComponent(InventoryContext::getPetRating)
                                        .description("Returns the rating of the current equipped pet.")
                                )
                                .branch(node("rating_percent").valueNumber(InventoryContext::getPetRatingPercent)
                                        .description("Returns the rating percentage of the current equipped pet.")
                                )
                                .branch(node("rarity").valueComponent(InventoryContext::getPetRarity)
                                        .description("Returns the rarity of the current equipped pet.")
                                )
                                .branch(node("location_luck_percent").valueNumber(InventoryContext::getPetLocationLuckPercent)
                                        .description("Returns the location luck percentage of the equipped pet.")
                                )
                                .branch(node("location_scale_percent").valueNumber(InventoryContext::getPetLocationScalePercent)
                                        .description("Returns the location scale percentage of the equipped pet.")
                                )
                                .branch(node("climate_luck_percent").valueNumber(InventoryContext::getPetClimateLuckPercent)
                                        .description("Returns the climate luck percentage of the equipped pet.")
                                )
                                .branch(node("climate_scale_percent").valueNumber(InventoryContext::getPetClimateScalePercent)
                                        .description("Returns the climate scale percentage of the equipped pet.")
                                )
                                .branch(node("location_luck").valueNumber(InventoryContext::getPetLocationLuck)
                                        .description("Returns the location luck value of the equipped pet.")
                                )
                                .branch(node("location_scale").valueNumber(InventoryContext::getPetLocationScale)
                                        .description("Returns the location scale value of the equipped pet.")
                                )
                                .branch(node("climate_luck").valueNumber(InventoryContext::getPetClimateLuck)
                                        .description("Returns the climate luck value of the equipped pet.")
                                )
                                .branch(node("climate_scale").valueNumber(InventoryContext::getPetClimateScale)
                                        .description("Returns the slimate scale value of the equipped pet.")
                                )
                                .branch(node("lore").branch(nodeIndex().valueComponent(InventoryContext::getPetLore))
                                        .description("Returns the lore line of the specified index.")
                                )
                                .branch(nodeStringArray().value(InventoryContext::getPetNbt)
                                        .description("Returns the custom data NBT value of the specified values.")
                                )
                        )
                        .branch(node("armor")
                                .branch(node("chestplate")
                                        .branch(node("name").valueComponent(InventoryContext::getChestplateName)
                                                .description("Returns the name of the equipped chestplate.")
                                        )
                                        .branch(node("lore").branch(nodeIndex().valueComponent(InventoryContext::getChestplateLore))
                                                .description("Returns the lore line of the specified index.")
                                        )
                                        .branch(nodeStringArray().value(InventoryContext::getChestplateNbt)
                                                .description("Returns the custom data NBT value of the specified values.")
                                        )
                                )
                                .branch(node("leggings")
                                        .branch(node("name").valueComponent(InventoryContext::getLeggingsName)
                                                .description("Returns the name of the equipped leggings.")
                                        )
                                        .branch(node("lore").branch(nodeIndex().valueComponent(InventoryContext::getLeggingsLore))
                                                .description("Returns the lore line of the specified index.")
                                        )
                                        .branch(nodeStringArray().value(InventoryContext::getLeggingsNbt)
                                                .description("Returns the custom data NBT value of the specified values.")
                                        )
                                )
                                .branch(node("boots")
                                        .branch(node("name").valueComponent(InventoryContext::getBootsName)
                                                .description("Returns the name of the equipped leggings.")
                                        )
                                        .branch(node("lore").branch(nodeIndex().valueComponent(InventoryContext::getBootsLore))
                                                .description("Returns the lore line of the specified index.")
                                        )
                                        .branch(nodeStringArray().value(InventoryContext::getBootsNbt)
                                                .description("Returns the custom data NBT value of the specified values.")
                                        )
                                )
                        )
                        .branch(node("held_item")
                                .branch(node("name").valueComponent(InventoryContext::getHeldItemName)
                                        .description("Returns the name of the current held item.")
                                )
                                .branch(node("lore").branch(nodeIndex().valueComponent(InventoryContext::getHeldItemLore))
                                        .description("Returns the lore line of the specified index.")
                                )
                                .branch(nodeStringArray().value(InventoryContext::getHeldItemNbt)
                                        .description("Returns the custom data NBT value of the specified values.")
                                )
                        )
                        .branch(node("slot")
                                .branch(nodeIndex()
                                        .branch(node("name").valueComponent(InventoryContext::getSlotName)
                                                .description("Returns the name of the item in the specified index slot.")
                                        )
                                        .branch(node("lore").branch(nodeIndex().valueComponent(InventoryContext::getSlotLore))
                                                .description("Returns the lore line of the specified index.")
                                        )
                                        .branch(nodeStringArray().value(InventoryContext::getSlotNbt)
                                                .description("Returns the custom data NBT value of the specified values.")
                                        )
                                )
                        )
        );

        register(node("key_bind")
                .branch(node("open_main").valueString(KeyBindContext::getOpenMainKeyBind)
                        .description("Returns the key bind of opening the main FOE+ screen.")
                )
                .branch(node("inspect").valueString(KeyBindContext::getInspectKeyBind)
                        .description("Returns the key bind of inspecting items if available.")
                )
        );

        register(node("loading")
                .branch(node("is_loading_done").valueBoolean(LoadingContext::getIsLoadingDone)
                        .description("Returns whether the mod is loaded.")
                )
                .branch(node("is_error").valueBoolean(LoadingContext::getIsError)
                        .description("Returns whether the mod has an error during loading.")
                )
        );

        register(node("hit_result")
                .branch(node("block")
                        .branch(node("name").valueComponent(HitResultContext::getBlockName)
                                .description("Returns the name of the block at the crosshair.")
                        )
                )
                .branch(node("entity")
                        .branch(node("name").valueComponent(HitResultContext::getEntityName)
                                .description("Returns the name of the entity at the crosshair.")
                        )
                )
                .branch(node("item_frame")
                        .branch(node("name").valueComponent(HitResultContext::getItemFromItemFrameName)
                                .description("Returns the name of the item in the item frame at the crosshair if available.")
                        )
                )
        );

        register(node("network")
                .branch(node("ping").valueNumber(NetworkContext::getPing)
                        .description("Returns the client ping to the server.")
                )
        );

        register(node("crew")
                .branch(node("online")
                        .branch(nodeIndex()
                                .branch(node("name").valueString(CrewContext::getOnlineName)
                                        .description("Returns the name of the online crew member at the specified index.")
                                )
                                .branch(node("id").valueString(CrewContext::getOnlineId)
                                        .description("Returns the UUID of the online crew member at the specified index.")
                                )
                        )
                )
                .branch(node("offline")
                        .branch(nodeIndex()
                                .branch(node("name").valueString(CrewContext::getOfflineName)
                                        .description("Returns the name of the offline crew member at the specified index.")
                                )
                                .branch(node("id").valueString(CrewContext::getOfflineId)
                                        .description("Returns the UUID of the offline crew member at the specified index.")
                                )
                        )
                )
                .branch(node("is_crew_nearby").valueBoolean(CrewContext::getIsCrewNearby) //TODO Use icons
                        .description("Returns whether the player is near a crew member. (Quick version)")
                )
        );

        register(node("chat")
                .branch(node("trigger").branch(nodeString().valueComponent(ChatContext::getStoredChatTrigger))
                        .description("Returns the last stored message from the specified chat trigger.")
                )
        );

        register(node("timer")
                .branch(nodeString()
                        .branch(node("timer").valueNumber(TimerContext::getTimer)
                                .description("Returns the timer in seconds from the specified timer.")
                        )
                        .branch(node("offset").valueNumber(TimerContext::getOffset)
                                .description("Returns the offset in seconds from the specified timer.")
                        )
                        .branch(node("notification_to_trigger").valueString(TimerContext::getNotificationToTrigger)
                                .description("Returns the notifications to trigger from the specified timer.")
                        )
                        .branch(node("clean_up_chat_trigger").valueString(TimerContext::getCleanUpChatTrigger)
                                .description("Returns the chat triggers to clean from the specified timer.")
                        )
                        .branch(node("use_timer").valueBoolean(TimerContext::getIsUseTimer)
                                .description("Returns whether the specified timer is active.")
                        )
                        .branch(node("is_period").valueBoolean(TimerContext::getIsPeriod)
                                .description("Returns whether the specified timer is in period mode.")
                        )
                        .branch(node("off_timer").valueNumber(TimerContext::getOffTimer)
                                .description("Returns the timer when off in seconds from the specified timer.")
                        )
                        .branch(node("notification_to_trigger_end").valueString(TimerContext::getNotificationToTriggerEnd)
                                .description("Returns the the notifications to trigger when off from the specified timer.")
                        )
                        .branch(node("time")
                                .branch(node("second").valueString(TimerContext::getTimeSecond)
                                        .description("Returns the second hand from the time from the specified timer.")
                                )
                                .branch(node("minute").valueString(TimerContext::getTimeMinute)
                                        .description("Returns the minute hand from the time from the specified timer.")
                                )
                                .branch(node("hour").valueNumber(TimerContext::getTimeHour)
                                        .description("Returns the hour hand from the time from the specified timer.")
                                )
                                .branch(node("on")
                                        .branch(node("second").valueString(TimerContext::getOnTimeSecond)
                                                .description("Returns the second hand in the on period from the time from the specified timer.")
                                        )
                                        .branch(node("minute").valueString(TimerContext::getOnTimeMinute)
                                                .description("Returns the minute hand in the on period from the time from the specified timer.")
                                        )
                                        .branch(node("hour").valueNumber(TimerContext::getOnTimeHour)
                                                .description("Returns the hour hand in the on period from the time from the specified timer.")
                                        )
                                )
                                .branch(node("off")
                                        .branch(node("second").valueString(TimerContext::getOffTimeSecond)
                                                .description("Returns the second hand in the off period from the time from the specified timer.")
                                        )
                                        .branch(node("minute").valueString(TimerContext::getOffTimeMinute)
                                                .description("Returns the minute hand in the off period from the time from the specified timer.")
                                        )
                                        .branch(node("hour").valueNumber(TimerContext::getOffTimeHour)
                                                .description("Returns the hour hand in the off period from the time from the specified timer.")
                                        )
                                )
                        )
                        .branch(node("is_on").valueBoolean(TimerContext::getIsOn)
                                .description("Returns whether the period timer is currently in on mode.")
                        )
                        .branch(node("is_off").valueBoolean(TimerContext::getIsOff)
                                .description("Returns whether the period timer is currently in off mode.")
                        )
                )
        );

        register(node("catch")
                .branch(node("last_caught")
                        .branch(node("fish")
                                .branch(node("name").valueComponent(CatchContext::getLastCaughtFishName)
                                        .description("Returns the name of the last caught fish.")
                                )
                                .branch(node("rarity")
                                        .branch(node("id").valueString(CatchContext::getLastCaughtFishRarityName)
                                                .description("Returns the rarity id of the last caught fish.")
                                        )
                                        .branch(node("icon").valueComponent(CatchContext::getLastCaughtFishRarityIcon)
                                                .description("Returns the rarity of the last caught fish.")
                                        )
                                        .branch(node("last_drystreak").valueNumber(CatchContext::getLastCaughtFishRarityDryStreak)
                                                .description("Returns the rarity drystreak of the last caught fish.")
                                        )
                                )
                                .branch(node("variant")
                                        .branch(node("id").valueString(CatchContext::getLastCaughtFishVariantName)
                                                .description("Returns the variant id of the last caught fish.")
                                        )
                                        .branch(node("icon").valueComponent(CatchContext::getLastCaughtFishVariantIcon)
                                                .description("Returns the variant of the last caught fish.")
                                        )
                                        .branch(node("last_drystreak").valueNumber(CatchContext::getLastCaughtFishVariantDryStreak)
                                                .description("Returns the variant drystreak of the last caught fish.")
                                        )
                                )
                                .branch(node("size")
                                        .branch(node("id").valueString(CatchContext::getLastCaughtFishSizeName)
                                                .description("Returns the fish size id of the last caught fish.")
                                        )
                                        .branch(node("icon").valueComponent(CatchContext::getLastCaughtFishSizeIcon)
                                                .description("Returns the fish size of the last caught fish.")
                                        )
                                        .branch(node("last_drystreak").valueNumber(CatchContext::getLastCaughtFishSizeDryStreak)
                                                .description("Returns the fish size drystreak of the last caught fish.")
                                        )
                                )
                                .branch(node("lore").branch(nodeIndex().valueComponent(CatchContext::getLastCaughtFishLore))
                                        .description("Returns the lore line of the specified index.")
                                )
                                .branch(nodeStringArray().value(CatchContext::getLastCaughtFishNbt)
                                        .description("Returns the custom data NBT value of the specified values.")
                                )
                        )
                        .branch(node("pet")
                                .branch(node("name").valueComponent(CatchContext::getLastCaughtPetName)
                                        .description("Returns the name of the last caught pet.")
                                )
                                .branch(node("rarity")
                                        .branch(node("id").valueString(CatchContext::getLastCaughtPetRarityName)
                                                .description("Returns the rarity id of the last caught pet.")
                                        )
                                        .branch(node("icon").valueComponent(CatchContext::getLastCaughtPetRarityIcon)
                                                .description("Returns the rarity of the last caught pet.")
                                        )
                                        .branch(node("last_drystreak").valueNumber(CatchContext::getLastCaughtPetRarityDryStreak)
                                                .description("Returns the rarity drystreak of the last caught pet.")
                                        )
                                ).branch(node("rating")
                                        .branch(node("id").valueString(CatchContext::getLastCaughtPetRatingName)
                                                .description("Returns the rating id of the last caught pet.")
                                        )
                                        .branch(node("icon").valueComponent(CatchContext::getLastCaughtPetRatingIcon)
                                                .description("Returns the rating of the last caught pet.")
                                        )
                                        .branch(node("last_drystreak").valueNumber(CatchContext::getLastCaughtPetRatingDryStreak)
                                                .description("Returns the rating drystreak of the last caught pet.")
                                        )
                                )
                                .branch(node("lore").branch(nodeIndex().valueComponent(CatchContext::getLastCaughtPetLore))
                                        .description("Returns the lore line of the specified index.")
                                )
                                .branch(nodeStringArray().value(CatchContext::getLastCaughtPetNbt)
                                        .description("Returns the custom data NBT value of the specified values.")
                                )
                        )
                        .branch(node("item")
                                .branch(nodeIndex()
                                        .branch(node("name").valueComponent(CatchContext::getLastCaughtItemName)
                                                .description("Returns the name of the last caught item at the specified index.")
                                        )
                                        .branch(node("amount").valueNumber(CatchContext::getLastCaughtItemStackAmount)
                                                .description("Returns the amount of the last caught item at the specified index.")
                                        )
                                        .branch(node("id").valueString(CatchContext::getLastCaughtItemId)
                                                .description("Returns the item id of the last caught item at the specified index.")
                                        )
                                        .branch(node("last_drystreak").valueNumber(CatchContext::getLastCaughtItemDryStreak)
                                                .description("Returns the item drystreak of the last caught item at the specified index.")
                                        )
                                        .branch(node("lore").branch(nodeIndex().valueComponent(CatchContext::getLastCaughtItemLore))
                                                .description("Returns the lore line of the specified index.")
                                        )
                                        .branch(nodeStringArray().value(CatchContext::getLastCaughtItemNbt)
                                                .description("Returns the custom data NBT value of the specified values.")
                                        )
                                )
                        )
                )
        );

        register(node("quest")
                .branch(node("last_rewarded")
                        .branch(node("pet")
                                .branch(node("name").valueComponent(QuestContext::getLastRewardedPetName)
                                        .description("Returns the name of the last rewarded pet.")
                                )
                                .branch(node("level").valueNumber(QuestContext::getLastRewardedPetLevel)
                                        .description("Returns the level of the last rewarded pet.")
                                )
                                .branch(node("level_progress").valueNumber(QuestContext::getLastRewardedPetLevelProgress)
                                        .description("Returns the level progress to the next level of the last rewarded pet.")
                                )
                                .branch(node("rating").valueComponent(QuestContext::getLastRewardedPetRating)
                                        .description("Returns the rating of the last rewarded pet.")
                                )
                                .branch(node("rating_percent").valueNumber(QuestContext::getLastRewardedPetRatingPercent)
                                        .description("Returns the rating percentage of the last rewarded pet.")
                                )
                                .branch(node("rarity").valueComponent(QuestContext::getLastRewardedPetRarity)
                                        .description("Returns the rarity of the last rewarded pet.")
                                )
                                .branch(node("location_luck_percent").valueNumber(QuestContext::getLastRewardedPetLocationLuckPercent)
                                        .description("Returns the location luck percentage of the last rewarded pet.")
                                )
                                .branch(node("location_scale_percent").valueNumber(QuestContext::getLastRewardedPetLocationScalePercent)
                                        .description("Returns the location scale percentage of the last rewarded pet.")
                                )
                                .branch(node("climate_luck_percent").valueNumber(QuestContext::getLastRewardedPetClimateLuckPercent)
                                        .description("Returns the climate luck percentage of the last rewarded pet.")
                                )
                                .branch(node("climate_scale_percent").valueNumber(QuestContext::getLastRewardedPetClimateScalePercent)
                                        .description("Returns the climate scale percentage of the last rewarded pet.")
                                )
                                .branch(node("location_luck").valueNumber(QuestContext::getLastRewardedPetLocationLuck)
                                        .description("Returns the location luck value of the last rewarded pet.")
                                )
                                .branch(node("location_scale").valueNumber(QuestContext::getLastRewardedPetLocationScale)
                                        .description("Returns the location scale value of the last rewarded pet.")
                                )
                                .branch(node("climate_luck").valueNumber(QuestContext::getLastRewardedPetClimateLuck)
                                        .description("Returns the climate luck value of the last rewarded pet.")
                                )
                                .branch(node("climate_scale").valueNumber(QuestContext::getLastRewardedPetClimateScale)
                                        .description("Returns the climate scale value of the last rewarded pet.")
                                )
                                .branch(node("lore").branch(nodeIndex().valueComponent(QuestContext::getLastRewardedPetLore))
                                        .description("Returns the lore line of the specified index.")
                                )
                                .branch(nodeStringArray().value(QuestContext::getLastRewardedPetNbt)
                                        .description("Returns the custom data NBT value of the specified values.")
                                )
                        )
                        .branch(node("item")
                                .branch(nodeIndex()
                                        .branch(node("name").valueComponent(QuestContext::getLastRewardedItemName)
                                                .description("Returns the name of the last rewarded item at the specified index.")
                                        )
                                        .branch(node("rarity").valueComponent(QuestContext::getLastRewardedItemRarity)
                                                .description("Returns the rarity of the last rewarded item at the specified index.")
                                        )
                                        .branch(node("amount").valueNumber(QuestContext::getLastRewardedItemAmount)
                                                .description("Returns the amount of the last rewarded item at the specified index.")
                                        )
                                        .branch(node("lore").branch(nodeIndex().valueComponent(QuestContext::getLastRewardedItemLore))
                                                .description("Returns the lore line of the specified index.")
                                        )
                                        .branch(nodeStringArray().value(QuestContext::getLastRewardedItemNbt)
                                                .description("Returns the custom data NBT value of the specified values.")
                                        )
                                )
                        )
                )
        );

        register(node("screen")
                .branch(node("last_screen").valueComponent(ScreenContext::getLastScreen)
                        .description("Returns the name of the last screen visited.")
                )
        );

        register(node("chummer")
                .branch(node("is_chummer_nearby").valueBoolean(ChummerContext::hasChummerNearby)
                        .description("Returns whether there is a chummer nearby.")
                )
                .branch(node("is_in_range").valueBoolean(ChummerContext::isInRange)
                        .description("Returns whether the player is in the chummer range.")
                )
                .branch(node("rarity").valueString(ChummerContext::getRarity)
                        .description("Returns the chummer rarity.")
                )
                .branch(node("player").valueString(ChummerContext::getPlayer)
                        .description("Returns the player who placed the chummer.")
                )
                .branch(node("time")
                        .branch(node("minute").valueNumber(ChummerContext::getTimeMinutes)
                                .description("Returns the minute hand of the time left of the chummer.")
                        )
                        .branch(node("second").valueNumber(ChummerContext::getTimeSeconds)
                                .description("Returns the second hand of the time left of the chummer.")
                        )
                )
        );

        register(node("constant_data")
                .branch(node("data")
                        .branch(node("fish")
                                .branch(node("variant").branch(nodeString().valueComponent(ConstantDataContext::getFishVariant))
                                        .description("Returns the variant from the specified id.")
                                )
                                .branch(node("rarity").branch(nodeString().valueComponent(ConstantDataContext::getFishRarity))
                                        .description("Returns the rarity from the specified id.")
                                )
                                .branch(node("size").branch(nodeString().valueComponent(ConstantDataContext::getFishSize))
                                        .description("Returns the fish size from the specified id.")
                                )
                        )
                        .branch(node("pet")
                                .branch(node("rarity").branch(nodeString().valueComponent(ConstantDataContext::getPetRarity))
                                        .description("Returns the rarity from the specified id.")
                                )
                                .branch(node("rating").branch(nodeString().valueComponent(ConstantDataContext::getPetRating))
                                        .description("Returns the pet rating from the specified id.")
                                )
                        )
                )
        );

        register(node("profile_data")
                .branch(node("data")
                        .branch(node("active_pet_slot").valueNumber(ProfileDataContext::getActivePetSlot)
                                .description("Returns the slot number of the active pet.")
                        )
                        .branch(node("has_imported_stats").valueBoolean(ProfileDataContext::getHasImportedStats)
                                .description("Returns whether the player stats are imported at least once.")
                        )
                        .branch(node("has_imported_crew").valueBoolean(ProfileDataContext::getHasImportedCrew)
                                .description("Returns whether the crew data are imported at least once.")
                        )
                        .branch(node("is_in_crew_chat").valueBoolean(ProfileDataContext::getIsInCrewChat)
                                .description("Returns whether the player has crew chat activated.")
                        )
                        .branch(node("tournament_contribution").valueBoolean(ProfileDataContext::getTournamentContribution)
                                .description("Returns whether the player is contributing to the tournament.")
                        )
                )
        );

        register(node("quest_data")
                .branch(node("data")
                        .branch(nodeIndex()
                                .branch(node("goal").valueComponent(QuestDataContext::getGoal)
                                        .description("Returns the goal of the quest at the specified index slot.")
                                )
                                .branch(node("max").valueNumber(QuestDataContext::getMax)
                                        .description("Returns the amount needed for the goal of the quest at the specified index slot.")
                                )
                                .branch(node("current").valueNumber(QuestDataContext::getCurrent)
                                        .description("Returns the current amount towards the goal of the quest at the specified index slot.")
                                )
                                .branch(node("has_quest").valueBoolean(QuestDataContext::getHasQuest)
                                        .description("Returns whether the start at the specified index has a quest.")
                                )
                        )
                )
        );

        register(node("stats_data")
                .branch(node("data")
                        .branch(node("fish")
                                .branch(node("total").valueNumber(StatsDataContext::getFishTotal)
                                        .description("Returns the total fish caught.")
                                )
                                .branch(node("rarity")
                                        .branch(nodeString()
                                                .branch(node("count").valueNumber(StatsDataContext::getFishRarityCount)
                                                        .description("Returns the amount caught of the specified rarity.")
                                                )
                                                .branch(node("dry_streak").valueNumber(StatsDataContext::getFishRarityDryStreak)
                                                        .description("Returns the dry streak of the specified rarity.")
                                                )
                                        )
                                )
                                .branch(node("size")
                                        .branch(nodeString()
                                                .branch(node("count").valueNumber(StatsDataContext::getFishSizeCount)
                                                        .description("Returns the amount caught of the specified fish size.")
                                                )
                                                .branch(node("dry_streak").valueNumber(StatsDataContext::getFishSizeDryStreak)
                                                        .description("Returns the dry streak of the specified fish size.")
                                                )
                                        )
                                )
                                .branch(node("variant")
                                        .branch(nodeString()
                                                .branch(node("count").valueNumber(StatsDataContext::getFishVariantCount)
                                                        .description("Returns the amount caught of the specified variant.")
                                                )
                                                .branch(node("dry_streak").valueNumber(StatsDataContext::getFishVariantDryStreak)
                                                        .description("Returns the dry streak of the specified variant.")
                                                )
                                        )
                                )
                        )
                        .branch(node("pet")
                                .branch(node("total").valueNumber(StatsDataContext::getPetTotal)
                                        .description("Returns the total pets caught.")
                                )
                                .branch(node("dry_streak").valueNumber(StatsDataContext::getPetDryStreak)
                                        .description("Returns the dry streak of caught pets.")
                                )
                                .branch(node("rarity")
                                        .branch(nodeString()
                                                .branch(node("count").valueNumber(StatsDataContext::getPetRarityCount)
                                                        .description("Returns the amount caught of the specified rarity.")
                                                )
                                                .branch(node("dry_streak").valueNumber(StatsDataContext::getPetRarityDryStreak)
                                                        .description("Returns the dry streak of the specified rarity.")
                                                )
                                        )
                                )
                                .branch(node("rating")
                                        .branch(nodeString()
                                                .branch(node("count").valueNumber(StatsDataContext::getPetRatingCount)
                                                        .description("Returns the amount caught of the specified pet rating.")
                                                )
                                                .branch(node("dry_streak").valueNumber(StatsDataContext::getPetRatingDryStreak)
                                                        .description("Returns the dry streak of the specified pet rating.")
                                                )
                                        )
                                )
                        )
                        .branch(node("item")
                                .branch(nodeString()
                                        .branch(node("count").valueNumber(StatsDataContext::getItemCount)
                                                .description("Returns the amount caught of the specified item value.")

                                        )
                                        .branch(node("dry_streak").valueNumber(StatsDataContext::getItemDryStreak)
                                                .description("Returns the dry streak of the specified item value.")
                                        )
                                )
                        )
                )
        );

        register(node("crew_data")
                .branch(node("data")
                        .branch(nodeIndex()
                                .branch(node("id").valueString(CrewDataContext::getUuid)
                                        .description("Returns the UUID of the player of the specified index.")
                                )
                                .branch(node("name").valueString(CrewDataContext::getName)
                                        .description("Returns the name of the player of the specified index.")
                                )
                        )
                )
        );

        register(node("tracker_data")
                .branch(node("data")
                        .branch(nodeString()
                                .branch(node("value").value(TrackerDataContext::getValue)
                                        .description("Return the current value of the specified tracker.")
                                )
                                .branch(node("itemstack")
                                        .branch(node("lore").branch(nodeIndex().valueComponent(TrackerDataContext::getItemLore))
                                                .description("Returns the lore line of the specified index.")
                                        )
                                        .branch(nodeStringArray().value(TrackerDataContext::getItemNbt)
                                                .description("Returns the custom data NBT value of the specified values.")
                                        )
                                )
                        )
                )
        );
        //endregion

        //region Boolean Functions
        register(node("condition").evalBoolean(EvaluationContext::evalCondition)
                .description("Returns a boolean from the specified valid condition using the following operators; <, <=, >, >=, ==, !=.")
                .param("condition", DocTypeKind.BOOLEAN)
        );
        register(node("equals_ignore_case").evalBoolean(EvaluationContext::evalEqualsIgnoreCase)
                .description("Returns true if the two specified values are equal ignoring case")
                .param("a", DocTypeKind.VALUE)
                .param("b", DocTypeKind.VALUE)
        );
        register(node("if").evalValue(EvaluationContext::evalConditionIf).allowEmpty()
                .description("Returns the true value or false value based on the specified valid condition using the following operators; <, <=, >, >=, ==, !=.")
                .param("condition", DocTypeKind.BOOLEAN)
                .param("true", DocTypeKind.VALUE)
                .paramOptional("false", DocTypeKind.VALUE)
        );
        register(node("is_blank").evalBoolean(EvaluationContext::evalIsBlank)
                .description("Returns true if the value is empty or contains only white space codepoints, otherwise false.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
        );
        register(node("contains").evalBoolean(EvaluationContext::evalContains)
                .description("Returns true if and only if this value contains the specified search parameter.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("search", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .paramOptional("ignore_case", DocTypeKind.BOOLEAN)
        );
        register(node("ends_with").evalBoolean(EvaluationContext::evalEndsWith)
                .description("Returns true if this value ends with the specified suffix.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("suffix", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .paramOptional("ignore_case", DocTypeKind.BOOLEAN)
        );
        register(node("starts_with").evalBoolean(EvaluationContext::evalStartsWith)
                .description("Returns true if this value starts with the specified prefix.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("prefix", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .paramOptional("ignore_case", DocTypeKind.BOOLEAN)
        );
        register(node("is_infinite").evalBoolean(EvaluationContext::evalIsInfinite)
                .description("Returns true if the specified number is infinitely large in magnitude.")
                .param("value", DocTypeKind.NUMBER)
        );
        register(node("is_nan").evalBoolean(EvaluationContext::evalIsNaN)
                .description("Returns true if the specified number is a Not-a-Number (NaN) value.")
                .param("value", DocTypeKind.NUMBER)
        );
        register(node("is_number").evalBoolean(EvaluationContext::evalIsNumber)
                .description("Returns true if the specified value is a number.")
                .param("value", DocTypeKind.VALUE)
        );
        register(node("is_string").evalBoolean(EvaluationContext::evalIsString)
                .description("Returns true if the specified value is a string.")
                .param("value", DocTypeKind.VALUE)
        );
        register(node("is_component").evalBoolean(EvaluationContext::evalIsComponent)
                .description("Returns true if the specified value is a component.")
                .param("value", DocTypeKind.VALUE)
        );
        register(node("is_boolean").evalBoolean(EvaluationContext::evalIsBoolean)
                .description("Returns true if the specified value is a boolean.")
                .param("value", DocTypeKind.VALUE)
        );
        register(node("between").evalBoolean(EvaluationContext::evalBetween)
                .description("Returns whether the specified value is betwen min and max (inclusive).")
                .param("value", DocTypeKind.NUMBER)
                .param("min", DocTypeKind.NUMBER)
                .param("max", DocTypeKind.NUMBER)
        );
        register(node("one_of").evalBoolean(EvaluationContext::evalOneOf)
                .description("Returns whether the specified values matches any of the candidates.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .paramVariadic("candidate", DocTypeKind.STRING, DocTypeKind.COMPONENT)
        );
        register(node("or").evalBoolean(EvaluationContext::evalOr)
                .description("Returns true if at least one value is true.")
                .paramVariadic("value", DocTypeKind.BOOLEAN)
        );
        register(node("and").evalBoolean(EvaluationContext::evalAnd)
                .description("Returns true if all values is true.")
                .paramVariadic("value", DocTypeKind.BOOLEAN)
        );
        register(node("not").evalBoolean(EvaluationContext::evalNot)
                .description("Returns the reverse boolean value.")
                .paramVariadic("value", DocTypeKind.BOOLEAN)
        );
        register(node("xor").evalBoolean(EvaluationContext::evalXor)
                .description("Returns true if at least one but not all is true. If more than 3 values, returns true if an odd number of values are true.")
                .paramVariadic("value", DocTypeKind.BOOLEAN)
        );
        //endregion

        //region String Manipulation Functions
        register(node("substring").evalValue(EvaluationContext::evalSubstring)
                .description("Returns a value that is a substring of this value. The substring begins with the character at the specified start index and extends to the end of this string, or extends to the specified end index.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("start", DocTypeKind.NUMBER)
                .paramOptional("end", DocTypeKind.BOOLEAN)
        );
        register(node("index_of").evalNumber(EvaluationContext::evalIndexOf)
                .description("Returns the index within this value of the first occurrence of the specified search value, or starting at the specified from_index index.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("search", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .paramOptional("from_index", DocTypeKind.NUMBER)
        );
        register(node("last_index_of").evalNumber(EvaluationContext::evalLastIndexOf)
                .description("Returns the index within this string of the last occurrence of the specified search value, or searching backward starting at the specified from_index index.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("search", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .paramOptional("from_index", DocTypeKind.NUMBER)
        );
        register(node("char_at").evalString(EvaluationContext::evalCharAt)
                .description("Returns the character at the specified index.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("index", DocTypeKind.NUMBER)
        );
        register(node("repeat").evalValue(EvaluationContext::evalRepeat)
                .description("Returns a value whose value is the concatenation of this value repeated count times.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("count", DocTypeKind.NUMBER)
        );
        register(node("uppercase").evalValue(EvaluationContext::evalUppercase)
                .description("Converts all of the characters in this value to upper case.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
        );
        register(node("lowercase").evalValue(EvaluationContext::evalLowercase)
                .description("Converts all of the characters in this value to lower case.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
        );
        register(node("replace").evalValue(EvaluationContext::evalReplace)
                .description("Replaces each target substring inside the value string with the specified replacement string. The replacement proceeds from the beginning of the string to the end.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("target", DocTypeKind.STRING)
                .param("replacement", DocTypeKind.STRING)
        );
        register(node("replace_first").evalValue(EvaluationContext::evalReplaceFirst)
                .description("Replaces the first occurrence of the target substring inside the value string with the specified replacement string.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("target", DocTypeKind.STRING)
                .param("replacement", DocTypeKind.STRING)
        );
        register(node("reverse").evalValue(EvaluationContext::evalReverse)
                .description("Returns the specified value in reverse character order.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
        );
        register(node("join").evalComponent(EvaluationContext::evalJoin)
                .description("Returns the text composed of the values joined together with the specified delimiter.")
                .param("delimiter", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .paramVariadic("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
        );
        register(node("count").evalNumber(EvaluationContext::evalCount)
                .description("Returns the count of specified search text in specified value.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("search", DocTypeKind.STRING, DocTypeKind.COMPONENT)
        );
        register(node("length").evalNumber(EvaluationContext::evalLength)
                .description("Returns the length of the specified value.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
        );
        register(node("shorten_number").evalString(EvaluationContext::evalShortenNumber)
                .description("Returns the value to numeric abbreviations like 1K (1.000), 1M (1.000.000), 1B (1.000.000.000), with up to 2 decimals.")
                .param("value", DocTypeKind.NUMBER)
        );
        register(node("apply_format").evalComponent(EvaluationContext::evalApplyFormat)
                .description("Returns the value with the specified style.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("style", DocTypeKind.STRING)
        );
        register(node("format").evalComponent(EvaluationContext::evalFormat)
                .description("Returns the value with the format. e.g. \"&aTest\".")
                .param("value", DocTypeKind.STRING)
        );
        register(node("remove_format").evalString(EvaluationContext::evalRemoveFormat)
                .description("Returns the plain text of the specified value.")
                .param("value", DocTypeKind.COMPONENT)
        );
        register(node("format_time").evalString(EvaluationContext::evalFormatTime)
                .description("Returns the number with a leading zero if and only if the specified value is one digit.")
                .param("value", DocTypeKind.NUMBER)
        );
        register(node("format_fancy_boolean").evalComponent(EvaluationContext::evalFormatFancyBoolean)
                .description("Returns a prettier version of a boolean. (It is formatted and thus is not a valid boolean as value)")
                .param("value", DocTypeKind.BOOLEAN)
        );
        register(node("trim").evalValue(EvaluationContext::evalTrim)
                .description("Returns a text whose value is this text, with all leading and trailing space removed.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
        );
        register(node("pad_start").evalValue(EvaluationContext::evalPadStart)
                .description("Returns the value padded with the specified character at the start until it reaches the specified length.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("length", DocTypeKind.NUMBER)
                .param("character", DocTypeKind.STRING, DocTypeKind.COMPONENT)
        );
        register(node("pad_end").evalValue(EvaluationContext::evalPadEnd)
                .description("Returns the value padded with the specified character at the end until it reaches the specified length.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .param("length", DocTypeKind.NUMBER)
                .param("character", DocTypeKind.STRING, DocTypeKind.COMPONENT)
        );
        register(node("truncate").evalValue(EvaluationContext::evalTruncate)
                .description("Returns the value shortened to the specified length, appending the suffix if available.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
                .paramOptional("suffix", DocTypeKind.STRING, DocTypeKind.COMPONENT)
        );
        register(node("capitalize").evalValue(EvaluationContext::evalCapitalize)
                .description("Capitalizes a text changing the first character to upper case.")
                .param("value", DocTypeKind.STRING, DocTypeKind.COMPONENT)
        );
        register(node("concat").evalValue(EvaluationContext::evalConcat)
                .description("Returns the concatenated text of all specified values.")
                .paramVariadic("value", DocTypeKind.VALUE)
        );
        //endregion

        //region Math Functions
        register(node("expression").evalNumber(EvaluationContext::evalExpression)
                .description("Returns a number from the specified valid expression using the following operators; +, -, *, /.")
                .param("expression", DocTypeKind.NUMBER)
        );
        register(node("max").evalNumber(EvaluationContext::evalMax)
                .description("Returns the greatest number of all values.")
                .paramVariadic("value", DocTypeKind.NUMBER)
        );
        register(node("min").evalNumber(EvaluationContext::evalMin)
                .description("Returns the smallest number of all values.")
                .paramVariadic("value", DocTypeKind.NUMBER)
        );
        register(node("sum").evalNumber(EvaluationContext::evalSum)
                .description("Returns the sum of all values.")
                .paramVariadic("value", DocTypeKind.NUMBER)
        );
        register(node("avg").evalNumber(EvaluationContext::evalAverage)
                .description("Returns the average of all values.")
                .paramVariadic("value", DocTypeKind.NUMBER)
        );
        register(node("abs").evalNumber(EvaluationContext::evalAbs)
                .description("Returns the absolute value of the specified value.")
                .param("value", DocTypeKind.NUMBER)
        );
        register(node("ceil").evalNumber(EvaluationContext::evalCeil)
                .description("Returns the nearest specified value rounded up to a mathematical integer.")
                .param("value", DocTypeKind.NUMBER)
        );
        register(node("floor").evalNumber(EvaluationContext::evalFloor)
                .description("Returns the nearest specified value rounded down to a mathematical integer.")
                .param("value", DocTypeKind.NUMBER)
        );
        register(node("round").evalNumber(EvaluationContext::evalRound)
                .description("Returns the nearest specified value rounded to the nearest mathematical integer.")
                .param("value", DocTypeKind.NUMBER)
                .paramOptional("decimals", DocTypeKind.NUMBER)
        );
        register(node("mod").evalNumber(EvaluationContext::evalMod)
                .description("Returns the modulus of a mod b.")
                .param("a", DocTypeKind.NUMBER)
                .param("b", DocTypeKind.NUMBER)
        );
        register(node("clamp").evalNumber(EvaluationContext::evalClamp)
                .description("Clamps the value to fit between min and max.")
                .param("value", DocTypeKind.NUMBER)
                .param("min", DocTypeKind.NUMBER)
                .param("max", DocTypeKind.NUMBER)
        );
        register(node("log").evalNumber(EvaluationContext::evalLog)
                .description("Returns the natural logarithm of the specified value.")
                .param("value", DocTypeKind.NUMBER)
        );
        register(node("pow").evalNumber(EvaluationContext::evalPow)
                .description("Returns the value of a raised to the power of b.")
                .param("a", DocTypeKind.NUMBER)
                .param("b", DocTypeKind.NUMBER)
        );
        register(node("sqrt").evalNumber(EvaluationContext::evalSqrt)
                .description("Returns the positive square root of the specified value.")
                .param("value", DocTypeKind.NUMBER)
        );
        register(node("distance").evalNumber(EvaluationContext::evalDistance)
                .description("Returns the distance between two points.")
                .param("x1", DocTypeKind.NUMBER)
                .param("y1", DocTypeKind.NUMBER)
                .param("z1", DocTypeKind.NUMBER)
                .param("x2", DocTypeKind.NUMBER)
                .param("y2", DocTypeKind.NUMBER)
                .param("z2", DocTypeKind.NUMBER)
        );
        register(node("random").evalNumber(EvaluationContext::evalRandom)
                .description("Returns a random number between 0.0 (inclusive) and 1.0 (exclusive).")
        );
        register(node("random_range").evalNumber(EvaluationContext::evalRandomRange)
                .description("Returns a random number between min (inclusive) and max (exclusive).")
                .param("min", DocTypeKind.NUMBER)
                .param("max", DocTypeKind.NUMBER)
        );
        //endregion

        //region Misc
        register(node("type_of").evalString(EvaluationContext::evalTypeOf)
                .description("Returns the type of the specified value.")
                .param("value", DocTypeKind.VALUE)
        );
        register(node("no_hide").evalValue(EvaluationContext::evalNoHide).allowEmpty()
                .description("Returns the value, including blank values without hiding the line.")
                .param("value", DocTypeKind.VALUE)
        );
        register(node("hide_line").evalValue(EvaluationContext::evalHideLine).allowEmpty()
                .description("Hides the full line if and only if the specified should_hide value is true.")
                .param("should_hide", DocTypeKind.BOOLEAN)
        );
        //endregion
    }

    public static void register(PlaceholderTreeNode root) {
        ROOTS.put(root.key(), root);
    }

    public static PlaceholderTreeNode getRoot(String key) {
        return ROOTS.get(key);
    }

    public static List<String> getRootNames() {
        List<String> names = new ArrayList<>(ROOTS.keySet());
        names.sort(String::compareTo);
        return names;
    }

    //region Placeholder Contexts

    static class BossEventContext {
        static MutableComponent getLocation() {
            return BossEventHandler.instance().getLocation();
        }

        static MutableComponent getWeather() {
            return BossEventHandler.instance().getWeather();
        }

        static MutableComponent getTime() {
            return BossEventHandler.instance().getTime();
        }

        static MutableComponent getTemperature() {
            return BossEventHandler.instance().getTemperature();
        }

        static MutableComponent getSubLocation() {
            return BossEventHandler.instance().getSubLocation();
        }

        static MutableComponent getCommunityGoalCurrent() {
            return BossEventHandler.instance().getCommunityGoalCurrent();
        }

        static MutableComponent getCommunityGoalMax() {
            return BossEventHandler.instance().getCommunityGoalMax();
        }
    }

    static class PlayerContext {
        static String getName() {
            return Minecraft.getInstance().player.getName().getString();
        }

        static Number getLevel() {
            return Minecraft.getInstance().player.experienceLevel;
        }

        static Number getLevelProgress() {
            return Minecraft.getInstance().player.experienceProgress * 100;
        }

        static Number getPosX() {
            return Minecraft.getInstance().player.position().x;
        }

        static Number getPosY() {
            return Minecraft.getInstance().player.position().y;
        }

        static Number getPosZ() {
            return Minecraft.getInstance().player.position().z;
        }

        static Number getFps() {
            return Minecraft.getInstance().getFps();
        }

        static Boolean isFishing() {
            return Minecraft.getInstance().player.fishing != null;
        }
    }

    static class ScoreboardContext {
        static MutableComponent getLevel() {
            return ScoreboardHandler.instance().getLevel();
        }

        static Number getWallet() {
            return TextHelper.toIntFromString(ScoreboardHandler.instance().getWallet().getString().substring(1));
        }

        static Number getCredits() {
            return TextHelper.toIntFromString(ScoreboardHandler.instance().getCredits().getString());
        }

        static Number getCatches() {
            return TextHelper.toIntFromString(ScoreboardHandler.instance().getCatches().getString().trim());
        }

        static Number getLocationMin() {
            return Integer.parseInt(ScoreboardHandler.instance().getLocationMin().getString().trim());
        }

        static Number getLocationMax() {
            return Integer.parseInt(ScoreboardHandler.instance().getLocationMax().getString().trim());
        }

        static String getCatchRate() {
            return ScoreboardHandler.instance().getCatchRate().getString();
        }

        static String getCrew() {
            return ScoreboardHandler.instance().getCrew().getString();
        }

        static MutableComponent getCrewNearby() {
            return ScoreboardHandler.instance().isCrewNearby();
        }

        static String getVersion() {
            return ScoreboardHandler.instance().getVersion().getString();
        }

        static String getDate() {
            return ScoreboardHandler.instance().getDate().getString();
        }
    }

    static class TabOverlayContext {
        static MutableComponent getPlayerName() {
            return TabOverlayHandler.instance().getPlayerName().copy();
        }

        static String getInstance() {
            return TabOverlayHandler.instance().getInstance();
        }

        static Boolean getIsInInstance() {
            return TabOverlayHandler.instance().isInInstance();
        }
    }

    static class TitleContext {
        static MutableComponent getTitle() {
            return TitleHandler.instance().getTitle();
        }

        static MutableComponent getSubTitle() {
            return TitleHandler.instance().getSubTitle();
        }
    }

    static class ConnectionContext {
        static Boolean getIsOnServer() {
            return ConnectionHandler.instance().isOnServer();
        }

        static Boolean getWasOnServer() {
            return ConnectionHandler.instance().wasOnServer();
        }
    }

    static class InventoryContext {
        static Number getEmptySlots() {
            return InventoryHandler.instance().getCurrentEmptySlots();
        }

        static MutableComponent getFishingRodName() {
            return InventoryHandler.instance().getCurrentFishingRod().getName().copy();
        }

        static MutableComponent getFishingRodLore(List<String> indices) {
            return getLoreValue(InventoryHandler.instance().getCurrentFishingRod(), indices.getFirst());
        }

        static PlaceholderValue getFishingRodNbt(List<String> indices) {
            return getNbtValue(InventoryHandler.instance().getCurrentFishingRod(), indices);
        }

        static MutableComponent getFishingRodLineName() {
            List<TagObject> lineList = InventoryHandler.instance().getCurrentFishingRod().getLineItem();

            if(!lineList.isEmpty()) {
                return lineList.getFirst().getName().copy();
            }

            return Component.empty();
        }

        static MutableComponent getFishingRodLineLore(List<String> indices) {
            List<TagObject> lineList = InventoryHandler.instance().getCurrentFishingRod().getLineItem();

            if(!lineList.isEmpty()) {
                return getLoreValue(lineList.getFirst(), indices.getFirst());
            }

            return Component.empty();
        }

        static PlaceholderValue getFishingRodLineNbt(List<String> indices) {
            List<TagObject> lineList = InventoryHandler.instance().getCurrentFishingRod().getLineItem();

            if(!lineList.isEmpty()) {
                return getNbtValue(lineList.getFirst(), indices);
            }

            return PlaceholderValue.emptyText();
        }

        static MutableComponent getFishingRodReelName() {
            List<TagObject> reelList = InventoryHandler.instance().getCurrentFishingRod().getReelItem();

            if(!reelList.isEmpty()) {
                return reelList.getFirst().getName().copy();
            }

            return Component.empty();
        }

        static MutableComponent getFishingRodReelLore(List<String> indices) {
            List<TagObject> reelList = InventoryHandler.instance().getCurrentFishingRod().getReelItem();

            if(!reelList.isEmpty()) {
                return getLoreValue(reelList.getFirst(), indices.getFirst());
            }

            return Component.empty();
        }

        static PlaceholderValue getFishingRodReelNbt(List<String> indices) {
            List<TagObject> reelList = InventoryHandler.instance().getCurrentFishingRod().getReelItem();

            if(!reelList.isEmpty()) {
                return getNbtValue(reelList.getFirst(), indices);
            }

            return PlaceholderValue.emptyText();
        }

        static MutableComponent getFishingRodPoleName() {
            List<TagObject> poleList = InventoryHandler.instance().getCurrentFishingRod().getPoleItem();

            if(!poleList.isEmpty()) {
                return poleList.getFirst().getName().copy();
            }

            return Component.empty();
        }

        static MutableComponent getFishingRodPoleLore(List<String> indices) {
            List<TagObject> poleList = InventoryHandler.instance().getCurrentFishingRod().getPoleItem();

            if(!poleList.isEmpty()) {
                return getLoreValue(poleList.getFirst(), indices.getFirst());
            }

            return Component.empty();
        }

        static PlaceholderValue getFishingRodPoleNbt(List<String> indices) {
            List<TagObject> poleList = InventoryHandler.instance().getCurrentFishingRod().getPoleItem();

            if(!poleList.isEmpty()) {
                return getNbtValue(poleList.getFirst(), indices);
            }

            return PlaceholderValue.emptyText();
        }

        static MutableComponent getPetName() {
            return InventoryHandler.instance().getCurrentPet().getName().copy();
        }

        static Number getPetLevel() {
            return InventoryHandler.instance().getCurrentPet().getLevel();
        }

        static Number getPetLevelProgress() {
            return InventoryHandler.instance().getCurrentPet().getProgress();
        }

        static MutableComponent getPetRating() {
            return InventoryHandler.instance().getCurrentPet().getRatingComponent().copy();
        }

        static Number getPetRatingPercent() {
            return InventoryHandler.instance().getCurrentPet().getTotalPercent() * 100;
        }

        static MutableComponent getPetRarity() {
            return InventoryHandler.instance().getCurrentPet().getRarityComponent().copy();
        }

        static Number getPetLocationLuckPercent() {
            return InventoryHandler.instance().getCurrentPet().getLocationPercentMaxLuck() * 100;
        }

        static Number getPetLocationScalePercent() {
            return InventoryHandler.instance().getCurrentPet().getLocationPercentMaxScale() * 100;
        }

        static Number getPetClimateLuckPercent() {
            return InventoryHandler.instance().getCurrentPet().getClimatePercentMaxLuck() * 100;
        }

        static Number getPetClimateScalePercent() {
            return InventoryHandler.instance().getCurrentPet().getClimatePercentMaxScale() * 100;
        }

        static Number getPetLocationLuck() {
            return InventoryHandler.instance().getCurrentPet().getLocationMaxLuck();
        }

        static Number getPetLocationScale() {
            return InventoryHandler.instance().getCurrentPet().getLocationMaxScale();
        }

        static Number getPetClimateLuck() {
            return InventoryHandler.instance().getCurrentPet().getClimateMaxLuck();
        }

        static Number getPetClimateScale() {
            return InventoryHandler.instance().getCurrentPet().getClimateMaxScale();
        }

        static MutableComponent getPetLore(List<String> indices) {
            return getLoreValue(InventoryHandler.instance().getCurrentPet(), indices.getFirst());
        }

        static PlaceholderValue getPetNbt(List<String> indices) {
            return getNbtValue(InventoryHandler.instance().getCurrentPet(), indices);
        }

        static MutableComponent getChestplateName() {
            ItemStack armor = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST);
            Pair<Boolean, TagObject> validatedItem = ValidateItem.isServerItem(armor, true);
            return validatedItem.value1() ? validatedItem.value2().getName().copy() : Component.empty();
        }

        static MutableComponent getChestplateLore(List<String> indices) {
            ItemStack armor = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST);
            return getLoreValue(armor, indices.getFirst());
        }

        static PlaceholderValue getChestplateNbt(List<String> indices) {
            ItemStack armor = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST);
            return getNbtValue(armor, indices);
        }

        static MutableComponent getLeggingsName() {
            ItemStack armor = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.LEGS);
            Pair<Boolean, TagObject> validatedItem = ValidateItem.isServerItem(armor, true);
            return validatedItem.value1() ? validatedItem.value2().getName().copy() : Component.empty();
        }

        static MutableComponent getLeggingsLore(List<String> indices) {
            ItemStack armor = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.LEGS);
            return getLoreValue(armor, indices.getFirst());
        }

        static PlaceholderValue getLeggingsNbt(List<String> indices) {
            ItemStack armor = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.LEGS);
            return getNbtValue(armor, indices);
        }

        static MutableComponent getBootsName() {
            ItemStack armor = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.FEET);
            Pair<Boolean, TagObject> validatedItem = ValidateItem.isServerItem(armor, true);
            return validatedItem.value1() ? validatedItem.value2().getName().copy() : Component.empty();
        }

        static MutableComponent getBootsLore(List<String> indices) {
            ItemStack armor = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.FEET);
            return getLoreValue(armor, indices.getFirst());
        }

        static PlaceholderValue getBootsNbt(List<String> indices) {
            ItemStack armor = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.FEET);
            return getNbtValue(armor, indices);
        }

        static MutableComponent getHeldItemName() {
            return InventoryHandler.instance().getCurrentHeldItem().getName().copy();
        }

        static MutableComponent getHeldItemLore(List<String> indices) {
            return getLoreValue(InventoryHandler.instance().getCurrentHeldItem(), indices.getFirst());
        }

        static PlaceholderValue getHeldItemNbt(List<String> indices) {
            return getNbtValue(InventoryHandler.instance().getCurrentHeldItem(), indices);
        }

        static MutableComponent getSlotName(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());

            if(index >= 0) {
                ItemStack item = Minecraft.getInstance().player.getInventory().getItem(index);
                return item.getHoverName().copy();
            }
            return Component.empty();
        }

        static MutableComponent getSlotLore(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());

            if(index >= 0) {
                ItemStack item = Minecraft.getInstance().player.getInventory().getItem(index);
                return getLoreValue(item, indices.get(1));
            }
            return Component.empty();
        }

        static PlaceholderValue getSlotNbt(List<String> indices) {
            if(indices.size() > 1) {
                int index = Integer.parseInt(indices.getFirst());

                if(index >= 0) {
                    ItemStack item = Minecraft.getInstance().player.getInventory().getItem(index);
                    return getNbtValue(item, indices.subList(1, indices.size()));
                }
            }
            return PlaceholderValue.emptyText();
        }
    }

    static class KeyBindContext {
        static String getOpenMainKeyBind() {
            return KeyBindHelper.getKeyString(Configs.keyBindConfig.openMainKeybind);
        }

        static String getInspectKeyBind() {
            return KeyBindHelper.getKeyString(Configs.keyBindConfig.inspectKeybind);
        }
    }

    static class LoadingContext {
        static Boolean getIsLoadingDone() {
            return LoadingHandler.instance().isLoadingDone();
        }

        static Boolean getIsError() {
            return LoadingHandler.instance().isError();
        }
    }

    static class HitResultContext {
        static MutableComponent getBlockName() {
            return HitResultHandler.instance().getBlockFromHitResult();
        }

        static MutableComponent getEntityName() {
            return HitResultHandler.instance().getEntityHitResult() != null ? HitResultHandler.instance().getEntityHitResult().getEntity().getName().copy() : Component.empty();
        }

        static MutableComponent getItemFromItemFrameName() {
            return HitResultHandler.instance().getItemFrameItem().getItem().getName().copy();
        }
    }

    static class NetworkContext {
        static Number getPing() {
            return NetworkHandler.instance().getPing();
        }
    }

    static class CrewContext {
        static String getOnlineName(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(index >= 0 && index < CrewHandler.instance().getOnlineMembers().size()) {
                return CrewHandler.instance().getOnlineMembers().get(index).value2();
            }
            return "";
        }

        static String getOnlineId(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(index >= 0 && index < CrewHandler.instance().getOnlineMembers().size()) {
                return CrewHandler.instance().getOnlineMembers().get(index).value1().toString();
            }
            return "";
        }

        static String getOfflineName(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(index >= 0 && index < CrewHandler.instance().getOfflineMembers().size()) {
                return CrewHandler.instance().getOnlineMembers().get(index).value2();
            }
            return "";
        }

        static String getOfflineId(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(index >= 0 && index < CrewHandler.instance().getOfflineMembers().size()) {
                return CrewHandler.instance().getOnlineMembers().get(index).value1().toString();
            }
            return "";
        }

        static Boolean getIsCrewNearby() {
            return CrewHandler.instance().isCrewNearby();
        }
    }

    static class ChatContext {
        static MutableComponent getStoredChatTrigger(List<String> indices) {
            return ChatHandler.instance().getStoredChatTriggerComponent().getOrDefault(indices.getFirst(), Component.empty()).copy();
        }
    }

    static class TimerContext {
        static Number getTimer(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer != null) {
                return timer.getTimer();
            }
            return null;
        }

        static Number getOffset(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer != null) {
                return timer.getOffset();
            }
            return null;
        }

        static String getNotificationToTrigger(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer != null) {
                return timer.getNotificationToTrigger();
            }
            return "";
        }

        static String getCleanUpChatTrigger(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer != null) {
                return timer.getNotificationToTrigger();
            }
            return "";
        }

        static Boolean getIsUseTimer(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer != null) {
                return timer.isUseTimer();
            }
            return false;
        }

        static Boolean getIsPeriod(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer != null) {
                return timer.isPeriod();
            }
            return false;
        }

        static Number getOffTimer(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer instanceof CustomTimerDataHandler.CustomTimerPeriod timerPeriod) {
                return timerPeriod.getOffTimer();
            }
            return null;
        }

        static String getNotificationToTriggerEnd(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer instanceof CustomTimerDataHandler.CustomTimerPeriod timerPeriod) {
                return timerPeriod.getNotificationToTriggerEnd();
            }
            return "";
        }

        static String getTimeSecond(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer != null) {
                long timeSeconds = System.currentTimeMillis() / 1000;
                long adjusted = timeSeconds + timer.getOffset();
                long pos = adjusted % timer.getTimer();
                long remaining = timer.getTimer() - pos;

                return String.format(Locale.US, "%02d", Double.valueOf(Math.floor(remaining % 60)).intValue());
            }
            return "";
        }

        static String getTimeMinute(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer != null) {
                long timeSeconds = System.currentTimeMillis() / 1000;
                long adjusted = timeSeconds + timer.getOffset();
                long pos = adjusted % timer.getTimer();
                long remaining = timer.getTimer() - pos;

                return String.format(Locale.US, "%02d", Double.valueOf(Math.floor((double) (remaining % 3600) / 60)).intValue());
            }
            return "";
        }

        static Number getTimeHour(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer != null) {
                long timeSeconds = System.currentTimeMillis() / 1000;
                long adjusted = timeSeconds + timer.getOffset();
                long pos = adjusted % timer.getTimer();
                long remaining = timer.getTimer() - pos;

                return Math.floor((double) remaining / 3600);
            }
            return null;
        }

        static String getOnTimeSecond(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer instanceof CustomTimerDataHandler.CustomTimerPeriod timerPeriod) {
                long cycle = timerPeriod.getTimer() + timerPeriod.getOffTimer();
                long adjusted = System.currentTimeMillis() / 1000 + timerPeriod.getOffset();
                long pos = adjusted % cycle;
                long remaining = pos < timerPeriod.getTimer()
                        ? timerPeriod.getTimer() - pos
                        : (cycle - pos) + timerPeriod.getTimer();

                return String.format(Locale.US, "%02d", Double.valueOf(Math.floor(remaining % 60)).intValue());
            }
            return "";
        }

        static String getOnTimeMinute(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer instanceof CustomTimerDataHandler.CustomTimerPeriod timerPeriod) {
                long cycle = timerPeriod.getTimer() + timerPeriod.getOffTimer();
                long adjusted = System.currentTimeMillis() / 1000 + timerPeriod.getOffset();
                long pos = adjusted % cycle;
                long remaining = pos < timerPeriod.getTimer()
                        ? timerPeriod.getTimer() - pos
                        : (cycle - pos) + timerPeriod.getTimer();

                return String.format(Locale.US, "%02d", Double.valueOf(Math.floor((double) (remaining % 3600) / 60)).intValue());
            }
            return "";
        }

        static Number getOnTimeHour(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer instanceof CustomTimerDataHandler.CustomTimerPeriod timerPeriod) {
                long cycle = timerPeriod.getTimer() + timerPeriod.getOffTimer();
                long adjusted = System.currentTimeMillis() / 1000 + timerPeriod.getOffset();
                long pos = adjusted % cycle;
                long remaining = pos < timerPeriod.getTimer()
                        ? timerPeriod.getTimer() - pos
                        : (cycle - pos) + timerPeriod.getTimer();

                return Math.floor((double) remaining / 3600);
            }
            return null;
        }

        static String getOffTimeSecond(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer instanceof CustomTimerDataHandler.CustomTimerPeriod timerPeriod) {
                long cycle = timerPeriod.getTimer() + timerPeriod.getOffTimer();
                long adjusted = System.currentTimeMillis() / 1000 + timerPeriod.getOffset();
                long pos = adjusted % cycle;
                long remaining = cycle - pos;

                return String.format(Locale.US, "%02d", Double.valueOf(Math.floor(remaining % 60)).intValue());
            }
            return "";
        }

        static String getOffTimeMinute(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer instanceof CustomTimerDataHandler.CustomTimerPeriod timerPeriod) {
                long cycle = timerPeriod.getTimer() + timerPeriod.getOffTimer();
                long adjusted = System.currentTimeMillis() / 1000 + timerPeriod.getOffset();
                long pos = adjusted % cycle;
                long remaining = cycle - pos;

                return String.format(Locale.US, "%02d", Double.valueOf(Math.floor((double) (remaining % 3600) / 60)).intValue());
            }
            return "";
        }

        static Number getOffTimeHour(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer instanceof CustomTimerDataHandler.CustomTimerPeriod timerPeriod) {
                long cycle = timerPeriod.getTimer() + timerPeriod.getOffTimer();
                long adjusted = System.currentTimeMillis() / 1000 + timerPeriod.getOffset();
                long pos = adjusted % cycle;
                long remaining = cycle - pos;

                return Math.floor((double) remaining / 3600);
            }
            return null;
        }

        static Boolean getIsOn(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer instanceof CustomTimerDataHandler.CustomTimerPeriod timerPeriod) {
                long cycle = timerPeriod.getTimer() + timerPeriod.getOffTimer();
                long adjusted = System.currentTimeMillis() / 1000 + timerPeriod.getOffset();
                long pos = adjusted % cycle;

                return pos < timerPeriod.getTimer();
            }
            return false;
        }

        static Boolean getIsOff(List<String> indices) {
            CustomTimerDataHandler.CustomTimer timer = TimerHandler.instance().getTimers().stream().filter(t -> Objects.equals(t.getName(), indices.getFirst())).findFirst().orElse(null);
            if(timer instanceof CustomTimerDataHandler.CustomTimerPeriod timerPeriod) {
                long cycle = timerPeriod.getTimer() + timerPeriod.getOffTimer();
                long adjusted = System.currentTimeMillis() / 1000 + timerPeriod.getOffset();
                long pos = adjusted % cycle;

                return !(pos < timerPeriod.getTimer());
            }
            return false;
        }
    }

    static class CatchContext {
        static MutableComponent getLastCaughtFishName() {
            if(!CatchingHandler.instance().getLastCaughtFish().getItemStack().isEmpty()) {
                return CatchingHandler.instance().getLastCaughtFish().getName().copy();
            }
            return Component.empty();
        }

        static String getLastCaughtFishRarityName() {
            if(CatchingHandler.instance().getLastDataFish() != null) {
                return CatchingHandler.instance().getLastDataFish().value1().value1();
            }
            return "";
        }

        static MutableComponent getLastCaughtFishRarityIcon() {
            if(!CatchingHandler.instance().getLastCaughtFish().getItemStack().isEmpty()) {
                return CatchingHandler.instance().getLastCaughtFish().getRarityComponent().copy();
            }
            return Component.empty();
        }

        static Number getLastCaughtFishRarityDryStreak() {
            if(CatchingHandler.instance().getLastDataFish() != null) {
                return CatchingHandler.instance().getLastDataFish().value1().value2();
            }
            return null;
        }

        static String getLastCaughtFishVariantName() {
            if(CatchingHandler.instance().getLastDataFish() != null) {
                return CatchingHandler.instance().getLastDataFish().value2().value1();
            }
            return "";
        }

        static MutableComponent getLastCaughtFishVariantIcon() {
            if(!CatchingHandler.instance().getLastCaughtFish().getItemStack().isEmpty()) {
                return CatchingHandler.instance().getLastCaughtFish().getVariantComponent().copy();
            }
            return Component.empty();

        }

        static Number getLastCaughtFishVariantDryStreak() {
            if(CatchingHandler.instance().getLastDataFish() != null) {
                return CatchingHandler.instance().getLastDataFish().value2().value2();
            }
            return null;

        }

        static String getLastCaughtFishSizeName() {
            if(CatchingHandler.instance().getLastDataFish() != null) {
                return CatchingHandler.instance().getLastDataFish().value3().value1();
            }
            return "";
        }

        static MutableComponent getLastCaughtFishSizeIcon() {
            if(!CatchingHandler.instance().getLastCaughtFish().getItemStack().isEmpty()) {
                return CatchingHandler.instance().getLastCaughtFish().getFishSizeComponent().copy();
            }
            return Component.empty();
        }

        static Number getLastCaughtFishSizeDryStreak() {
            if(CatchingHandler.instance().getLastDataFish() != null) {
                return CatchingHandler.instance().getLastDataFish().value3().value2();
            }
            return null;
        }

        static MutableComponent getLastCaughtFishLore(List<String> indices) {
            return getLoreValue(CatchingHandler.instance().getLastCaughtFish(), indices.getFirst());
        }

        static PlaceholderValue getLastCaughtFishNbt(List<String> indices) {
            return getNbtValue(CatchingHandler.instance().getLastCaughtFish(), indices);
        }

        static MutableComponent getLastCaughtItemName(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(CatchingHandler.instance().getLastCaughtItems().isEmpty() || CatchingHandler.instance().getLastCaughtItems().size() >= index) return Component.empty();
            return CatchingHandler.instance().getLastCaughtItems().get(index).value1().getName().copy();
        }

        static Number getLastCaughtItemStackAmount(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(CatchingHandler.instance().getLastCaughtItems().isEmpty() || CatchingHandler.instance().getLastCaughtItems().size() >= index) return null;
            return CatchingHandler.instance().getLastCaughtItems().get(index).value2();
        }

        static String getLastCaughtItemId(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(CatchingHandler.instance().getLastCaughtItems().isEmpty() || CatchingHandler.instance().getLastCaughtItems().size() >= index) return "";
            return CatchingHandler.instance().getLastCaughtItems().get(index).value3().value1();
        }

        static Number getLastCaughtItemDryStreak(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(CatchingHandler.instance().getLastCaughtItems().isEmpty() || CatchingHandler.instance().getLastCaughtItems().size() >= index) return null;
            return CatchingHandler.instance().getLastCaughtItems().get(index).value3().value2();
        }

        static MutableComponent getLastCaughtItemLore(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(CatchingHandler.instance().getLastCaughtItems().isEmpty() || CatchingHandler.instance().getLastCaughtItems().size() >= index) return Component.empty();
            return getLoreValue(CatchingHandler.instance().getLastCaughtItems().get(index).value1(), indices.get(1));
        }

        static PlaceholderValue getLastCaughtItemNbt(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(CatchingHandler.instance().getLastCaughtItems().isEmpty() || CatchingHandler.instance().getLastCaughtItems().size() >= index) return PlaceholderValue.emptyText();
            return getNbtValue(CatchingHandler.instance().getLastCaughtItems().get(index).value1(), indices.subList(1, indices.size()));
        }

        static MutableComponent getLastCaughtPetName() {
            if(!CatchingHandler.instance().getLastCaughtPet().getItemStack().isEmpty()) {
                return CatchingHandler.instance().getLastCaughtPet().getName().copy();
            }
            return Component.empty();
        }

        static String getLastCaughtPetRarityName() {
            if(CatchingHandler.instance().getLastDataPet() != null) {
                return CatchingHandler.instance().getLastDataPet().value1().value1();
            }
            return "";
        }

        static MutableComponent getLastCaughtPetRarityIcon() {
            if(!CatchingHandler.instance().getLastCaughtPet().getItemStack().isEmpty()) {
                return CatchingHandler.instance().getLastCaughtPet().getRarityComponent().copy();
            }
            return Component.empty();
        }

        static Number getLastCaughtPetRarityDryStreak() {
            if(CatchingHandler.instance().getLastDataPet() != null) {
                return CatchingHandler.instance().getLastDataPet().value1().value2();
            }
            return null;
        }

        static String getLastCaughtPetRatingName() {
            if(CatchingHandler.instance().getLastDataPet() != null) {
                return CatchingHandler.instance().getLastDataPet().value2().value1();
            }
            return "";
        }

        static MutableComponent getLastCaughtPetRatingIcon() {
            if(!CatchingHandler.instance().getLastCaughtPet().getItemStack().isEmpty()) {
                return CatchingHandler.instance().getLastCaughtPet().getRatingComponent().copy();
            }
            return Component.empty();
        }

        static Number getLastCaughtPetRatingDryStreak() {
            if(CatchingHandler.instance().getLastDataPet() != null) {
                return CatchingHandler.instance().getLastDataPet().value2().value2();
            }
            return null;
        }

        static MutableComponent getLastCaughtPetLore(List<String> indices) {
            return getLoreValue(CatchingHandler.instance().getLastCaughtPet(), indices.getFirst());
        }

        static PlaceholderValue getLastCaughtPetNbt(List<String> indices) {
            return getNbtValue(CatchingHandler.instance().getLastCaughtPet(), indices);
        }
    }

    static class QuestContext {
        static MutableComponent getLastRewardedPetName() {
            return QuestHandler.instance().getLastRewardedPet().getName().copy();
        }

        static Number getLastRewardedPetLevel() {
            return QuestHandler.instance().getLastRewardedPet().getLevel();
        }

        static Number getLastRewardedPetLevelProgress() {
            return QuestHandler.instance().getLastRewardedPet().getProgress();
        }

        static MutableComponent getLastRewardedPetRating() {
            return QuestHandler.instance().getLastRewardedPet().getRatingComponent().copy();
        }

        static Number getLastRewardedPetRatingPercent() {
            return QuestHandler.instance().getLastRewardedPet().getTotalPercent() * 100;
        }

        static MutableComponent getLastRewardedPetRarity() {
            return QuestHandler.instance().getLastRewardedPet().getRarityComponent().copy();
        }

        static Number getLastRewardedPetLocationLuckPercent() {
            return QuestHandler.instance().getLastRewardedPet().getLocationPercentMaxLuck() * 100;
        }

        static Number getLastRewardedPetLocationScalePercent() {
            return QuestHandler.instance().getLastRewardedPet().getLocationPercentMaxScale() * 100;
        }

        static Number getLastRewardedPetClimateLuckPercent() {
            return QuestHandler.instance().getLastRewardedPet().getClimatePercentMaxLuck() * 100;
        }

        static Number getLastRewardedPetClimateScalePercent() {
            return QuestHandler.instance().getLastRewardedPet().getClimatePercentMaxScale() * 100;
        }

        static Number getLastRewardedPetLocationLuck() {
            return QuestHandler.instance().getLastRewardedPet().getLocationMaxLuck();
        }

        static Number getLastRewardedPetLocationScale() {
            return QuestHandler.instance().getLastRewardedPet().getLocationMaxScale();
        }

        static Number getLastRewardedPetClimateLuck() {
            return QuestHandler.instance().getLastRewardedPet().getClimateMaxLuck();
        }

        static Number getLastRewardedPetClimateScale() {
            return QuestHandler.instance().getLastRewardedPet().getClimateMaxScale();
        }

        static MutableComponent getLastRewardedPetLore(List<String> indices) {
            return getLoreValue(QuestHandler.instance().getLastRewardedPet(), indices.getFirst());
        }

        static PlaceholderValue getLastRewardedPetNbt(List<String> indices) {
            return getNbtValue(QuestHandler.instance().getLastRewardedPet(), indices);
        }

        static MutableComponent getLastRewardedItemName(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(QuestHandler.instance().getLastRewardedItems().isEmpty() || QuestHandler.instance().getLastRewardedItems().size() >= index) return Component.empty();
            return QuestHandler.instance().getLastRewardedItems().get(index).value1().getName().copy();
        }

        static MutableComponent getLastRewardedItemRarity(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(QuestHandler.instance().getLastRewardedItems().isEmpty() || QuestHandler.instance().getLastRewardedItems().size() >= index) return Component.empty();
            return QuestHandler.instance().getLastRewardedItems().get(index).value1().getRarityComponent().copy();
        }

        static Number getLastRewardedItemAmount(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(QuestHandler.instance().getLastRewardedItems().isEmpty() || QuestHandler.instance().getLastRewardedItems().size() >= index) return null;
            return QuestHandler.instance().getLastRewardedItems().get(index).value2();
        }

        static MutableComponent getLastRewardedItemLore(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(QuestHandler.instance().getLastRewardedItems().isEmpty() || QuestHandler.instance().getLastRewardedItems().size() >= index) return Component.empty();
            return getLoreValue(QuestHandler.instance().getLastRewardedItems().get(index).value1(), indices.get(1));
        }

        static PlaceholderValue getLastRewardedItemNbt(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(QuestHandler.instance().getLastRewardedItems().isEmpty() || QuestHandler.instance().getLastRewardedItems().size() >= index) return PlaceholderValue.emptyText();
            return getNbtValue(QuestHandler.instance().getLastRewardedItems().get(index).value1(), indices.subList(1, indices.size()));
        }
    }

    static class ScreenContext {
        static MutableComponent getLastScreen() {
            return ScreenHander.instance().getLastScreen().copy();
        }
    }

    static class ChummerContext {
        static Boolean isInRange() {
            ChummerHandler.ChummerInfo chummer = ChummerHandler.instance().getChummer();
            return chummer != null ? chummer.isInRange() : null;
        }

        static Boolean hasChummerNearby() {
            return ChummerHandler.instance().getChummer() != null;
        }

        static String getPlayer() {
            ChummerHandler.ChummerInfo chummer = ChummerHandler.instance().getChummer();
            return chummer != null ? chummer.getPlayer() : null;
        }

        static String getRarity() {
            ChummerHandler.ChummerInfo chummer = ChummerHandler.instance().getChummer();
            return chummer != null ? chummer.getRarity() : null;
        }

        static Number getTimeMinutes() {
            ChummerHandler.ChummerInfo chummer = ChummerHandler.instance().getChummer();
            return chummer != null ? chummer.getMinutes() : null;
        }

        static Number getTimeSeconds() {
            ChummerHandler.ChummerInfo chummer = ChummerHandler.instance().getChummer();
            return chummer != null ? chummer.getSeconds() : null;
        }
    }

    static class ConstantDataContext {
        static MutableComponent getFishRarity(List<String> indices) {
            return ConstantDataHandler.instance().getConstantData().fishData.getOrDefault(FishTagObject.RARITY, Map.of()).getOrDefault(indices.getFirst(), Component.empty()).copy();
        }

        static MutableComponent getFishSize(List<String> indices) {
            return ConstantDataHandler.instance().getConstantData().fishData.getOrDefault(FishTagObject.FISH_SIZE, Map.of()).getOrDefault(indices.getFirst(), Component.empty()).copy();
        }

        static MutableComponent getFishVariant(List<String> indices) {
            return ConstantDataHandler.instance().getConstantData().fishData.getOrDefault(FishTagObject.VARIANT, Map.of()).getOrDefault(indices.getFirst(), Component.empty()).copy();
        }

        static MutableComponent getPetRarity(List<String> indices) {
            return ConstantDataHandler.instance().getConstantData().petData.getOrDefault(PetTagObject.RARITY, Map.of()).getOrDefault(indices.getFirst(), Component.empty()).copy();
        }

        static MutableComponent getPetRating(List<String> indices) {
            return ConstantDataHandler.instance().getConstantData().petData.getOrDefault(PetTagObject.RATING, Map.of()).getOrDefault(TextHelper.smallCaps(indices.getFirst()), Component.empty()).copy();
        }
    }

    static class ProfileDataContext {
        static Number getActivePetSlot() {
            return ProfileDataHandler.instance().getProfileData().activePetSlot;
        }

        static Boolean getHasImportedStats() {
            return ProfileDataHandler.instance().getProfileData().hasImportedStats;
        }

        static Boolean getHasImportedCrew() {
            return ProfileDataHandler.instance().getProfileData().hasImportedCrew;
        }

        static Boolean getIsInCrewChat() {
            return ProfileDataHandler.instance().getProfileData().isInCrewChat;
        }

        static Boolean getTournamentContribution() {
            return ProfileDataHandler.instance().getProfileData().tournamentContribution;
        }
    }

    static class QuestDataContext {
        static MutableComponent getGoal(List<String> indices) {
            String location = BossEventHandler.instance().getLocation().getString();
            List<QuestDataHandler.Quest> quests = QuestDataHandler.instance().getQuestData().questList.getOrDefault(location, List.of());
            int index = Integer.parseInt(indices.getFirst());
            if(!quests.isEmpty() && index >= 0 && index < quests.size()) return ConstantDataHandler.instance().getConstantFishComponent(quests.get(index).goal).copy();
            return Component.empty();
        }

        static Number getMax(List<String> indices) {
            String location = BossEventHandler.instance().getLocation().getString();
            List<QuestDataHandler.Quest> quests = QuestDataHandler.instance().getQuestData().questList.getOrDefault(location, List.of());
            int index = Integer.parseInt(indices.getFirst());
            if(!quests.isEmpty() && index >= 0 && index < quests.size()) return quests.get(index).max;
            return null;
        }

        static Number getCurrent(List<String> indices) {
            String location = BossEventHandler.instance().getLocation().getString();
            List<QuestDataHandler.Quest> quests = QuestDataHandler.instance().getQuestData().questList.getOrDefault(location, List.of());
            int index = Integer.parseInt(indices.getFirst());
            if(!quests.isEmpty() && index >= 0 && index < quests.size()) return quests.get(index).current;
            return null;
        }

        static Boolean getHasQuest(List<String> indices) {
            String location = BossEventHandler.instance().getLocation().getString();
            List<QuestDataHandler.Quest> quests = QuestDataHandler.instance().getQuestData().questList.getOrDefault(location, List.of());
            int index = Integer.parseInt(indices.getFirst());
            if(quests.isEmpty() || index >= quests.size()) return false;
            if(index >= 0) return true;
            return null;
        }
    }

    static class StatsDataContext {
        static Number getFishTotal() {
            return StatsDataHandler.instance().getStatsData().fishTotal;
        }

        static Number getFishRarityCount(List<String> indices) {
            return StatsDataHandler.instance().getStatsData().fishData.getOrDefault(FishTagObject.RARITY, Map.of()).getOrDefault(indices.getFirst(), StatsDataHandler.Stat.of(null, null)).amount();
        }

        static Number getFishRarityDryStreak(List<String> indices) {
            Number caughtOn = StatsDataHandler.instance().getStatsData().fishData.getOrDefault(FishTagObject.RARITY, Map.of()).getOrDefault(indices.getFirst(), StatsDataHandler.Stat.of(null, null)).caughtOn();
            if(caughtOn != null) return StatsDataHandler.instance().getStatsData().fishTotal - caughtOn.intValue();
            return null;
        }

        static Number getFishSizeCount(List<String> indices) {
            return StatsDataHandler.instance().getStatsData().fishData.getOrDefault(FishTagObject.FISH_SIZE, Map.of()).getOrDefault(indices.getFirst(), StatsDataHandler.Stat.of(null, null)).amount();
        }

        static Number getFishSizeDryStreak(List<String> indices) {
            Number caughtOn = StatsDataHandler.instance().getStatsData().fishData.getOrDefault(FishTagObject.FISH_SIZE, Map.of()).getOrDefault(indices.getFirst(), StatsDataHandler.Stat.of(null, null)).caughtOn();
            if(caughtOn != null) return StatsDataHandler.instance().getStatsData().fishTotal - caughtOn.intValue();
            return null;
        }

        static Number getFishVariantCount(List<String> indices) {
            return StatsDataHandler.instance().getStatsData().fishData.getOrDefault(FishTagObject.VARIANT, Map.of()).getOrDefault(indices.getFirst(), StatsDataHandler.Stat.of(null, null)).amount();
        }

        static Number getFishVariantDryStreak(List<String> indices) {
            Number caughtOn = StatsDataHandler.instance().getStatsData().fishData.getOrDefault(FishTagObject.VARIANT, Map.of()).getOrDefault(indices.getFirst(), StatsDataHandler.Stat.of(null, null)).caughtOn();
            if(caughtOn != null) return StatsDataHandler.instance().getStatsData().fishTotal - caughtOn.intValue();
            return null;
        }

        static Number getPetTotal() {
            return StatsDataHandler.instance().getStatsData().petTotal;
        }

        static Number getPetDryStreak() {
            return StatsDataHandler.instance().getStatsData().fishTotal - StatsDataHandler.instance().getStatsData().petData.getOrDefault(PetTagObject.RARITY, new HashMap<>()).values().stream().mapToInt(StatsDataHandler.Stat::caughtOn).max().orElse(0);
        }

        static Number getPetRarityCount(List<String> indices) {
            return StatsDataHandler.instance().getStatsData().petData.getOrDefault(PetTagObject.RARITY, Map.of()).getOrDefault(indices.getFirst(), StatsDataHandler.Stat.of(null, null)).amount();
        }

        static Number getPetRarityDryStreak(List<String> indices) {
            Number caughtOn = StatsDataHandler.instance().getStatsData().petData.getOrDefault(PetTagObject.RARITY, Map.of()).getOrDefault(indices.getFirst(), StatsDataHandler.Stat.of(null, null)).caughtOn();
            if(caughtOn != null) return StatsDataHandler.instance().getStatsData().fishTotal - caughtOn.intValue();
            return null;
        }

        static Number getPetRatingCount(List<String> indices) {
            return StatsDataHandler.instance().getStatsData().petData.getOrDefault(PetTagObject.RATING, Map.of()).getOrDefault(TextHelper.smallCaps(indices.getFirst()), StatsDataHandler.Stat.of(null, null)).amount();
        }

        static Number getPetRatingDryStreak(List<String> indices) {
            Number caughtOn = StatsDataHandler.instance().getStatsData().petData.getOrDefault(PetTagObject.RATING, Map.of()).getOrDefault(TextHelper.smallCaps(indices.getFirst()), StatsDataHandler.Stat.of(null, null)).caughtOn();
            if(caughtOn != null) return StatsDataHandler.instance().getStatsData().fishTotal - caughtOn.intValue();
            return null;
        }

        static Number getItemCount(List<String> indices) {
            return StatsDataHandler.instance().getStatsData().itemData.getOrDefault(indices.getFirst(), StatsDataHandler.Stat.of(null, null)).amount();
        }

        static Number getItemDryStreak(List<String> indices) {
            Number caughtOn = StatsDataHandler.instance().getStatsData().itemData.getOrDefault(indices.getFirst(), StatsDataHandler.Stat.of(null, null)).caughtOn();
            if(caughtOn != null) return StatsDataHandler.instance().getStatsData().fishTotal - caughtOn.intValue();
            return null;
        }
    }

    static class CrewDataContext {
        static String getUuid(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(CrewHandler.instance().getCrewListOrdered().isEmpty() || CrewHandler.instance().getCrewListOrdered().size() >= index) return "";
            return CrewHandler.instance().getCrewListOrdered().get(index).value1().toString();
        }

        static String getName(List<String> indices) {
            int index = Integer.parseInt(indices.getFirst());
            if(CrewHandler.instance().getCrewListOrdered().isEmpty() || CrewHandler.instance().getCrewListOrdered().size() >= index) return "";
            return CrewHandler.instance().getCrewListOrdered().get(index).value2();
        }
    }

    static class TrackerDataContext {
        static PlaceholderValue getValue(List<String> indices) {
            CustomTrackerDataHandler.CustomTracker tracker = CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.getOrDefault(indices.getFirst(), null);
            if(tracker != null) {
                return switch (tracker.getValue()) {
                    case BooleanValue booleanValue -> PlaceholderValue.bool(booleanValue.value());
                    case ItemStackValue itemStackValue -> PlaceholderValue.component(itemStackValue.value().value1().getHoverName().copy());
                    case NumberValue numberValue -> PlaceholderValue.number(numberValue.value());
                    default -> PlaceholderValue.emptyText();
                };
            }
            return PlaceholderValue.emptyText();
        }

        static MutableComponent getItemLore(List<String> indices) {
            CustomTrackerDataHandler.CustomTracker tracker = CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.getOrDefault(indices.getFirst(), null);
            if(tracker != null) {
                return switch (tracker.getValue()) {
                    case ItemStackValue itemStackValue -> getLoreValue(itemStackValue.value().value1(), indices.get(1));
                    default -> Component.empty();
                };
            }
            return Component.empty();
        }

        static PlaceholderValue getItemNbt(List<String> indices) {
            CustomTrackerDataHandler.CustomTracker tracker = CustomTrackerDataHandler.instance().getCustomTrackerData().trackerList.getOrDefault(indices.getFirst(), null);
            if(tracker != null) {
                return switch (tracker.getValue()) {
                    case ItemStackValue itemStackValue -> getNbtValue(itemStackValue.value().value1(), indices.subList(1, indices.size()));
                    default -> PlaceholderValue.emptyText();
                };
            }
            return PlaceholderValue.emptyText();
        }
    }
    //endregion

    //region Functions
    static class EvaluationContext {
        /// Boolean

        static Boolean evalCondition(List<PlaceholderValue> args) {
            if(args.isEmpty()) {
                throw new PlaceholderEvaluationException(
                        "expects at least 1 argument, got " + args.size()
                );
            };
            return args.getFirst().toBoolean();
        }

        static Boolean evalEqualsIgnoreCase(List<PlaceholderValue> args) {
            if(args.size() != 2) {
                throw new PlaceholderEvaluationException(
                        "expects 2 arguments, got " + args.size()
                );
            }

            return args.getFirst().toString().equalsIgnoreCase(args.get(1).toString());
        }

        static PlaceholderValue evalConditionIf(List<PlaceholderValue> args) {
            if(args.size() < 2) {
                throw new PlaceholderEvaluationException(
                        "expects at least 2 arguments, got " + args.size()
                );
            };
            return args.getFirst().toBoolean()
                    ? args.get(1)
                    : (args.size() >= 3 ? args.get(2) : PlaceholderValue.emptyText());
        }

        static Boolean evalOr(List<PlaceholderValue> args) {
            if(args.isEmpty()) {
                throw new PlaceholderEvaluationException(
                        "expects at least 1 argument, got " + args.size()
                );
            };
            for (PlaceholderValue arg : args) {
                if(arg.toBoolean()) return true;
            }
            return false;
        }

        static Boolean evalAnd(List<PlaceholderValue> args) {
            if(args.isEmpty()) {
                throw new PlaceholderEvaluationException(
                        "expects at least 1 argument, got " + args.size()
                );
            };
            for (PlaceholderValue arg : args) {
                if(!arg.toBoolean()) return false;
            }
            return true;
        }

        static Boolean evalXor(List<PlaceholderValue> args) {
            if(args.isEmpty()) {
                throw new PlaceholderEvaluationException(
                        "expects at least 1 argument, got " + args.size()
                );
            };
            boolean result = false;
            for (PlaceholderValue arg : args) {
                result ^= arg.toBoolean();
            }
            return result;
        }

        static Boolean evalNot(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            }
            boolean value = args.getFirst().toBoolean();
            return !value;
        }

        static Boolean evalIsBlank(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            }
            return args.getFirst().toString().isBlank();
        }

        static Boolean evalContains(List<PlaceholderValue> args) {
            if(args.size() < 2) {
                throw new PlaceholderEvaluationException(
                        "expects at least 2 arguments, got " + args.size()
                );
            };
            if(args.size() > 2 && args.get(2).toBoolean()) {
                return args.getFirst().toString().toLowerCase(Locale.US).contains(args.get(1).toString().toLowerCase(Locale.US));
            } else {
                return args.getFirst().toString().contains(args.get(1).toString());
            }
        }

        static Boolean evalEndsWith(List<PlaceholderValue> args) {
            if(args.size() < 2) {
                throw new PlaceholderEvaluationException(
                        "expects at least 2 arguments, got " + args.size()
                );
            };
            if(args.size() > 2 && args.get(2).toBoolean()) {
                return args.getFirst().toString().toLowerCase(Locale.US).endsWith(args.get(1).toString().toLowerCase(Locale.US));
            } else {
                return args.getFirst().toString().endsWith(args.get(1).toString());
            }
        }

        static Boolean evalStartsWith(List<PlaceholderValue> args) {
            if(args.size() < 2) {
                throw new PlaceholderEvaluationException(
                        "expects at least 2 arguments, got " + args.size()
                );
            };
            if(args.size() > 2 && args.get(2).toBoolean()) {
                return args.getFirst().toString().toLowerCase(Locale.US).startsWith(args.get(1).toString().toLowerCase(Locale.US));
            } else {
                return args.getFirst().toString().startsWith(args.get(1).toString());
            }
        }

        static Boolean evalIsInfinite(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            }
            return Double.isInfinite(args.getFirst().toDouble());
        }

        static Boolean evalIsNaN(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            }
            return Double.isNaN(args.getFirst().toDouble());
        }

        static Boolean evalIsNumber(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            }
            return args.getFirst().isNumber();
        }

        static Boolean evalIsString(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            }
            return args.getFirst().isString();
        }

        static Boolean evalIsComponent(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            }
            return args.getFirst().isComponent();
        }

        static Boolean evalIsBoolean(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            }
            return args.getFirst().isBoolean();
        }

        static Boolean evalBetween(List<PlaceholderValue> args) {
            if(args.size() != 3) {
                throw new PlaceholderEvaluationException(
                        "expects 3 arguments, got " + args.size()
                );
            }
            return args.getFirst().toDouble() >= args.get(1).toDouble() && args.getFirst().toDouble() <= args.get(2).toDouble();
        }

        static Boolean evalOneOf(List<PlaceholderValue> args) {
            if(args.size() < 2) {
                throw new PlaceholderEvaluationException(
                        "expects at least 2 arguments, got " + args.size()
                );
            }

            String value = args.getFirst().toString();
            boolean oneOf = false;

            List<PlaceholderValue> candidates = args.subList(1, args.size());

            for (PlaceholderValue arg : candidates) {
                if(value.equals(arg.toString())) oneOf = true;
            }

            return oneOf;
        }

        /// Math

        static Number evalExpression(List<PlaceholderValue> args) {
            return args.isEmpty() ? 0 : args.getFirst().toDouble();
        }

        static Number evalMax(List<PlaceholderValue> args) {
            if(args.isEmpty()) {
                throw new PlaceholderEvaluationException(
                        "expects at least 1 argument, got " + args.size()
                );
            };
            double result = Double.NEGATIVE_INFINITY;
            for (PlaceholderValue arg : args) {
                result = Math.max(result, arg.toDouble());
            }
            return args.isEmpty() ? 0 : result;
        }

        static Number evalMin(List<PlaceholderValue> args) {
            if(args.isEmpty()) {
                throw new PlaceholderEvaluationException(
                        "expects at least 1 argument, got " + args.size()
                );
            };
            double result = Double.POSITIVE_INFINITY;
            for (PlaceholderValue arg : args) {
                result = Math.min(result, arg.toDouble());
            }
            return args.isEmpty() ? 0 : result;
        }

        static Number evalSum(List<PlaceholderValue> args) {
            if(args.isEmpty()) {
                throw new PlaceholderEvaluationException(
                        "expects at least 1 argument, got " + args.size()
                );
            };
            double result = 0;
            for (PlaceholderValue arg : args) {
                result += arg.toDouble();
            }
            return args.isEmpty() ? 0 : result;
        }

        static Number evalAverage(List<PlaceholderValue> args) {
            if(args.isEmpty()) {
                throw new PlaceholderEvaluationException(
                        "expects at least 1 argument, got " + args.size()
                );
            };
            double result = 0;
            for (PlaceholderValue arg : args) {
                result += arg.toDouble();
            }
            return args.isEmpty() ? 0 : result / args.size();
        }

        static Number evalAbs(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            }
            return Math.abs(args.getFirst().toDouble());
        }

        static Number evalCeil(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            }
            return Math.ceil(args.getFirst().toDouble());
        }

        static Number evalFloor(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            }
            return Math.floor(args.getFirst().toDouble());
        }

        static Number evalRound(List<PlaceholderValue> args) {
            if(args.isEmpty()) {
                throw new PlaceholderEvaluationException(
                        "expects at least 1 argument, got " + args.size()
                );
            }

            int decimals = 0;
            if(args.size() > 1) decimals = args.get(1).toInteger();

            if(Double.isInfinite(args.getFirst().toDouble())) return args.getFirst().toDouble();

            BigDecimal bd = new BigDecimal(args.getFirst().toDouble());
            bd = bd.setScale(decimals, RoundingMode.HALF_UP);
            return bd.doubleValue();
        }

        static Number evalMod(List<PlaceholderValue> args) {
            if(args.size() != 2) {
                throw new PlaceholderEvaluationException(
                        "expects 2 arguments, got " + args.size()
                );
            };
            double a = args.getFirst().toDouble();
            double b = args.get(1).toDouble();

            return a % b;
        }

        static Number evalClamp(List<PlaceholderValue> args) {
            if(args.size() != 3) {
                throw new PlaceholderEvaluationException(
                        "expects 3 arguments, got " + args.size()
                );
            };

            double value = args.getFirst().toDouble();
            double min = args.get(1).toDouble();
            double max = args.get(2).toDouble();

            return Math.clamp(value, min, max);
        }

        static Number evalLog(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };
            return Math.log(args.getFirst().toDouble());
        }

        static Number evalPow(List<PlaceholderValue> args) {
            if(args.size() != 2) {
                throw new PlaceholderEvaluationException(
                        "expects 2 arguments, got " + args.size()
                );
            };
            double a = args.getFirst().toDouble();
            double b = args.get(1).toDouble();

            return Math.pow(a, b);
        }

        static Number evalSqrt(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };
            return Math.sqrt(args.getFirst().toDouble());
        }

        static Number evalDistance(List<PlaceholderValue> args) {
            if(args.size() != 6) {
                throw new PlaceholderEvaluationException(
                        "expects 6 arguments, got " + args.size()
                );
            };
            double x1 = args.getFirst().toDouble();
            double y1 = args.get(1).toDouble();
            double z1 = args.get(2).toDouble();
            double x2 = args.get(3).toDouble();
            double y2 = args.get(4).toDouble();
            double z2 = args.get(5).toDouble();

            double dx = x2 - x1;
            double dy = y2 - y1;
            double dz = z2 - z1;

            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        static Number evalRandom(List<PlaceholderValue> args) {
            if(!args.isEmpty()) {
                throw new PlaceholderEvaluationException(
                        "expects no arguments, got " + args.size()
                );
            };
            return Math.random();
        }

        static Number evalRandomRange(List<PlaceholderValue> args) {
            if(args.size() != 2) {
                throw new PlaceholderEvaluationException(
                        "expects 2 arguments, got " + args.size()
                );
            };
            return args.getFirst().toDouble() + Math.random() * (args.get(1).toDouble() - args.getFirst().toDouble());
        }
        /// String Manipulation

        static PlaceholderValue evalSubstring(List<PlaceholderValue> args) {
            if(args.size() < 2) {
                throw new PlaceholderEvaluationException(
                        "expects at least 2 arguments, got " + args.size()
                );
            };

            PlaceholderValue value = args.getFirst();
            int length = value.toString().length();
            int start = args.get(1).toInteger();

            int end = length;
            if(args.size() > 2) end = args.get(2).toInteger();

            if(start < 0 || end < start || end > length) return PlaceholderValue.emptyText();

            if(value.isComponent()) {
                return PlaceholderValue.component(TextHelper.substring(value.toComponent(), start, end));
            } else {
                return PlaceholderValue.text(value.toString().substring(start, end));
            }
        }

        static Number evalIndexOf(List<PlaceholderValue> args) {
            if(args.size() < 2) {
                throw new PlaceholderEvaluationException(
                        "expects at least 2 arguments, got " + args.size()
                );
            };
            String value = args.getFirst().toString();
            String valueToSearch = args.get(1).toString();

            if(args.size() < 3) {
                return value.indexOf(valueToSearch);
            } else {
                int fromIndex = args.get(2).toInteger();
                return value.indexOf(valueToSearch, fromIndex);
            }
        }

        static Number evalLastIndexOf(List<PlaceholderValue> args) {
            if(args.size() < 2) {
                throw new PlaceholderEvaluationException(
                        "expects at least 2 arguments, got " + args.size()
                );
            };
            String value = args.getFirst().toString();
            String valueToSearch = args.get(1).toString();

            if(args.size() < 3) {
                return value.lastIndexOf(valueToSearch);
            } else {
                int fromIndex = args.get(2).toInteger();
                return value.lastIndexOf(valueToSearch, fromIndex);
            }
        }

        static String evalCharAt(List<PlaceholderValue> args) {
            if(args.size() != 2) {
                throw new PlaceholderEvaluationException(
                        "expects 2 arguments, got " + args.size()
                );
            };

            return String.valueOf(args.getFirst().toString().charAt(args.get(1).toInteger()));
        }

        static PlaceholderValue evalRepeat(List<PlaceholderValue> args) {
            if(args.size() != 2) {
                throw new PlaceholderEvaluationException(
                        "expects 2 arguments, got " + args.size()
                );
            };

            PlaceholderValue value = args.getFirst();

            int count = args.get(1).toInteger();
            if(count <= 0) return PlaceholderValue.emptyText();

            if(value.isComponent()) {
                MutableComponent repeatedComponent = Component.empty();

                for (int i = 0; i < count; i++) {
                    repeatedComponent.append(value.toComponent());
                }

                return PlaceholderValue.component(repeatedComponent);
            } else {
                return PlaceholderValue.text(value.toString().repeat(count));
            }
        }

        static PlaceholderValue evalUppercase(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };

            PlaceholderValue value = args.getFirst();

            if(value.isComponent()) {
                return PlaceholderValue.component(TextHelper.toUppercase(value.toComponent()));
            } else {
                return PlaceholderValue.text(value.toString().toUpperCase(Locale.US));
            }
        }

        static PlaceholderValue evalLowercase(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };

            PlaceholderValue value = args.getFirst();

            if(value.isComponent()) {
                return PlaceholderValue.component(TextHelper.toLowercase(value.toComponent()));
            } else {
                return PlaceholderValue.text(value.toString().toLowerCase(Locale.US));
            }
        }

        static PlaceholderValue evalReplace(List<PlaceholderValue> args) {
            if(args.size() != 3) {
                throw new PlaceholderEvaluationException(
                        "expects 3 arguments, got " + args.size()
                );
            };

            PlaceholderValue value = args.getFirst();

            if(value.isComponent()) {
                return PlaceholderValue.component(TextHelper.replace(value.toComponent(), args.get(1).toString(), args.get(2).toString()));
            } else {
                return PlaceholderValue.text(value.toString().replace(args.get(1).toString(), args.get(2).toString()));
            }
        }

        static PlaceholderValue evalReplaceFirst(List<PlaceholderValue> args) {
            if(args.size() != 3) {
                throw new PlaceholderEvaluationException(
                        "expects 3 argument, got " + args.size()
                );
            };

            PlaceholderValue value = args.getFirst();

            if(value.isComponent()) {
                return PlaceholderValue.component(TextHelper.replaceFirst(value.toComponent(), args.get(1).toString(), args.get(2).toString()));
            } else {
                return PlaceholderValue.text(value.toString().replaceFirst(args.get(1).toString(), args.get(2).toString()));
            }
        }

        static PlaceholderValue evalReverse(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };

            PlaceholderValue value = args.getFirst();

            if(value.isComponent()) {
                return PlaceholderValue.component(TextHelper.reverse(value.toComponent()));
            } else {
                return PlaceholderValue.text(new StringBuilder(value.toString()).reverse().toString());
            }
        }

        static MutableComponent evalJoin(List<PlaceholderValue> args) {
            if(args.size() < 2) {
                throw new PlaceholderEvaluationException(
                        "expects at least 2 arguments, got " + args.size()
                );
            };

            MutableComponent result = Component.empty().append(args.get(1).toComponent());
            MutableComponent separator = args.getFirst().toComponent();

            if(args.size() > 2) {
                List<PlaceholderValue> values = args.subList(2, args.size());

                for (PlaceholderValue arg : values) {
                    result.append(separator).append(arg.toComponent());
                }
            }

            return result;
        }

        static Number evalCount(List<PlaceholderValue> args) {
            if(args.size() != 2) {
                throw new PlaceholderEvaluationException(
                        "expects 2 arguments, got " + args.size()
                );
            };

            String value = args.getFirst().toString();

            return value.isEmpty() ? 0 : value.length() - value.replace(args.get(1).toString(), "").length() / args.get(1).toString().length();
        }

        static Number evalLength(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            }
            return args.getFirst().toString().length();
        }

        static String evalShortenNumber(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };
            Number number = args.getFirst().toDouble();

            return TextHelper.shortenNumber(number.floatValue(), 2);
        }

        static MutableComponent evalApplyFormat(List<PlaceholderValue> args) {
            if(args.size() != 2) {
                throw new PlaceholderEvaluationException(
                        "expects 2 arguments, got " + args.size()
                );
            };
            return PlaceholderColorCodes.applyFormat(args.getFirst().toString(), args.get(1).toString());
        }

        static MutableComponent evalFormat(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };
            return PlaceholderColorCodes.applyFormat(args.getFirst().toString());
        }

        static String evalRemoveFormat(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };
            return args.getFirst().toString();
        }

        static String evalFormatTime(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };
            return String.format(Locale.US, "%02d", args.getFirst().toInteger());
        }

        static MutableComponent evalFormatFancyBoolean(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };
            return TextHelper.literal(args.getFirst().toBoolean(), true);
        }

        static PlaceholderValue evalTrim(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };
            return PlaceholderValue.component(TextHelper.trim(args.getFirst().toComponent()));
        }

        static PlaceholderValue evalPadStart(List<PlaceholderValue> args) {
            if(args.size() != 3) {
                throw new PlaceholderEvaluationException(
                        "expects 3 arguments, got " + args.size()
                );
            };

            PlaceholderValue value = args.getFirst();
            int length = args.get(1).toInteger();
            PlaceholderValue paddingValue = args.get(2);

            MutableComponent paddedComponent = Component.empty();

            for (int i = 0; i < length; i++) {
                paddedComponent.append(paddingValue.toComponent());
            }

            MutableComponent result = paddedComponent.append(value.toComponent());

            return PlaceholderValue.component(result);
        }

        static PlaceholderValue evalPadEnd(List<PlaceholderValue> args) {
            if(args.size() != 3) {
                throw new PlaceholderEvaluationException(
                        "expects 3 arguments, got " + args.size()
                );
            };

            PlaceholderValue value = args.getFirst();
            int length = args.get(1).toInteger();
            PlaceholderValue paddingValue = args.get(2);

            MutableComponent paddedComponent = Component.empty();

            for (int i = 0; i < length; i++) {
                paddedComponent.append(paddingValue.toComponent());
            }

            MutableComponent result = value.toComponent().append(paddedComponent);

            return PlaceholderValue.component(result);
        }

        static PlaceholderValue evalTruncate(List<PlaceholderValue> args) {
            if(args.size() < 2) {
                throw new PlaceholderEvaluationException(
                        "expects at least 2 arguments, got " + args.size()
                );
            };

            if(args.get(1).toInteger() < args.getFirst().toString().length()) {
                MutableComponent truncatedText = TextHelper.substring(args.getFirst().toComponent(), 0, args.get(1).toInteger());
                return args.size() > 2
                        ? PlaceholderValue.component(truncatedText.append(args.get(2).toComponent()))
                        : PlaceholderValue.component(truncatedText);
            } else {
                return args.getFirst();
            }
        }

        static PlaceholderValue evalCapitalize(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };
            return PlaceholderValue.component(TextHelper.capitalize(args.getFirst().toComponent()));
        }

        static PlaceholderValue evalConcat(List<PlaceholderValue> args) {
            if(args.isEmpty()) {
                throw new PlaceholderEvaluationException(
                        "expects at least 1 argument, got " + args.size()
                );
            };

            MutableComponent result = Component.empty().append(args.getFirst().toComponent());

            if(args.size() > 1) {
                List<PlaceholderValue> values = args.subList(1, args.size());
                for (PlaceholderValue arg : values) {
                    result.append(arg.toComponent());
                }
            }

            return PlaceholderValue.component(result);
        }

        /// Misc

        static String evalTypeOf(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };

            PlaceholderValue value = args.getFirst();

            if(value.isComponent()) return "component";
            else if (value.isString()) return "string";
            else if (value.isBoolean()) return "boolean";
            else if (value.isNumber()) return "number";
            return "unknown";
        }

        static PlaceholderValue evalNoHide(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };
            return args.getFirst();
        }

        static PlaceholderValue evalHideLine(List<PlaceholderValue> args) {
            if(args.size() != 1) {
                throw new PlaceholderEvaluationException(
                        "expects 1 argument, got " + args.size()
                );
            };
            return args.getFirst().toBoolean() ? PlaceholderValue.emptyText().markFailure() : PlaceholderValue.emptyText();
        }
    }
    //endregion

    //region JSON Schema
    public static JsonObject toJsonSchema() {
        JsonObject root = new JsonObject();

        for (Map.Entry<String, PlaceholderTreeNode> entry : ROOTS.entrySet()) {
            root.add(entry.getKey(), describeNode(entry.getKey(), entry.getValue()));
        }

        return root;
    }

    public static String toJsonSchemaString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(toJsonSchema());
    }

    public static JsonObject toJsonPathList() {
        JsonObject root = new JsonObject();

        for (Map.Entry<String, PlaceholderTreeNode> entry : ROOTS.entrySet()) {
            JsonArray paths = new JsonArray();
            collectPaths(entry.getKey(), entry.getValue(), paths);
            root.add(entry.getKey(), paths);
        }

        return root;
    }

    public static String toJsonPathListString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(toJsonPathList());
    }

    public static void collectPaths(String path, PlaceholderTreeNode node, JsonArray out) {
        if(node.getValueKind() != ValueKind.NONE) out.add(path);
        if(node.getEvalKind() != EvalKind.NONE) out.add(path + ".()");

        for (Map.Entry<String, PlaceholderTreeNode> child : node.getChildren().entrySet()) {
            collectPaths(path + "." + child.getKey(), child.getValue(), out);
        }

        if(node.getIndexChild() != null) collectPaths(path + ".<index>", node.getIndexChild(), out);
        if(node.getStringChild() != null) collectPaths(path + ".<string>", node.getStringChild(), out);
        if(node.getStringArrayChild() != null) collectPaths(path + ".<string[]>", node.getStringArrayChild(), out);
    }

    private static JsonElement describeNode(String name, PlaceholderTreeNode node) {
        boolean hasChildren = !node.getChildren().isEmpty()
                || node.getIndexChild() != null
                || node.getStringChild() != null
                || node.getStringArrayChild() != null;
        boolean hasOwnValue = node.getValueKind() != ValueKind.NONE;
        boolean hasOwnFunction = node.getEvalKind() != EvalKind.NONE;

        if(!hasChildren) {
            if(hasOwnValue && hasOwnFunction) return selfDescriptor(name, node);
            if(hasOwnValue) return describeValueLeaf(node);
            if(hasOwnFunction) return describeFunctionLeaf(name, node);

            return new JsonPrimitive("unknown");
        }

        JsonObject object = new JsonObject();
        if(hasOwnValue && hasOwnFunction) {
            object.add("$self", selfDescriptor(name, node));
        } else if (hasOwnValue) {
            object.add("$self", describeValueLeaf(node));
        } else if (hasOwnFunction) {
            object.add("$self", describeFunctionLeaf(name, node));
        }

        for (Map.Entry<String, PlaceholderTreeNode> child : node.getChildren().entrySet()) {
            object.add(child.getKey(), describeNode(child.getKey(), child.getValue()));
        }
        if(node.getIndexChild() != null) {
            object.add("<index>", describeNode("<index>", node.getIndexChild()));
        }
        if(node.getStringChild() != null) {
            object.add("<string>", describeNode("<string>", node.getStringChild()));
        }
        if(node.getStringArrayChild() != null) {
            object.add("<string[]>", describeNode("<string[]>", node.getStringArrayChild()));
        }
        return object;
    }

    private static JsonObject selfDescriptor(String name, PlaceholderTreeNode node) {
        JsonObject self = new JsonObject();
        self.add("value", describeValueLeaf(node));
        self.add("function", describeFunctionLeaf(name, node));
        return self;
    }

    private static JsonElement describeValueLeaf(PlaceholderTreeNode node) {
        String tag = valueTag(node.getValueKind());

        JsonObject obj = new JsonObject();
        obj.addProperty("returns", tag);
        if(node.getDescription() != null) {
            obj.addProperty("description", node.getDescription());
        }
        if(node.allowsEmpty()) {
            obj.addProperty("allow_empty", true);
        }
        return obj;
    }

    private static JsonObject describeFunctionLeaf(String name, PlaceholderTreeNode node) {
        String returnTag = evalTag(node.getEvalKind());
        List<Param> params = node.getParams();

        JsonObject obj = new JsonObject();
        if(params.isEmpty()) {
            obj.addProperty("signature", name + ".(value): " + returnTag);
            obj.addProperty("returns", returnTag);
            if(node.getDescription() != null) {
                obj.addProperty("description", node.getDescription());
            }
            if(node.allowsEmpty()) {
                obj.addProperty("allow_empty", true);
            }
            return obj;
        }

        StringBuilder signature = new StringBuilder(name).append(".(");
        JsonArray paramsArray = new JsonArray();
        for (int i = 0; i < params.size(); i++) {
            Param param = params.get(i);
            if(i > 0) signature.append(", ");
            if(param.variadic()) signature.append("...");
            signature.append(param.name());
            if(param.optional()) signature.append("?");
            signature.append(": ").append(param.getType());

            JsonObject paramObj = new JsonObject();
            paramObj.addProperty("name", param.name());
            paramObj.addProperty("type", param.getType());
            if(param.optional()) paramObj.addProperty("optional", true);
            if(param.variadic()) paramObj.addProperty("variadic", true);
            paramsArray.add(paramObj);
        }
        signature.append("): ").append(returnTag);

        obj.addProperty("signature", signature.toString());
        obj.addProperty("returns", returnTag);
        if(node.getDescription() != null) {
            obj.addProperty("description", node.getDescription());
        }
        if(node.allowsEmpty()) {
            obj.addProperty("allow_empty", true);
        }
        obj.add("params", paramsArray);
        return obj;
    }

    private static String valueTag(ValueKind valueKind) {
        return switch (valueKind) {
            case NONE -> "none";
            case STRING -> "string";
            case COMPONENT -> "component";
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
            case VALUE -> "dynamic";
        };
    }

    private static String evalTag(EvalKind evalKind) {
        return switch (evalKind) {
            case NONE -> "none";
            case STRING -> "string";
            case COMPONENT -> "component";
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
            case VALUE -> "dynamic";
        };
    }
    //endregion

    //region Helpers
    private static MutableComponent getLoreValue(ItemStack itemStack, String indexString) {
        Pair<Boolean, TagObject> item = ValidateItem.isServerItem(itemStack, false);
        return item.value1() ? getLoreValue(item.value2(), indexString) : Component.empty();
    }

    private static MutableComponent getLoreValue(TagObject object, String indexString) {
        try {
            int index = Integer.parseInt(indexString);
            List<Component> loreLines = object.getLore();

            if(index >= 0 && index < loreLines.size()) {
                return loreLines.get(index).copy();
            }

            return Component.empty();
        } catch (NumberFormatException e) {
            return Component.empty();
        }
    }

    public static PlaceholderValue getNbtValue(ItemStack itemStack, List<String> indices) {
        Pair<Boolean, TagObject> item = ValidateItem.isServerItem(itemStack, true);
        return item.value1() ? getNbtValue(item.value2(), indices) : PlaceholderValue.emptyText();
    }

    public static PlaceholderValue getNbtValue(TagObject object, List<String> indices) {
        if(object.contains(indices.getFirst())) {
            Tag data = object.get(indices.getFirst());
            return switch (data.getId()) {
                case 1 -> PlaceholderValue.bool(object.getBoolean(indices.getFirst()));
                case 2 -> PlaceholderValue.number(object.getShort(indices.getFirst()));
                case 3 -> PlaceholderValue.number(object.getInt(indices.getFirst()));
                case 4 -> PlaceholderValue.number(object.getLong(indices.getFirst()));
                case 5 -> PlaceholderValue.number(object.getFloat(indices.getFirst()));
                case 6 -> PlaceholderValue.number(object.getDouble(indices.getFirst()));
                case 7 -> {
                    if(indices.size() > 1) {
                        try {
                            int index = Integer.parseInt(indices.get(1));
                            yield PlaceholderValue.number(object.getByteFromArray(indices.getFirst(), index));
                        } catch (NumberFormatException e) {
                            yield PlaceholderValue.emptyText();
                        }
                    }
                    yield PlaceholderValue.emptyText();
                }
                case 8 -> PlaceholderValue.text(object.getString(indices.getFirst()));
                case 9 -> {
                    if(indices.size() > 2) {
                        try {
                            int index = Integer.parseInt(indices.get(1));
                            yield getNbtValue(TagObject.of(object.getList(indices.getFirst()).getCompound(index).orElse(new CompoundTag())),
                                    indices.subList(2, indices.size())
                            );
                        } catch (NumberFormatException e) {
                            yield PlaceholderValue.emptyText();
                        }
                    }
                    yield PlaceholderValue.emptyText();
                }
                case 10 -> getNbtValue(TagObject.of(object.getTag(indices.getFirst())), indices.subList(1, indices.size()));
                case 11 -> {
                    if(indices.size() > 1) {
                        try {
                            int index = Integer.parseInt(indices.get(1));
                            yield PlaceholderValue.number(object.getIntFromArray(indices.getFirst(), index));
                        } catch (NumberFormatException e) {
                            yield PlaceholderValue.emptyText();
                        }
                    }
                    yield PlaceholderValue.emptyText();
                }
                case 12 -> {
                    if(indices.size() > 1) {
                        try {
                            int index = Integer.parseInt(indices.get(1));
                            yield PlaceholderValue.number(object.getLongFromArray(indices.getFirst(), index));
                        } catch (NumberFormatException e) {
                            yield PlaceholderValue.emptyText();
                        }
                    }
                    yield PlaceholderValue.emptyText();
                }
                default -> PlaceholderValue.emptyText();
            };
        }
        return PlaceholderValue.emptyText();
    }
    //endregion
}
