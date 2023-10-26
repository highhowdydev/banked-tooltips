package com.bankedtooltips;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BankedTooltipTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BankedTooltipsPlugin.class);
		RuneLite.main(args);
	}
}