package org.patryk3211.powergrid;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;
import org.patryk3211.powergrid.collections.ModdedCommands;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.EntityWireInteraction;
import org.patryk3211.powergrid.electricity.wire.WireItem;
import org.patryk3211.powergrid.equipment.portablebattery.PortableBatteryItem;
import org.patryk3211.powergrid.collections.ModPackets;
import org.patryk3211.powergrid.collections.ModdedPackets;
import org.patryk3211.powergrid.network.packets.NegotiateSyncC2SPacket;
import org.patryk3211.powergrid.network.packets.ServerConfigS2CPacket;
import org.patryk3211.powergrid.utility.Lang;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class PowerGridEvents {
	// NeoForge: Fabric's FULL_CHUNK_STATUS_CHANGE handed over the LevelChunk; NeoForge's ticket-level event only has the
	// position. The chunk is taken when the event fires if it is already loaded (so a chunk that is about to unload
	// still gets paused), otherwise looked up when the queue is drained at the start of the next level tick.
	private record PendingChunkStatus(ChunkPos pos, @org.jetbrains.annotations.Nullable LevelChunk chunk, FullChunkStatus oldStatus, FullChunkStatus newStatus) { }

	private static final Map<ServerLevel, List<PendingChunkStatus>> PENDING_CHUNK_STATUS = new IdentityHashMap<>();

	public static void register() {
		var bus = NeoForge.EVENT_BUS;
		bus.addListener((ServerStartedEvent event) -> ComponentRegistry.validateItemEntries(event.getServer().registryAccess()));
		bus.addListener((LevelTickEvent.Pre event) -> {
			if (event.getLevel() instanceof ServerLevel level)
				levelPreTick(level);
		});
		bus.addListener((LevelTickEvent.Post event) -> {
			if (event.getLevel() instanceof ServerLevel level)
				GlobalElectricNetworks.postTick(level);
		});
		bus.addListener((ServerTickEvent.Post event) -> EntityWireInteraction.postTick(event.getServer()));
		bus.addListener((LevelEvent.Unload event) -> {
			if (event.getLevel() instanceof ServerLevel level) {
				PENDING_CHUNK_STATUS.remove(level);
				GlobalElectricNetworks.unloadWorld(level);
			}
		});
		bus.addListener((RegisterCommandsEvent event) ->
				ModdedCommands.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
		bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player)
				playerJoin(player);
		});
		bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player)
				playerQuit(player);
		});
		bus.addListener((PlayerEvent.Clone event) -> {
			if (event.getOriginal() instanceof ServerPlayer oldPlayer && event.getEntity() instanceof ServerPlayer newPlayer)
				playerCopy(oldPlayer, newPlayer, !event.isWasDeath());
		});
		bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player)
				GlobalElectricNetworks.dropTrackers(player, event.getFrom());
		});
		// Fabric's UseBlockCallback / UseItemCallback: a non-PASS result cancels the interaction with that result
		bus.addListener((PlayerInteractEvent.RightClickBlock event) -> {
			var result = WireItem.useOn(event.getEntity(), event.getHand(), event.getPos(), event.getFace());
			if (result != InteractionResult.PASS) {
				event.setCancellationResult(result);
				event.setCanceled(true);
			}
		});
		bus.addListener((PlayerInteractEvent.RightClickItem event) -> {
			var result = WireItem.use(event.getEntity(), event.getHand());
			if (result != InteractionResult.PASS) {
				event.setCancellationResult(result);
				event.setCanceled(true);
			}
		});
		bus.addListener((EntityLeaveLevelEvent event) -> {
			if (event.getLevel() instanceof ServerLevel level)
				BaseWireEntity.entityUnload(event.getEntity(), level);
		});
		bus.addListener((ChunkEvent.Load event) -> {
			if (event.getLevel() instanceof ServerLevel level)
				chunkLoad(level, event.getChunk().getPos());
		});
		bus.addListener(PowerGridEvents::chunkStatusChange);
	}

	private static void chunkStatusChange(ChunkTicketLevelUpdatedEvent event) {
		var oldStatus = ChunkLevel.fullStatus(event.getOldTicketLevel());
		var newStatus = ChunkLevel.fullStatus(event.getNewTicketLevel());
		if (oldStatus == newStatus)
			return;
		var pos = ChunkPos.unpack(event.getChunkPos());
		PENDING_CHUNK_STATUS.computeIfAbsent(event.getLevel(), key -> new ArrayList<>())
				.add(new PendingChunkStatus(pos, event.getLevel().getChunkSource().getChunkNow(pos.x(), pos.z()), oldStatus, newStatus));
	}

	private static void drainChunkStatusChanges(ServerLevel level) {
		var pending = PENDING_CHUNK_STATUS.remove(level);
		if (pending == null)
			return;
		for (var entry : pending) {
			var pos = entry.pos();
			var chunk = entry.chunk() != null ? entry.chunk() : level.getChunkSource().getChunkNow(pos.x(), pos.z());
			if (chunk == null)
				continue;
			if (entry.newStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING) && !level.getChunkSource().hasChunk(pos.x(), pos.z()))
				continue;
			ElectricBehaviour.handleTicketChange(chunk, entry.oldStatus(), entry.newStatus());
		}
	}

	private static void levelPreTick(ServerLevel level) {
		drainChunkStatusChanges(level);
		GlobalElectricNetworks.preTick(level);
		for (ServerPlayer player : level.players())
			playerPre(player);
	}

	private static void chunkLoad(ServerLevel level, net.minecraft.world.level.ChunkPos pos) {
		var global = GlobalElectricNetworks.getWorldNetworks((LevelAccessor) level);
		if (global == null)
			return;
		global.chunkLoaded(pos);
	}

	private static void playerCopy(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		Boolean syncType = NegotiateSyncC2SPacket.SYNC_TYPES.remove(oldPlayer);
		if (syncType != null)
			NegotiateSyncC2SPacket.SYNC_TYPES.put(newPlayer, syncType);
	}

	private static void playerQuit(ServerPlayer player) {
		NegotiateSyncC2SPacket.SYNC_TYPES.remove(player);
		GlobalElectricNetworks.dropTrackers(player, player.level().dimension());
	}

	private static void playerJoin(ServerPlayer player) {
		ModPackets.PACKETS.onPlayerJoin(player);
		ModdedPackets.sendToClient(new ServerConfigS2CPacket(), player);

		if(player.permissions().hasPermission(Permissions.COMMANDS_ADMIN) && !ModdedConfigs.server().isUpToDate()) {
			player.sendSystemMessage(Lang.translateDirect("message.outdated_configs"));
			player.sendSystemMessage(Component.empty()
					.append(Lang.translateDirect("message.reset_configs")
							.withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE)
									.withClickEvent(new ClickEvent.RunCommand("/powergrid reset_configs")))
					).append(Component.literal(" "))
					.append(Lang.translateDirect("message.disable_config_message")
							.withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE)
									.withClickEvent(new ClickEvent.RunCommand("/powergrid ignore_configs")))
					)
			);
		}
	}

	private static void playerPre(Player player) {
		if (player.level().isClientSide())
			return;

		ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!(chestStack.getItem() instanceof PortableBatteryItem battery))
			return;

		battery.onWornTick(chestStack, player);
	}
}
