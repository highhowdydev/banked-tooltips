package com.bankedtooltips;

import net.runelite.client.config.*;

@ConfigGroup("bankedtooltips")
public interface BankedTooltipsConfig extends Config
{
	String MODIFIER_KEYBIND = "modifierKeybind";
	String USE_KEYBIND = "useKeybind";

	@ConfigSection(
			name = "General Settings",
			description = "General settings for the Banked Tooltips",
			position = 0
	)
	String generalSettings = "generalSettings";

	@ConfigSection(
			name = "Keybind Settings",
			description = "Keybind settings for the Banked Tooltips",
			position = 1
	)
	String keybindSettings = "keybindSettings";

	@ConfigItem(
			position = 0,
			keyName = "includeInventory",
			name = "Include Inventory Items",
			description = "Whether to include inventory items in the banked tooltips",
			section = generalSettings
	)
	default boolean includeInventory() {
		return true;
	}

	@ConfigItem(
			position = 0,
			keyName = USE_KEYBIND,
			name = "Use Keybind",
			description = "Whether to press a key to show quantity or not",
			section = keybindSettings
	)
	default boolean useKeybind() {
		return true;
	}

	@ConfigItem(
			position = 1,
			keyName = MODIFIER_KEYBIND,
			name = "Keybind",
			description = "The key to press to show quantity",
			section = keybindSettings
	)
	default Keybind modifierKey() {
		return Keybind.CTRL;
	}
}
