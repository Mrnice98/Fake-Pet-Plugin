package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("FakePetPlugin")
public interface FakePetConfig extends Config
{
	@ConfigItem(
		keyName = "pet",
		name = "Pick Pet",
		description = "Pick pet",
		hidden = true
	)
	default PetData pet()
	{
		return PetData.ABYSSAL_ORPHAN;
	}

	@ConfigItem(
			keyName = "filter",
			name = "Filter",
			description = "Filter",
			hidden = true
	)
	default boolean filter()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showPets",
			name = "Show Pets",
			description = "Show Pets",
			hidden = true
	)
	default boolean showFavs()
	{
		return true;
	}

	@ConfigItem(
			keyName = "favorites",
			name = "Favorites",
			description = "favorites",
			hidden = true
	)
	default String favorites()
	{
		return "<Abyssal Orphan><Hellpuppy><Tangleroot><Noon>";
	}

	@ConfigItem(
			keyName = "debug",
			name = "Enable Debug",
			description = "Enables Debug",
			hidden = false
	)
	default boolean debug()
	{
		return false;
	}

}
