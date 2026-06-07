package com.adubs.inventorpeeker.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Hooks the Cloth Config screen into ModMenu's mod list. */
public class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return PeekConfigScreen::create;
	}
}
