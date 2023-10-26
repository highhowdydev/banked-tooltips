package com.bankedtooltips;

import javax.inject.Inject;

import com.bankedtooltips.data.BankSave;
import com.bankedtooltips.data.BankWorldType;
import com.bankedtooltips.data.PluginDataStore;
import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.Optional;

@PluginDescriptor(
	name = "Banked Tooltips",
		description = "Shows the quantity of items in your bank",
		tags = {"bank", "inventory", "quantity", "banked", "tooltips"}
)
public class BankedTooltipsPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private OverlayManager overlayManager;

 	@Inject private BankedTooltipsOverlay overlay;

	@Inject private PluginDataStore dataStore;
	@Inject private ItemManager itemManager;
	@Inject private BankedTooltipsConfig config;
	@Inject private KeyManager keyManager;

	private boolean displayNameRegistered = false;

	@Override
	protected void startUp() throws Exception {
		overlayManager.add(overlay);
		keyManager.registerKeyListener(overlay);
	}

	@Override
	protected void shutDown() throws Exception {
		overlayManager.remove(overlay);
		keyManager.unregisterKeyListener(overlay);
	}

	@Provides
	BankedTooltipsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankedTooltipsConfig.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN)
			displayNameRegistered = false;
		else if (gameStateChanged.getGameState() == GameState.LOGGED_IN && !displayNameRegistered)
			handleLogin();
	}
//
	private void handleLogin() {
		BankWorldType worldType = BankWorldType.forWorld(client.getWorldType());
		Optional<BankSave> bankSave = dataStore.getDataForCurrentBank(worldType, String.valueOf(client.getAccountHash()));


		if (!bankSave.isEmpty()) {
			System.out.println("Found bank save for world type " + worldType + " and account hash " + client.getAccountHash());
			handleBankSave(bankSave.get());
			return;
		} else {
			System.out.println("No bank save found for world type " + worldType + " and account hash " + client.getAccountHash());
		}
	}

	private void handleBankSave(BankSave newSave) {
		assert client.isClientThread();
		dataStore.saveAsCurrentBank(newSave);
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (!displayNameRegistered) {
			Player player = client.getLocalPlayer();
			String charName = player == null ? null : player.getName();
			if (charName == null) return;
			displayNameRegistered = true;
			dataStore.registerDisplayNameForLogin(String.valueOf(client.getAccountHash()), charName);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged container) {
		if (container.getContainerId() != InventoryID.BANK.getId()) return;

		System.out.println("Bank container changed" + container.toString());

		BankWorldType worldType = BankWorldType.forWorld(client.getWorldType());
		ItemContainer bank = container.getItemContainer();
		handleBankSave(
				BankSave.fromCurrentBank(worldType, String.valueOf(client.getAccountHash()), bank, itemManager)
		);
	}

}
