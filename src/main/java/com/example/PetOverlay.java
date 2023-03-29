package com.example;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import java.awt.*;

public class PetOverlay extends Overlay
{

    private FakePetPlugin plugin;

    private Client client;

    private FakePetConfig config;

    @Inject
    public PetOverlay(FakePetPlugin plugin, Client client, FakePetConfig config)
    {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPosition(OverlayPosition.DYNAMIC);
    }


    @Override
    public Dimension render(Graphics2D graphics)
    {

        if (plugin.pet.getRlObject() != null && plugin.pet.isActive() && plugin.nextTravellingPoint != null && plugin.poly != null && config.debug())
        {
            if (plugin.poly.contains(client.getMouseCanvasPosition().getX(),client.getMouseCanvasPosition().getY()))
            {
                graphics.setColor(Color.GREEN);
            }
            else
            {
                graphics.setColor(Color.WHITE);
            }

            graphics.draw(plugin.poly);

            graphics.draw(Perspective.getCanvasTileAreaPoly(client,plugin.pet.getLocalLocation(),plugin.petData.getSize()));

            graphics.setFont(FontManager.getRunescapeBoldFont());

//            if (plugin.wizard.getRlObject() != null && plugin.cutScene && plugin.wizard.isActive())
//            {
//                Point canvasPoint2 = Perspective.getCanvasTextLocation(client,graphics,plugin.wizard.getLocalLocation(),plugin.message,client.getLocalPlayer().getLogicalHeight());
//                OverlayUtil.renderTextLocation(graphics,canvasPoint2,plugin.message,Color.YELLOW);
//            }

        }

        return null;
    }
}
