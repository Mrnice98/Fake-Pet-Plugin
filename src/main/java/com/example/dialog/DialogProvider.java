package com.example.dialog;

import javax.inject.Inject;

import com.example.FakePetPlugin;
import com.google.common.util.concurrent.Runnables;
import net.runelite.api.Client;
import net.runelite.api.NpcID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;

public class DialogProvider
{

	private static final int PLAYER_TALK_1 = 567;
	private static final String LIL_ZIK_TITLE = "Lil' Zik";
	private static final int LIL_ZIK_ANIMATION = 610;

	@Inject
	private Client client;

	@Inject
	private FakePetPlugin plugin;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private FakeDialogManager fakePetDialogManager;

	@Inject
	private ClientThread clientThread;


//	public final DialogNode CALL_THE_WIZARD =
//		DialogNode.builder()
//			.player()
//			.animationId(PLAYER_TALK_1)
//			.body("Finally after 1800 tires i've got you.")
//			.onContinue
//		(() ->
//
//				DialogNode.builder()
//				.npc(NpcID.LIL_ZIK_8337)
//				.title(LIL_ZIK_TITLE)
//				.body("Thank heavens a top 5 gamer!<br>" +
//						  "Please hide me from the mean wizard<br>" +
//						  "He wants to take me back.")
//				.animationId(LIL_ZIK_ANIMATION)
//				.next
//					(
//
//					DialogNode.builder()
//					.player()
//					.body("Oh boy, this is a real head scratcher.")
//					.animationId(PLAYER_TALK_1)
//					.onContinue
//					(() ->
//
//						DialogNode.builder()
//						.option("Call the wizard",()->
//						{
//							plugin.runCutScene();
//							return null;
//						})
//						.option("Do nothing",()->
//						{
//							Runnables.doNothing();
//							return null;
//						})
//
//		.build()
//		)
//		.build()
//		)
//		.build()
//		)
//		.build();



}
