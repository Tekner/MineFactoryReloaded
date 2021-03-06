package powercrystals.minefactoryreloaded.gui.client;

import cpw.mods.fml.common.network.PacketDispatcher;

import net.minecraft.client.gui.GuiButton;

import powercrystals.core.net.PacketWrapper;
import powercrystals.minefactoryreloaded.MineFactoryReloadedCore;
import powercrystals.minefactoryreloaded.gui.container.ContainerMobRouter;
import powercrystals.minefactoryreloaded.net.Packets;
import powercrystals.minefactoryreloaded.tile.machine.TileEntityMobRouter;

public class GuiMobRouter extends GuiFactoryPowered
{
	private TileEntityMobRouter _router;
	
	private GuiButton _toggle;
	private GuiButton _mode;
	
	public GuiMobRouter(ContainerMobRouter container, TileEntityMobRouter te)
	{
		super(container, te);
		_router = te;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void initGui()
	{
		super.initGui();
		
		int xOffset = (this.width - this.xSize) / 2;
		int yOffset = (this.height - this.ySize) / 2;
		
		_toggle = new GuiButton(1, xOffset + 7 + 16 + 3, yOffset + 23, 85, 20, "Whitelist");
		_mode = new GuiButton(2, xOffset + 7 + 16 + 3, yOffset + 23 + 19, 85, 20, "Mode");
		updateButtons();
		
		buttonList.add(_toggle);
		buttonList.add(_mode);
	}
	
	@Override
	public void updateScreen()
	{
		super.updateScreen();
		updateButtons();
	}
	
	protected void updateButtons()
	{
		_toggle.displayString = _router.getWhiteList() ? "Whitelist" : "Blacklist";
		String mode = "Unknown";
		switch (_router.getMatchMode())
		{
		case 0:
			mode = "Type Match";
			break;
		case 1:
			mode = "SubType Match";
			break;
		case 2:
			mode = "SuperType Match";
			break;
		case 3:
			mode = "UltraType Match";
			break;
		}
		_mode.displayString = mode;
	}
	
	@Override
	protected void actionPerformed(GuiButton button)
	{
		if(button.id == 1)
		{
			PacketDispatcher.sendPacketToServer(PacketWrapper.createPacket(MineFactoryReloadedCore.modNetworkChannel, Packets.RouterButton,
					new Object[] { _tileEntity.xCoord, _tileEntity.yCoord, _tileEntity.zCoord, 1 }));
		}
		else if(button.id == 2)
		{
			PacketDispatcher.sendPacketToServer(PacketWrapper.createPacket(MineFactoryReloadedCore.modNetworkChannel, Packets.RouterButton,
					new Object[] { _tileEntity.xCoord, _tileEntity.yCoord, _tileEntity.zCoord, 2 }));
		}
	}
}
