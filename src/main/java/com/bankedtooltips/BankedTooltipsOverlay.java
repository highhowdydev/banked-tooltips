package com.bankedtooltips;

import com.bankedtooltips.data.BankSave;
import com.bankedtooltips.data.BankWorldType;
import com.bankedtooltips.data.PluginDataStore;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Optional;
import javax.inject.Inject;

import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.util.ColorUtil;

import org.apache.commons.lang3.StringUtils;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.Keybind;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public class BankedTooltipsOverlay extends Overlay implements KeyListener {

    private static final int INVENTORY_ITEM_WIDGETID = WidgetInfo.INVENTORY.getPackedId();
    private static final int BANK_INVENTORY_ITEM_WIDGETID = WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getPackedId();
    private static final int EXPLORERS_RING_ITEM_WIDGETID = WidgetInfo.EXPLORERS_RING_ALCH_INVENTORY.getPackedId();
    private static final int SEED_VAULT_ITEM_WIDGETID = WidgetInfo.SEED_VAULT_ITEM_CONTAINER.getPackedId();
    private static final int SEED_VAULT_INVENTORY_ITEM_WIDGETID = WidgetInfo.SEED_VAULT_INVENTORY_ITEMS_CONTAINER.getPackedId();
    private static final int POH_TREASURE_CHEST_INVENTORY_ITEM_WIDGETID = WidgetInfo.POH_TREASURE_CHEST_INVENTORY_CONTAINER.getPackedId();

    private final Client client;
    private final ItemManager itemManager;
    private final TooltipManager tooltipManager;
    private final BankedTooltipsConfig config;
    private boolean keybindPressed = false;

    @Inject private PluginDataStore dataStore;

    @Inject
    BankedTooltipsOverlay(Client client, TooltipManager tooltipManager, ItemManager itemManager, BankedTooltipsConfig config) {
        setPosition(OverlayPosition.DYNAMIC);
        this.client = client;
        this.tooltipManager = tooltipManager;
        this.itemManager = itemManager;
        this.config = config;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (client.isMenuOpen()) return null;

        if (config.useKeybind()) {
            if (config.modifierKey().getKeyCode() == Keybind.NOT_SET.getKeyCode()) {
                if (config.modifierKey().getModifiers() == 0) return null;
            }

            if (!keybindPressed) return null;
        }

        final MenuEntry[] menuEntries = client.getMenuEntries();
        final int last = menuEntries.length - 1;

        if (last < 0) return null;

        final MenuEntry menuEntry = menuEntries[last];

        if (StringUtils.isEmpty(menuEntry.getTarget()) || menuEntry.getParam0() < 0)
            return null;

        final MenuAction action = menuEntry.getType();
        final int widgetId = menuEntry.getParam1();
        final int groupId = WidgetInfo.TO_GROUP(widgetId);

       switch (action) {
           case WIDGET_USE_ON_ITEM:
           case WIDGET_TARGET:
           case CC_OP:
           case ITEM_USE:
           case ITEM_FIRST_OPTION:
           case ITEM_SECOND_OPTION:
           case ITEM_THIRD_OPTION:
           case ITEM_FOURTH_OPTION:
           case CC_OP_LOW_PRIORITY:
           case ITEM_FIFTH_OPTION:
                switch (groupId) {
                    case WidgetID.INVENTORY_GROUP_ID:
                        addTooltip(menuEntry, groupId);
                    default:
                        return null;
                }
       }

        return null;
    }

    private ItemContainer getContainer(int widgetId) {
        if (widgetId == INVENTORY_ITEM_WIDGETID)
            return client.getItemContainer(InventoryID.INVENTORY);

        return null;
    }

    private void addTooltip(MenuEntry menuEntry, int groupId) {
        if (groupId == WidgetID.INVENTORY_GROUP_ID) {
            final String text = buildTooltipText(menuEntry);

            if (text != null)
                tooltipManager.add(new Tooltip(ColorUtil.prependColorTag(text, new Color(238, 238, 238))));
        }

        return;
    }

    private String buildTooltipText(MenuEntry menuEntry) {
        final int widgetId = menuEntry.getParam1();
        ItemContainer container = null;

        if (widgetId == INVENTORY_ITEM_WIDGETID ||
                widgetId == BANK_INVENTORY_ITEM_WIDGETID ||
                widgetId == EXPLORERS_RING_ITEM_WIDGETID ||
                widgetId == SEED_VAULT_INVENTORY_ITEM_WIDGETID ||
                widgetId == POH_TREASURE_CHEST_INVENTORY_ITEM_WIDGETID)
        {
            container = client.getItemContainer(InventoryID.INVENTORY);
        } else if (widgetId == SEED_VAULT_ITEM_WIDGETID) {
            container = client.getItemContainer(InventoryID.SEED_VAULT);
        }

        final int index = menuEntry.getParam0();
        assert container != null;
        final Item item = container.getItem(index);

        if (item != null) {
            int itemId = item.getId();
            int bankQuantity = getQuantity(itemId);
            int itemQuantity = item.getQuantity();
            int quantity = config.includeInventory() ? itemQuantity + bankQuantity : bankQuantity;
            return "Total Quantity: " + quantity;
        }

        return null;
    }

    private int getQuantity(int itemId) {
        BankWorldType worldType = BankWorldType.forWorld(client.getWorldType());
        Optional<BankSave> bankSave = dataStore.getDataForCurrentBank(worldType, String.valueOf(client.getAccountHash()));

        if (bankSave.isPresent()) {
            BankSave save = bankSave.get();
            return save.getItemQuantity(itemId);
        }

        return 0;
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        System.out.println("Key pressed" + e.getKeyCode());

        if (!config.useKeybind()) return;

        if (config.modifierKey().matches(e)) {
            keybindPressed = true;
            System.out.println("Keybind pressed");
        } else {
            System.out.println("Invalid key pressed");
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        System.out.println("Key released");
        if (!config.useKeybind()) return;

        if (config.modifierKey().matches(e)) {
            keybindPressed = false;
            System.out.println("Keybind released");
        } else {
            System.out.println("Invalid key released");
        }
    }
}


