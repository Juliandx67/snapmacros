package com.example.swapmacros;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;

/**
 * Fabric client mod (target: Minecraft 1.21.11, Yarn mappings).
 * Four keybinds, each runs a swap sequence over the hotbar.
 * Items are found automatically in the hotbar (slots 0-8).
 *
 * G: Windcharge -> Spear   (spear more often)
 * H: Pearl -> Windcharge   (pearl catch)
 * J: Mace <-> Axe          (swap hit)
 * K: Mace <-> Sword        (swap hit)
 */
public class SwapMacros implements ClientModInitializer {

    // Delay in ticks between the two steps of a sequence (0 = same tick).
    private static final int WINDCHARGE_SPEAR_DELAY = 1;
    private static final int PEARL_WINDCHARGE_DELAY = 2;

    private static final Predicate<ItemStack> WINDCHARGE = s -> s.isOf(Items.WIND_CHARGE);
    private static final Predicate<ItemStack> PEARL = s -> s.isOf(Items.ENDER_PEARL);
    private static final Predicate<ItemStack> MACE = s -> s.isOf(Items.MACE);
    private static final Predicate<ItemStack> AXE = s -> s.isIn(ItemTags.AXES);
    private static final Predicate<ItemStack> SWORD = s -> s.isIn(ItemTags.SWORDS);
    private static final Predicate<ItemStack> SPEAR = s -> s.isIn(ItemTags.SPEARS);

    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("swapmacros", "keys"));

    private KeyBinding spearKey, pearlKey, maceAxeKey, maceSwordKey;

    /** Steps run one per tick; a null entry is a pause of one tick. */
    private final Deque<Runnable> queue = new ArrayDeque<>();

    @Override
    public void onInitializeClient() {
        spearKey = reg("key.swapmacros.windcharge_spear", GLFW.GLFW_KEY_G);
        pearlKey = reg("key.swapmacros.pearl_windcharge", GLFW.GLFW_KEY_H);
        maceAxeKey = reg("key.swapmacros.mace_axe", GLFW.GLFW_KEY_J);
        maceSwordKey = reg("key.swapmacros.mace_sword", GLFW.GLFW_KEY_K);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.interactionManager == null) {
                queue.clear();
                return;
            }

            if (queue.isEmpty()) {
                if (spearKey.wasPressed()) {
                    useThenUse(client, WINDCHARGE, SPEAR, WINDCHARGE_SPEAR_DELAY);
                } else if (pearlKey.wasPressed()) {
                    useThenUse(client, PEARL, WINDCHARGE, PEARL_WINDCHARGE_DELAY);
                } else if (maceAxeKey.wasPressed()) {
                    swapHit(client, MACE, AXE);
                } else if (maceSwordKey.wasPressed()) {
                    swapHit(client, MACE, SWORD);
                }
            }

            Runnable step = queue.poll();
            if (step != null) step.run();
        });
    }

    private static KeyBinding reg(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(name, key, CATEGORY));
    }

    /** Finds the first hotbar slot matching the predicate, or -1. */
    private static int find(ClientPlayerEntity p, Predicate<ItemStack> match) {
        for (int i = 0; i < 9; i++) {
            if (match.test(p.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    /** Select item A and use it, wait, select item B and use it. */
    private void useThenUse(MinecraftClient mc, Predicate<ItemStack> a, Predicate<ItemStack> b, int delay) {
        ClientPlayerEntity p = mc.player;
        int slotA = find(p, a), slotB = find(p, b);
        if (slotA < 0 || slotB < 0) return;
        int original = p.getInventory().getSelectedSlot();

        queue.add(() -> use(mc, slotA));
        for (int i = 0; i < delay; i++) queue.add(() -> {});
        queue.add(() -> use(mc, slotB));
        queue.add(() -> p.getInventory().setSelectedSlot(original));
    }

    private static void use(MinecraftClient mc, int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /**
     * Swap hit: in a single tick select the partner item, attack the
     * targeted entity, then switch back to the original slot.
     */
    private void swapHit(MinecraftClient mc, Predicate<ItemStack> held, Predicate<ItemStack> partner) {
        ClientPlayerEntity p = mc.player;
        Entity target = mc.targetedEntity;
        if (target == null) return;

        int heldSlot = find(p, held), partnerSlot = find(p, partner);
        if (heldSlot < 0 || partnerSlot < 0) return;

        p.getInventory().setSelectedSlot(partnerSlot);
        mc.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);
        p.getInventory().setSelectedSlot(heldSlot);
    }
}
