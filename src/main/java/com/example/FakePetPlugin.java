package com.example;

import com.example.dialog.DialogNode;
import com.example.dialog.DialogProvider;
import com.example.dialog.FakeDialogManager;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.geometry.SimplePolygon;
import net.runelite.api.model.Jarvis;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.example.PetObjectModel.radToJau;
import static net.runelite.api.Perspective.COSINE;
import static net.runelite.api.Perspective.SINE;

@Slf4j
@PluginDescriptor(
	name = "Fake Pet Plugin",
	description = "Spawn any pet in the game",
	tags = {"pet","fake pet","fake","pvm","fake follower"}
)
public class FakePetPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private EventBus eventBus;

	@Inject
	private FakePetConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PetOverlay overlayPet;

	@Inject
	private DialogProvider dialogProvider;

	@Inject
	private FakeDialogManager fakeDialogManager;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;


	@Override
	protected void startUp() throws Exception
	{
		buildSidePanel();
		overlayManager.add(overlayPet);
		eventBus.register(fakeDialogManager);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlayPet);
		eventBus.register(fakeDialogManager);
		clientToolbar.removeNavigation(navButton);
	}

	@Provides
	FakePetConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FakePetConfig.class);
	}

	private int lastActorOrientation;

	//public String message;

	private boolean dialogOpen;
	private boolean petFollowing = false;
	private boolean petEnterHouse;
	public boolean cutScene = false;

	public PetObjectModel pet = new PetObjectModel();
	public PetObjectModel wizard = new PetObjectModel();

	public PetData petData;

	private WorldPoint lastPlayerWP;
	private WorldPoint lastActorWP;

	public WorldArea nextTravellingPoint;
	public WorldArea petWorldArea = null;

	private Model petModel;

	private final List<WorldPoint> prevPlayerWPs = new ArrayList<>();

	public SimplePolygon poly;

	private FakePetPanel panel;

	private NavigationButton navButton;

	private final static String CONFIG_GROUP = "FakePetPlugin";

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getKey().equals("pet"))
		{
			petData = PetData.pets.get(config.pet().getIdentifier());
			clientThread.invokeLater(()-> updatePet());
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{

		if (pet.getRlObject() == null)
		{
			return;
		}

		if (gameStateChanged.getGameState() == GameState.LOADING && pet.isActive())
		{
			lastPlayerWP = client.getLocalPlayer().getWorldLocation();
			lastActorWP = pet.getWorldLocation();
			lastActorOrientation = pet.getOrientation();

			pet.despawn();
		}

		if ((gameStateChanged.getGameState() == GameState.LOGGED_IN && petFollowing && client.getVarbitValue(6719) == 0) || gameStateChanged.getGameState() == GameState.HOPPING && petFollowing)
		{

			WorldPoint wp = client.getLocalPlayer().getWorldLocation();
			WorldPoint aWP = pet.getWorldLocation();

			double intx = aWP.toWorldArea().getX() - wp.toWorldArea().getX();
			double inty = aWP.toWorldArea().getY() - wp.toWorldArea().getY();


			if (lastPlayerWP.distanceTo(client.getLocalPlayer().getWorldLocation()) < 5)
			{
				pet.spawn(lastActorWP,lastActorOrientation,petData.getSize());
				pet.setAnimation(pet.animationPoses[0]);
			}
			else
			{
				pet.spawn(client.getLocalPlayer().getWorldLocation(),radToJau(Math.atan2(intx,inty)),petData.getSize());
				pet.setAnimation(pet.animationPoses[0]);
			}

			nextTravellingPoint = pet.getWorldLocation().toWorldArea();
		}

	}


	@Subscribe
	public void onChatMessage(ChatMessage event)
	{

		String message = event.getMessage();

		if (cutScene)
		{
			return;
		}

		if (message.equals("You do not have a follower.") && event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			callPet(event);
		}

	}


	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{

		if (varbitChanged.getVarbitId() == 6719 && varbitChanged.getValue() == 0)
		{
			petEnterHouse = petFollowing;
		}

	}




	@Subscribe
	public void onGameTick(GameTick event)
	{

		if (cutScene)
		{
			//updateWizardActions();
		}

		WorldPoint playerDelayedLoc = getAndUpdatePlayersDelayedLoc();

		spawnPetInHouse();

		if (pet.getRlObject() == null || !pet.isActive())
		{
			return;
		}

		Player player = client.getLocalPlayer();
		double intx = pet.getLocalLocation().getX() - player.getLocalLocation().getX();
		double inty = pet.getLocalLocation().getY() - player.getLocalLocation().getY();

		if (nextTravellingPoint != null)
		{
			petWorldArea = nextTravellingPoint;
			petWorldArea = new WorldArea(nextTravellingPoint.toWorldPoint(),petData.getSize(),petData.getSize());
		}

		WorldArea worldArea = new WorldArea(playerDelayedLoc,1,1);

		nextTravellingPoint = petWorldArea.calculateNextTravellingPoint(client,worldArea,true, this::extraBlockageCheck);

		//allow pets to run / need to update so it wont start walking when it gets close on arrival to the player
		if (petData == PetData.NEXLING && nextTravellingPoint != null)
		{
			nextTravellingPoint = nextTravellingPoint.calculateNextTravellingPoint(client,worldArea,true, this::extraBlockageCheck);
		}

		if (nextTravellingPoint == null)
		{
			nextTravellingPoint = new WorldArea(getPathOutWorldPoint(petWorldArea),petData.getSize(),petData.getSize());
		}

		if (pet.isActive() && nextTravellingPoint != null && !cutScene)
		{
			pet.moveTo(nextTravellingPoint.toWorldPoint(), radToJau(Math.atan2(intx,inty)),petData.getSize());
		}

		if (pet.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation()) > 16 && petFollowing)
		{
			callPet(null);
		}




	}


	@Subscribe
	public void onClientTick(ClientTick event)
	{

//		if (cutScene)
//		{
//			double intx = wizard.getWorldLocation().toWorldArea().getX() - pet.getWorldLocation().toWorldArea().getX();
//			double inty = wizard.getWorldLocation().toWorldArea().getY() - pet.getWorldLocation().toWorldArea().getY();
//			wizard.rotateObject(intx,inty);
//		}


		if (pet.getRlObject() != null && pet.animationPoses != null)
		{

			List<LocalPoint> localPoints = new ArrayList<>();
			List<Player> nonLocalPlayers = client.getPlayers().stream().filter(player -> !Objects.equals(player.getName(), client.getLocalPlayer().getName())).collect(Collectors.toList());
			nonLocalPlayers.forEach(player -> localPoints.add(player.getLocalLocation()));
			client.getNpcs().forEach(npc -> localPoints.add(npc.getLocalLocation()));

			boolean overlappingModel = localPoints.stream().anyMatch(localPoint -> localPoint.equals(pet.getLocalLocation()));

			if (pet.animationPoses[0].getId() == pet.getRlObject().getAnimation().getId() && overlappingModel)
			{
				//pet.setModel(client.loadModel(0));
			}
			else if (!overlappingModel)
			{
				//pet.setModel(petModel);
			}

			LocalPoint lp = pet.getLocalLocation();
			int zOff = Perspective.getTileHeight(client,lp,client.getPlane());

			poly = calculateAABB(client, pet.getRlObject().getModel(), pet.getOrientation(), pet.getLocalLocation().getX(), pet.getLocalLocation().getY(),client.getPlane(), zOff);


			if (!cutScene)
			{

				pet.onClientTick(event);

				//for 2x2 set drawFontTilesFirst only when they are moveing / test vs walls in house and rimmy
				if (petData.getSize() == 2 && pet.getRlObject().getAnimation() != pet.animationPoses[0])
				{
					pet.getRlObject().setDrawFrontTilesFirst(true);
					pet.getRlObject().setRadius(120);
				}
				else if (pet.getRlObject().getRadius() == 120)
				{
					pet.getRlObject().setDrawFrontTilesFirst(false);
					pet.getRlObject().setRadius(60);
				}


			}

		}


	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (pet.getRlObject() == null || !pet.isActive())
		{
			return;
		}

		int firstMenuIndex = 0;

		for (int i = 0; i < client.getMenuEntries().length; i++)
		{
			if (client.getMenuEntries()[i].getOption().equals("Walk here"))
			{
				firstMenuIndex = i;
				break;
			}
		}


		List<String> options = Arrays.asList("Talk-to","Pick-up","Examine");

		if (petData.isMetamorph())
		{
			options = Arrays.asList("Talk-to","Pick-up","Metamorphosis","Examine");
		}


		for (String string : options)
		{
			if (poly.contains(client.getMouseCanvasPosition().getX(),client.getMouseCanvasPosition().getY()) && pet.isActive())
			{
				client.createMenuEntry(firstMenuIndex)
						.setOption(string)
						.setTarget("<col=ffff00>" + petData.getName() + "</col>")
						.setType(MenuAction.RUNELITE)
						.setParam0(0)
						.setParam1(0)
						.setDeprioritized(true);

			}

		}

	}



	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{


		if (petData == null)
		{
			return;
		}


		if (event.getMenuEntry().getType() == MenuAction.RUNELITE && event.getMenuTarget().contains(petData.getName()) && event.getMenuOption().equals("Pick-up"))
		{
			//&& pet.getWorldLocation().toWorldArea().isInMeleeDistance(client.getLocalPlayer().getWorldArea())
			if (pet.isActive())
			{
				petFollowing = false;
				pet.despawn();
			}
		}

		if (event.getMenuEntry().getType() == MenuAction.RUNELITE && event.getMenuTarget().contains(petData.getName()) && event.getMenuOption().equals("Examine"))
		{
			if (pet.isActive())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE,"",petData.getExamine(),"",false);
			}
		}


		if (event.getMenuEntry().getType() == MenuAction.RUNELITE && event.getMenuTarget().contains(petData.getName()) && event.getMenuOption().equals("Metamorphosis"))
		{
			if (pet.isActive())
			{
				petData = PetData.morphModel.get(petData);
				updatePet();
			}
		}

		if (event.getMenuEntry().getType() == MenuAction.RUNELITE && event.getMenuTarget().contains(petData.getName()) && event.getMenuOption().equals("Talk-to"))
		{

			if (pet.isActive() && pet.getWorldArea().distanceTo(client.getLocalPlayer().getWorldArea()) <= 1)
			{
				dialogOpen = true;
				fakeDialogManager.open(provideDialog());
				//fakeDialogManager.open(dialogProvider.CALL_THE_WIZARD);
			}

		}

		if (!event.getMenuTarget().contains(petData.getName()) && !event.getMenuOption().equals("Continue") && dialogOpen)
		{
			dialogOpen = false;
			chatboxPanelManager.close();
		}






	}

	private DialogNode provideDialog()
	{
		String dataString = petData.getDryestPerson();
		String name = dataString.substring(dataString.lastIndexOf(">") + 1,dataString.indexOf(":"));
		String kc = dataString.substring(dataString.indexOf(":")+ 1,dataString.lastIndexOf(":"));
		String date = dataString.substring(dataString.lastIndexOf(":") + 1);
		String kcIdentifer = dataString.substring(1,dataString.lastIndexOf(">"));


		return DialogNode.builder()
				.player()
				.animationId(567)
				.body("Tell me something to make me feel better")
				.onContinue
						(() ->
								DialogNode.builder()
										.npc(petData.getNpcId())
										.title(petData.getName())
										.body("It took " + name +" "+ kc + " " + kcIdentifer +" but<br>" +
												"They finally got me on " + date)
										.animationId(petData.getChatHeadAnimID())
										.build()


						)
				.build();
	}



	//contrast * 5 + 850
	public Model provideModel()
	{
		ModelData[] modelDataArray = new ModelData[petData.getModelIDs().size()];
		for (int i = 0; i < petData.getModelIDs().size(); i++)
		{
			modelDataArray[i] = client.loadModelData(petData.getModelIDs().get(i));
		}

		ModelData modelData = createModel(client,modelDataArray);

		if (petData.getScale() != -1)
		{
			modelData.cloneVertices();
			modelData.scale(petData.getScale(),petData.getScale(),petData.getScale());
		}


		//cut list in half fist half color to find, second half color to replace
		if (petData.getRecolorIDs() !=  null)
		{
			modelData.cloneColors();
			int mid = (petData.getRecolorIDs().size() / 2);

			for (int i = 0; i < mid; i++)
			{
				modelData.recolor(petData.getRecolorIDs().get(i),petData.getRecolorIDs().get(mid + i));
			}

		}


		int ambient = (petData.getAmbient() != -1 ? petData.getAmbient() : 0);
		int contrast = (petData.getContrast() != -1 ? petData.getContrast() : 0);

		return modelData.light(ambient + 64, contrast + 850,-30,-50,-30);
	}


	private void buildSidePanel()
	{
		panel = injector.getInstance(FakePetPanel.class);
		panel.sidePanelInitializer();

		if (petData == null)
		{
			petData = PetData.pets.get(config.pet().getIdentifier());
		}

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/nav_icon.png");
		navButton = NavigationButton.builder().tooltip("Fake Pet").icon(icon).priority(5).panel(panel).build();
		clientToolbar.addNavigation(navButton);
	}




	public void updatePet()
	{
		if (petData != null)
		{
			configManager.setConfiguration(CONFIG_GROUP,"pet",petData);
		}

		if (pet.getRlObject() == null)
		{
			pet.init(client);
		}

		petModel = provideModel();
		pet.setPoseAnimations(petData.getIdleAnim(),petData.getWalkAnim(),petData.getRunAnim());

		if (client.getGameState() == GameState.LOGGED_IN && pet.isActive())
		{
			//set to 0 for 1x1 and != 90 for 2x2
			if (pet.getLocalLocation().distanceTo(LocalPoint.fromWorld(client,nextTravellingPoint.toWorldPoint())) > 0 && pet.getLocalLocation().distanceTo(LocalPoint.fromWorld(client,nextTravellingPoint.toWorldPoint())) != 90)
			{
				pet.setAnimation(pet.animationPoses[1]);
			}
			else
			{
				pet.setAnimation(pet.animationPoses[0]);
			}

		}

		pet.setModel(petModel);
		panel.updateCurrentPetIcon();
	}

	public void updatePet(PetData buttonPetData)
	{
		petData = buttonPetData;
		updatePet();
	}

	public boolean extraBlockageCheck(WorldPoint worldPoint)
	{
		if (petData.getSize() != 2)
		{
			return true;
		}

		WorldArea area = new WorldArea(worldPoint, 1, 1);

		List<WorldArea> worldAreas = new ArrayList<>();
		client.getPlayers().forEach(p -> worldAreas.add(p.getWorldArea()));
		client.getNpcs().forEach(npc -> worldAreas.add(npc.getWorldArea()));

		boolean overlappingModel = worldAreas.stream().anyMatch(wa -> wa.intersectsWith(area));

		return !overlappingModel;
	}


	private void spawnPetInHouse()
	{
		if (petEnterHouse && petFollowing && !cutScene)
		{
			petEnterHouse = false;
			WorldPoint wp = client.getLocalPlayer().getWorldLocation();
			WorldPoint aWP = pet.getWorldLocation();

			double intx = aWP.toWorldArea().getX() - wp.toWorldArea().getX();
			double inty = aWP.toWorldArea().getY() - wp.toWorldArea().getY();

			pet.spawn(client.getLocalPlayer().getWorldLocation(),radToJau(Math.atan2(intx,inty)),petData.getSize());
			pet.setAnimation(pet.animationPoses[0]);
			nextTravellingPoint = pet.getWorldLocation().toWorldArea();
		}

	}



	private void callPet(ChatMessage event)
	{

		if ((pet.getRlObject() == null || !pet.isActive()))
		{
			petData = PetData.pets.get(config.pet().getIdentifier());
			petModel = provideModel();

			pet.init(client);
			pet.setPoseAnimations(petData.getIdleAnim(),petData.getWalkAnim(),petData.getRunAnim());
			pet.setModel(petModel);
		}

		WorldPoint wp = client.getLocalPlayer().getWorldLocation();
		WorldPoint aWP = pet.getWorldLocation();

		boolean petHasLOS = wp.toWorldArea().hasLineOfSightTo(client,aWP);

		if (event != null && wp.toWorldArea().distanceTo(aWP.toWorldArea()) < 6 && petHasLOS && pet.isActive())
		{
			event.getMessageNode().setValue("Your follower is already close enough.");
			return;
		}
		else if (pet.isActive())
		{
			pet.despawn();
		}


		double intx = aWP.toWorldArea().getX() - wp.toWorldArea().getX();
		double inty = aWP.toWorldArea().getY() - wp.toWorldArea().getY();

		if (event != null)
		{
			event.getMessageNode().setValue("");
		}

		petFollowing = true;

		pet.spawn(getPathOutWorldPoint(new WorldArea(getAndUpdatePlayersDelayedLoc(),petData.getSize(),petData.getSize())),radToJau(Math.atan2(intx,inty)),petData.getSize());
		pet.setAnimation(pet.animationPoses[0]); //0 == walk
		nextTravellingPoint = pet.getWorldLocation().toWorldArea();
	}

	private WorldPoint getAndUpdatePlayersDelayedLoc()
	{

		if (client.getLocalPlayer().getWorldLocation() == null)
		{
			return null;
		}


		WorldPoint worldPoint = null;

		prevPlayerWPs.add(0,client.getLocalPlayer().getWorldLocation());

		if (prevPlayerWPs.size() >= 2)
		{
			worldPoint = prevPlayerWPs.get(1);
		}

		if (worldPoint == null)
		{
			worldPoint = client.getLocalPlayer().getWorldLocation();
		}

		if (prevPlayerWPs.size() > 10)
		{
			prevPlayerWPs.subList(10, prevPlayerWPs.size()).clear();
		}

		return worldPoint;
	}

	private int getRandomInt(int max, int min)
	{
		return min + (int)(Math.random() * ((max - min) + 1));
	}


	public WorldPoint getPathOutWorldPoint(WorldArea worldArea)
	{

		ArrayList<WorldPoint> points = new ArrayList<>();

		for (int i = -1; i < 2; i++)
		{
			if (i != 0)
			{
				if (worldArea.canTravelInDirection(client,i,0))
				{

					WorldPoint worldPoint = new WorldPoint(worldArea.getX() + i,worldArea.getY(),client.getPlane());

					boolean secondCheck = true;
					if (petData.getSize() == 2)
					{
						WorldArea area = new WorldArea(worldPoint,2,2);
						secondCheck = area.canTravelInDirection(client,i,0) ;
					}


					if (!worldPoint.equals(client.getLocalPlayer().getWorldLocation()) && secondCheck)
					{
						points.add(worldPoint);
					}

				}


				if (worldArea.canTravelInDirection(client,0,i))
				{
					WorldPoint worldPoint = new WorldPoint(worldArea.getX(),worldArea.getY() + i,client.getPlane());

					boolean secondCheck = true;
					if (petData.getSize() == 2)
					{
						WorldArea area = new WorldArea(worldPoint,2,2);
					    secondCheck	= area.canTravelInDirection(client,0,i);
					}

					if (!worldPoint.equals(client.getLocalPlayer().getWorldLocation()) && secondCheck)
					{
						points.add(worldPoint);
					}
				}
			}
		}

		
		if (!points.isEmpty())
		{
			return points.get(getRandomInt(points.size() - 1,0));
		}

		return null;
	}


//	public void runCutScene()
//	{
//		if (cutScene)
//		{
//			return;
//		}
//
//		cutScene = true;
//		gameTickCutSceneStart = client.getTickCount();
//
//		short clothingColor = JagexColor.rgbToHSL(new Color(102, 93, 44).getRGB(), 0.6d);
//		short blue = JagexColor.rgbToHSL(Color.BLUE.getRGB(), 1.0d);
//
//		ModelData skirt = client.loadModelData(265).cloneColors().recolor((short)25238, clothingColor);
//
//		ModelData shirt = client.loadModelData(292).cloneColors().recolor((short)8741, clothingColor);
//
//		ModelData arms = client.loadModelData(170).cloneColors().recolor((short)8741, clothingColor);
//
//		ModelData cape = client.loadModelData(323).cloneColors()
//				.recolor((short) 926, blue) // Inside
//				.recolor((short) 7700, blue) // Mail cape
//				.recolor((short) 11200, (short)8741); // Trim
//
//		ModelData partyhat = client.loadModelData(187).cloneColors().recolor((short)926, blue);
//
//		ModelData mdf = createModel(client,
//				9103, // face
//				4925, // beard
//				176, // hands
//				181); // feet
//
//		mdf = createModel(client, mdf, skirt, shirt, arms, cape, partyhat);
//		Model model = mdf.light();
//
//		wizard.init(client);
//		wizard.setModel(model);
//
//		double intx = wizard.getWorldLocation().toWorldArea().getX() - pet.getWorldLocation().toWorldArea().getX();
//		double inty = wizard.getWorldLocation().toWorldArea().getY() - pet.getWorldLocation().toWorldArea().getY();
//
//		wizard.getRlObject().setAnimation(client.loadAnimation(813));
//		wizard.spawn(getPathOutWorldPoint(pet.getWorldLocation().toWorldArea()),radToJau(Math.atan2(intx,inty)),1);
//
//	}
//
//	public void updateWizardActions()
//	{
//		switch (client.getTickCount() - gameTickCutSceneStart)
//		{
//			case 1:
//				message = "Hey, How'd you get out of your cage?";
//				wizard.getRlObject().setAnimation(client.loadAnimation(857));
//				client.addChatMessage(ChatMessageType.GAMEMESSAGE,"", "MrNice98: <col=0000ff>"+message+"</col>","");
//				break;
//
//			case 7:
//				message = "";
//				break;
//
//			case 10:
//				message = "Don't you know your Clippers pet";
//				wizard.getRlObject().setAnimation(client.loadAnimation(813));
//				client.addChatMessage(ChatMessageType.GAMEMESSAGE,"","MrNice98: <col=0000ff>"+message+"</col>","");
//				break;
//
//			case 16:
//				message = "";
//				break;
//
//			case 20:
//				message = "Sorry, I need bring him back to Clipper";
//				client.addChatMessage(ChatMessageType.GAMEMESSAGE,"","MrNice98: <col=0000ff>"+message+"</col>","");//<col=ef1020>58:40</col>58:40</col>
//				break;
//
//			case 26:
//				message = "";
//				break;
//
//			case 30:
//				message = "Best I can do is a dupe Lil' Grumble";
//				client.addChatMessage(ChatMessageType.GAMEMESSAGE,"","MrNice98: <col=0000ff>"+message+"</col>","");
//				break;
//
//			case 35:
//				message = "Toodaloo!";
//				wizard.getRlObject().setAnimation(client.loadAnimation(714));
//				client.addChatMessage(ChatMessageType.GAMEMESSAGE,"","MrNice98: <col=0000ff>"+message+"</col>","");
//				break;
//
//			case 37:
//				wizard.despawn();
//				pet.despawn();
//				petFollowing = false;
//				break;
//		}
//	}


	private static ModelData createModel(Client client, ModelData... data)
	{
		return client.mergeModels(data);
	}

	private static ModelData createModel(Client client, int... data)
	{
		ModelData[] modelData = new ModelData[data.length];
		for (int i = 0; i < data.length; i++)
		{
			modelData[i] = client.loadModelData(data[i]);
		}
		return client.mergeModels(modelData);
	}


	private static SimplePolygon calculateAABB(Client client, Model m, int jauOrient, int x, int y, int z, int zOff)
	{
		AABB aabb = m.getAABB(jauOrient);

		int x1 = aabb.getCenterX();
		int y1 = aabb.getCenterZ();
		int z1 = aabb.getCenterY() + zOff;

		int ex = aabb.getExtremeX();
		int ey = aabb.getExtremeZ();
		int ez = aabb.getExtremeY();

		int x2 = x1 + ex;
		int y2 = y1 + ey;
		int z2 = z1 + ez;

		x1 -= ex;
		y1 -= ey;
		z1 -= ez;

		int[] xa = new int[]{
				x1, x2, x1, x2,
				x1, x2, x1, x2
		};
		int[] ya = new int[]{
				y1, y1, y2, y2,
				y1, y1, y2, y2
		};
		int[] za = new int[]{
				z1, z1, z1, z1,
				z2, z2, z2, z2
		};

		int[] x2d = new int[8];
		int[] y2d = new int[8];

		modelToCanvasCpu(client, 8, x, y, z, 0, xa, ya, za, x2d, y2d);

		return Jarvis.convexHull(x2d, y2d);
	}

	private static void modelToCanvasCpu(Client client, int end, int x3dCenter, int y3dCenter, int z3dCenter, int rotate, int[] x3d, int[] y3d, int[] z3d, int[] x2d, int[] y2d)
	{
		final int
				cameraPitch = client.getCameraPitch(),
				cameraYaw = client.getCameraYaw(),

				pitchSin = SINE[cameraPitch],
				pitchCos = COSINE[cameraPitch],
				yawSin = SINE[cameraYaw],
				yawCos = COSINE[cameraYaw],
				rotateSin = SINE[rotate],
				rotateCos = COSINE[rotate],

				cx = x3dCenter - client.getCameraX(),
				cy = y3dCenter - client.getCameraY(),
				cz = z3dCenter - client.getCameraZ(),

				viewportXMiddle = client.getViewportWidth() / 2,
				viewportYMiddle = client.getViewportHeight() / 2,
				viewportXOffset = client.getViewportXOffset(),
				viewportYOffset = client.getViewportYOffset(),

				zoom3d = client.getScale();

		for (int i = 0; i < end; i++)
		{
			int x = x3d[i];
			int y = y3d[i];
			int z = z3d[i];

			if (rotate != 0)
			{
				int x0 = x;
				x = x0 * rotateCos + y * rotateSin >> 16;
				y = y * rotateCos - x0 * rotateSin >> 16;
			}

			x += cx;
			y += cy;
			z += cz;

			final int
					x1 = x * yawCos + y * yawSin >> 16,
					y1 = y * yawCos - x * yawSin >> 16,
					y2 = z * pitchCos - y1 * pitchSin >> 16,
					z1 = y1 * pitchCos + z * pitchSin >> 16;

			int viewX, viewY;

			if (z1 < 50)
			{
				viewX = Integer.MIN_VALUE;
				viewY = Integer.MIN_VALUE;
			}
			else
			{
				viewX = (viewportXMiddle + x1 * zoom3d / z1) + viewportXOffset;
				viewY = (viewportYMiddle + y2 * zoom3d / z1) + viewportYOffset;
			}

			x2d[i] = viewX;
			y2d[i] = viewY;
		}
	}



}
