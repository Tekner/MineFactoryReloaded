package powercrystals.minefactoryreloaded.tile.rednet;

import static powercrystals.minefactoryreloaded.block.BlockRedNetCable.subSelection;

import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.vec.Vector3;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemDye;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;

import powercrystals.core.net.PacketWrapper;
import powercrystals.core.position.BlockPosition;
import powercrystals.core.position.INeighboorUpdateTile;
import powercrystals.minefactoryreloaded.MineFactoryReloadedCore;
import powercrystals.minefactoryreloaded.api.rednet.IConnectableRedNet;
import powercrystals.minefactoryreloaded.api.rednet.IRedNetDecorative;
import powercrystals.minefactoryreloaded.api.rednet.IRedNetNoConnection;
import powercrystals.minefactoryreloaded.api.rednet.RedNetConnectionType;
import powercrystals.minefactoryreloaded.net.Packets;
import powercrystals.minefactoryreloaded.setup.MFRConfig;

public class TileEntityRedNetCable extends TileEntity implements INeighboorUpdateTile
{
	
	protected int[] _sideColors = new int [6];
	protected byte[] cableMode = {0,0, 0,0,0,0, 0};
	
	private RedstoneNetwork _network;
	private boolean _needsNetworkUpdate;
	
	private static final int _maxVanillaBlockId = 158;
	private static List<Integer> _connectionWhitelist = Arrays.asList(23, 25, 27, 28, 29, 33, 46, 55, 64, 69, 70, 71, 72, 75, 76, 77, 93, 94, 96, 107, 123, 124, 131, 
			143, 147, 148, 149, 150, 151, 152, 154, 157, 158);
	private static List<Integer> _connectionBlackList;
	
	public TileEntityRedNetCable()
	{
		if(_connectionBlackList == null)
		{
			_connectionBlackList = new LinkedList<Integer>();
			for(String s : MFRConfig.redNetConnectionBlacklist.getString().replace("\"", "").split(","))
			{
				try
				{
					int i = Integer.parseInt(s.trim());
					_connectionBlackList.add(i);
				}
				catch(NumberFormatException x)
				{
					System.out.println("Empty or invalid rednet blacklist entry found. Not adding to rednet blacklist.");
				}
			}
		}
	}
	
	@Override
	public void invalidate() {
		super.invalidate();
		this.onChunkUnload();
	}
	
	public void setSideColor(ForgeDirection side, int color)
	{
		if(side == ForgeDirection.UNKNOWN)
		{
			return;
		}
		_sideColors[side.ordinal()] = color;
		updateNetwork();
	}
	
	public int getSideColor(ForgeDirection side)
	{
		if(side == ForgeDirection.UNKNOWN)
		{
			return 0;
		}
		return _sideColors[side.ordinal()];
	}
	
	public int getSideColorValue(ForgeDirection side)
	{
		return (ItemDye.dyeColors[~getSideColor(side) & 15] << 8) | 0xFF;
	}
	
	public byte getMode(int side)
	{
		return cableMode[side];
	}
	
	public void setMode(int side, byte mode)
	{
		boolean mustUpdate = (mode != cableMode[side]);
		cableMode[side] = mode;
		if(mustUpdate)
		{
			_needsNetworkUpdate = true;
		}
	}
	
	public RedNetConnectionType getConnectionState(ForgeDirection side)
	{
		return getConnectionState(side, true);
	}
	
	protected RedNetConnectionType getConnectionState(ForgeDirection side, boolean decorative)
	{
		byte _mode = cableMode[side.ordinal()];
		if (!decorative)
			_mode = 1;
		if (cableMode[6] == 1)
			_mode = 3;
		BlockPosition bp = new BlockPosition(this);
		bp.orientation = side;
		bp.moveForwards(1);
		
		int blockId = worldObj.getBlockId(bp.x, bp.y, bp.z);
		Block b = Block.blocksList[blockId];
		
		if(b == null) // block doesn't exist (air) - never connect
		{
			return RedNetConnectionType.None;
		}
		else if(blockId == MineFactoryReloadedCore.rednetCableBlock.blockID) // cables - always connect
		{
			return RedNetConnectionType.CableAll;
		}
		else if(_mode == 3) // cable-only, and not a cable - don't connect
		{
			return RedNetConnectionType.None;
		}
		else if(b instanceof IConnectableRedNet) // API node - let them figure it out
		{
			RedNetConnectionType type = ((IConnectableRedNet)b).getConnectionType(worldObj, bp.x, bp.y, bp.z, side.getOpposite());
			return type.isConnectionForced & !(_mode == 1 | _mode == 2) ?
					RedNetConnectionType.None : type; 
		}
		else if(b instanceof IRedNetNoConnection || b.isAirBlock(worldObj, bp.x, bp.y, bp.z))
		{
			return RedNetConnectionType.None;
		}
		else if (_mode == 2 && b.isBlockSolidOnSide(worldObj, bp.x, bp.y, bp.z, side.getOpposite()))
		{
			return RedNetConnectionType.ForcedCableSingle;
		}
		else if(_mode == 1) // mode 1 forces plate mode for weak power
		{
			return RedNetConnectionType.ForcedPlateSingle;
		}
		else if ((blockId <= _maxVanillaBlockId && !_connectionWhitelist.contains(blockId)) ||
				_connectionBlackList.contains(blockId) ||
				b instanceof IRedNetDecorative)
			// standard connection logic, then figure out if we shouldn't connect
			// mode 1 will skip this
		{
			return RedNetConnectionType.None;
		}
		else if(b.isBlockSolidOnSide(worldObj, bp.x, bp.y, bp.z, side.getOpposite()))
		{
			return RedNetConnectionType.CableSingle;
		}
		else
		{
			return RedNetConnectionType.PlateSingle;
		}
	}
	
	@Override
	public Packet getDescriptionPacket()
	{
		return  PacketWrapper.createPacket(MineFactoryReloadedCore.modNetworkChannel,
				Packets.CableDescription, new Object[]
				{
					xCoord, yCoord, zCoord,
					_sideColors[0], _sideColors[1], _sideColors[2],
					_sideColors[3], _sideColors[4], _sideColors[5],
					cableMode[0] | (cableMode[1] << 8) |
					(cableMode[2] << 16) | (cableMode[3] << 24),
					cableMode[4] | (cableMode[5] << 8) |
					(cableMode[6] << 16)
				});
	}
	
	@Override
	public void validate()
	{
		super.validate();
		_needsNetworkUpdate = true;
	}
	
	@Override
	public void updateEntity()
	{
		if(worldObj.isRemote)
			return;
		if(_network != null && _network.isInvalid())
		{
			_network = null;
			_needsNetworkUpdate = true;
		}
		if(_needsNetworkUpdate)
		{
			_needsNetworkUpdate = false;
			updateNetwork();
		}
		_network.tick();
	}
	
	public boolean onPartHit(EntityPlayer player, int side, int subHit)
	{
		return false;
	}
	
	public void addTraceableCuboids(List<IndexedCuboid6> list, boolean forTrace)
	{
		Vector3 offset = new Vector3(xCoord, yCoord, zCoord);
		
		IndexedCuboid6 main = new IndexedCuboid6(0, subSelection[0]); 
		list.add(main);
		
		ForgeDirection[] sides = ForgeDirection.VALID_DIRECTIONS;
		for (int i = sides.length; i --> 0; )
		{
			RedNetConnectionType c = getConnectionState(sides[i], true);
			RedNetConnectionType f = getConnectionState(sides[i], false);
			if (c.isConnected)
			{
				int o = 2 + i;
				if (c.isPlate)
					o += 6;
				else if (c.isCable)
					if (c.isAllSubnets)
					{
						if (forTrace)
							main.setSide(i, i & 1);
						else
							list.add((IndexedCuboid6)new IndexedCuboid6(2 + 6*3 + i,
									subSelection[2 + 6*3 + i]).add(offset));
						continue;
					}
				list.add((IndexedCuboid6)new IndexedCuboid6(o, subSelection[o]).add(offset));
				o = 2 + 6*2 + i;
				if (c.isSingleSubnet)
					list.add((IndexedCuboid6)new IndexedCuboid6(o, subSelection[o]).add(offset));
			}
			else if (forTrace & f.isConnected && cableMode[6] != 1)
			{ // cable-only
				list.add((IndexedCuboid6)new IndexedCuboid6(2 + i, subSelection[2 + i]).add(offset));
			}
		}
		main.add(offset);
	}
	
	public boolean canInterface(TileEntityRedNetCable with)
	{
		return true;
	}

	public void getTileInfo(List<String> info, ForgeDirection side, EntityPlayer player, boolean debug) {
		if (debug) {
			if (_network != null) {
				info.add("Grid:" + _network);/*
				info.add("Conduits: " + _network. + ", Nodes: " + grid.getNodeCount());
				info.add("Grid Max: " + grid.storage.getMaxEnergyStored());
				info.add("Grid Cur: " + grid.storage.getEnergyStored());//*/
			} else {
				info.add("Null Grid");
			}
			info.add("SideMode: " + Arrays.toString(cableMode));
			//info.add("Node: " + isNode);
			return;
		}
	}
	
	@Override
	public void onNeighboorChanged()
	{
		_needsNetworkUpdate = true;
	}
	
	public RedstoneNetwork getNetwork()
	{
		return _network;
	}
	
	public void setNetwork(RedstoneNetwork network)
	{
		_network = network;
		_network.addCable(new BlockPosition(this));
	}
	
	private void updateNetwork()
	{
		if(worldObj.isRemote)
		{
			return;
		}
		
		BlockPosition ourbp = new BlockPosition(this);
		RedstoneNetwork.log("Cable at %s updating network", ourbp.toString());
		
		if(_network == null)
		{
			for(BlockPosition bp : ourbp.getAdjacent(true))
			{
				TileEntity te = bp.getTileEntity(worldObj);
				if(te instanceof TileEntityRedNetCable)
				{
					TileEntityRedNetCable cable = ((TileEntityRedNetCable)te);
					if(cable.getNetwork() != null && !cable.getNetwork().isInvalid())
					{
						_network = cable.getNetwork();
						break;
					}
				}
			}
		}
		if(_network == null)
		{
			RedstoneNetwork.log("Initializing new network at %s", ourbp.toString());
			setNetwork(new RedstoneNetwork(worldObj));
		}
		for(BlockPosition bp : ourbp.getAdjacent(true))
		{
			TileEntity te = bp.getTileEntity(worldObj);
			if(te instanceof TileEntityRedNetCable)
			{
				TileEntityRedNetCable cable = ((TileEntityRedNetCable)te);
				if(cable.getNetwork() == null)
				{
					cable.setNetwork(_network);
				}
				else if(cable.getNetwork() != _network && cable.getNetwork() != null && !cable.getNetwork().isInvalid())
				{
					_network.mergeNetwork(cable.getNetwork());
				}
			}
			else
			{
				updateNearbyNode(bp);
			}
		}
	}
	
	public void updateNodes()
	{
		for(BlockPosition bp : new BlockPosition(this).getAdjacent(true))
		{
			updateNearbyNode(bp);
		}
	}
	
	private void updateNearbyNode(BlockPosition bp)
	{
		int subnet = getSideColor(bp.orientation);
		RedNetConnectionType connectionType = getConnectionState(bp.orientation);
		
		if(!worldObj.isAirBlock(bp.x, bp.y, bp.z))
		{
			if(connectionType.isAllSubnets)
			{
				_network.addOrUpdateNode(bp);
			}
			else if(connectionType.isCable)
			{
				_network.addOrUpdateNode(bp, subnet, false);
			}
			else if(connectionType.isPlate)
			{
				_network.addOrUpdateNode(bp, subnet, true);
			}
			else
			{
				_network.removeNode(bp);
			}
		}
		else
		{
			_network.removeNode(bp);
		}
	}
	
	@Override
	public void writeToNBT(NBTTagCompound tag)
	{
		super.writeToNBT(tag);
		tag.setIntArray("sideSubnets", _sideColors);
		tag.setByte("mode", cableMode[0]);
		tag.setByte("v", (byte)3);
		tag.setByteArray("cableMode", cableMode);
	}
	
	@Override
	public void readFromNBT(NBTTagCompound tag)
	{
		super.readFromNBT(tag);
		_sideColors = tag.getIntArray("sideSubnets");
		if(_sideColors.length == 0)
		{
			_sideColors = new int[6];
		}
		byte _mode = tag.getByte("mode");
		cableMode = tag.getByteArray("cableMode");
		if (cableMode.length < 6) cableMode = new byte[] {0,0,0, 0,0,0, 0};
		switch (tag.getByte("v"))
		{
		case 2:
			cableMode[6] = (byte)(cableMode[6] == 3 ? 1 : 0);
			break;
		case 0:
			if (_mode == 2)
				_mode = 3;
		case 1:
			cableMode = new byte[] {_mode,_mode,_mode,
					_mode,_mode,_mode, (byte)(_mode == 3 ? 1 : 0)};
			break;
		default:
			break;
		}
	}

	public boolean isSolidOnSide(int side)
	{
		return cableMode[side] != 3 & cableMode[6] != 1;
	}

    @Override
	public boolean shouldRenderInPass(int pass)
    {
        return false;
    }

	public void onNeighborTileChange(int x, int y, int z) {
	}
}
