package net.qtqgyt.MendCommand;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;

public class MendCommand implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("mend")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(this::executeMend)));
    }

    private int executeMend(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var player = source.getPlayer();
        if (player == null) return 0;

        var level = player.level();
        var inv = player.getInventory();
        List<ItemStack> mendableItems = new ArrayList<>();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.isDamaged()) continue;
            var enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (var entry : enchantments.entrySet()) {
                if (entry.getKey().value().effects().has(EnchantmentEffectComponents.REPAIR_WITH_XP)) {
                    mendableItems.add(stack);
                    break;
                }
            }
        }

        if (mendableItems.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No damaged Mending items found in inventory."), false);
            return 0;
        }

        int totalXpNeeded = 0;
        for (var stack : mendableItems) {
            int damage = stack.getDamageValue();
            int xpForOne = EnchantmentHelper.modifyDurabilityToRepairFromXp(level, stack, 1);
            if (xpForOne <= 0) xpForOne = 2;
            totalXpNeeded += (damage + xpForOne - 1) / xpForOne;
        }

        int availableXp = getTotalXpPoints(player);
        if (availableXp <= 0) {
            source.sendSuccess(() -> Component.literal("You have no XP to spend."), false);
            return 0;
        }

        if (availableXp >= totalXpNeeded) {
            for (var stack : mendableItems) {
                int damage = stack.getDamageValue();
                int xpForOne = EnchantmentHelper.modifyDurabilityToRepairFromXp(level, stack, 1);
                if (xpForOne <= 0) xpForOne = 2;
                int xpNeeded = (damage + xpForOne - 1) / xpForOne;
                int effectiveRepair = EnchantmentHelper.modifyDurabilityToRepairFromXp(level, stack, xpNeeded);
                stack.setDamageValue(damage - Math.min(effectiveRepair, damage));
            }
            player.giveExperiencePoints(-totalXpNeeded);
            int total = totalXpNeeded;
            int count = mendableItems.size();
            source.sendSuccess(() -> Component.literal("Fully repaired " + count + " item(s) for " + total + " XP."), false);
        } else {
            int remainingXp = availableXp;
            int fully = 0;
            int partial = 0;
            for (var stack : mendableItems) {
                if (remainingXp <= 0) break;
                int damage = stack.getDamageValue();
                int xpForOne = EnchantmentHelper.modifyDurabilityToRepairFromXp(level, stack, 1);
                if (xpForOne <= 0) xpForOne = 2;
                int maxXp = (damage + xpForOne - 1) / xpForOne;
                int toSpend = Math.min(remainingXp, maxXp);
                int effectiveRepair = EnchantmentHelper.modifyDurabilityToRepairFromXp(level, stack, toSpend);
                stack.setDamageValue(damage - Math.min(effectiveRepair, damage));
                remainingXp -= toSpend;
                if (toSpend >= maxXp) fully++;
                else partial++;
            }
            int spent = availableXp - remainingXp;
            player.giveExperiencePoints(-spent);
            int fFully = fully;
            int fPartial = partial;
            source.sendSuccess(() -> {
                String msg = "Repaired " + (fFully + fPartial) + " item(s) with " + spent + " XP";
                if (fFully > 0) msg += ", fully repaired " + fFully;
                if (fPartial > 0) msg += ", partially repaired " + fPartial;
                return Component.literal(msg + ".");
            }, false);
        }

        player.inventoryMenu.broadcastChanges();
        return 1;
    }

    private int getTotalXpPoints(ServerPlayer player) {
        int level = player.experienceLevel;
        float progress = player.experienceProgress;
        int total = 0;
        for (int i = 0; i < level; i++)
            total += getXpNeededForLevel(i);
        return total + (int)(progress * getXpNeededForLevel(level));
    }

    private int getXpNeededForLevel(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }
}