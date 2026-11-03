package com.smartchests.client;

import com.smartchests.DataPayload;
import com.smartchests.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public class SmartChestsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.TERMINAL, TerminalScreen::new);
		// Fabric runs play-payload receivers on the game thread.
		ClientPlayNetworking.registerGlobalReceiver(DataPayload.TYPE, (payload, context) -> ClientTerminalState.accept(payload));
	}
}
