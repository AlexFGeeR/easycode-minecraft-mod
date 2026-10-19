package ru.blockcode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import ru.blockcode.program.AgentWorld;
import ru.blockcode.program.ProgramError;

import java.util.List;
import java.util.UUID;

/**
 * Агент игрока в мире. В прототипе это обычный аллей без ИИ: он летает, не падает,
 * неуязвим и подписан «Агент ник». Программа двигает его по блокам.
 */
public class MinecraftAgent implements AgentWorld {

    public static final String TAG = "blockcode_agent";
    public static final int MAX_DISTANCE = 100;

    private final MinecraftServer server;
    private final UUID ownerId;
    private UUID entityId;
    private BlockPos pos;
    private Direction facing = Direction.SOUTH;

    public MinecraftAgent(MinecraftServer server, UUID ownerId) {
        this.server = server;
        this.ownerId = ownerId;
    }

    private String ownerTag() { return TAG + "_" + ownerId; }

    private ServerPlayer owner() {
        ServerPlayer p = server.getPlayerList().getPlayer(ownerId);
        if (p == null) throw new ProgramError("Игрок вышел из игры");
        return p;
    }

    private Entity entity() {
        if (entityId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(entityId);
            if (e != null && e.isAlive()) return e;
        }
        return null;
    }

    private ServerLevel level() {
        Entity e = entity();
        return e != null ? (ServerLevel) e.level() : owner().serverLevel();
    }

    /** Найти своего агента (в том числе после перезахода) или создать нового рядом с игроком. */
    public void ensureSpawned() {
        Entity e = entity();
        if (e == null) {
            ServerPlayer p = owner();
            List<? extends Allay> found = p.serverLevel().getEntities(EntityType.ALLAY, a -> a.getTags().contains(ownerTag()));
            if (!found.isEmpty()) {
                e = found.get(0);
                entityId = e.getUUID();
                for (int i = 1; i < found.size(); i++) found.get(i).discard();
            }
        }
        if (e == null) {
            spawnAt(owner());
        } else if (pos == null) {
            pos = e.blockPosition();
            facing = Direction.fromYRot(e.getYRot());
        }
    }

    private void spawnAt(ServerPlayer p) {
        Entity old = entity();
        if (old != null) old.discard();
        ServerLevel level = p.serverLevel();
        Allay a = EntityType.ALLAY.create(level);
        if (a == null) throw new ProgramError("Не удалось создать агента");
        a.setNoAi(true);
        a.setNoGravity(true);
        a.setInvulnerable(true);
        a.setSilent(true);
        a.setPersistenceRequired();
        a.setCustomName(Component.literal("Агент " + p.getGameProfile().getName()));
        a.setCustomNameVisible(true);
        a.addTag(TAG);
        a.addTag(ownerTag());
        pos = p.blockPosition();
        facing = p.getDirection();
        place(a);
        level.addFreshEntity(a);
        entityId = a.getUUID();
    }

    private void place(Entity e) {
        float yRot = facing.toYRot();
        e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yRot, 0);
        e.setYHeadRot(yRot);
        e.setYBodyRot(yRot);
        e.setDeltaMovement(Vec3.ZERO);
    }

    private void sync() {
        Entity e = entity();
        if (e == null) { spawnAt(owner()); return; }
        place(e);
    }

    private Direction toWorld(Dir d) {
        switch (d) {
            case FORWARD: return facing;
            case BACK: return facing.getOpposite();
            case LEFT: return facing.getCounterClockWise();
            case RIGHT: return facing.getClockWise();
            case UP: return Direction.UP;
            default: return Direction.DOWN;
        }
    }

    private BlockPos target(Dir d) {
        ensureSpawned();
        BlockPos t = pos.relative(toWorld(d));
        ServerLevel level = level();
        if (level.isOutsideBuildHeight(t)) throw new ProgramError("Агент упёрся в границу мира по высоте");
        if (!level.isLoaded(t)) throw new ProgramError("Агент улетел туда, где мир не загружен");
        ServerPlayer p = owner();
        if (p.level() == level && (Math.abs(t.getX() - p.getBlockX()) > MAX_DISTANCE
                || Math.abs(t.getZ() - p.getBlockZ()) > MAX_DISTANCE)) {
            throw new ProgramError("Агент не может улетать от тебя дальше " + MAX_DISTANCE + " блоков");
        }
        return t;
    }

    @Override
    public boolean move(Dir d) {
        BlockPos t = target(d);
        ServerLevel level = level();
        if (!level.getBlockState(t).getCollisionShape(level, t).isEmpty()) return false;
        pos = t;
        sync();
        return true;
    }

    @Override
    public void turn(boolean left) {
        ensureSpawned();
        facing = left ? facing.getCounterClockWise() : facing.getClockWise();
        sync();
    }

    @Override
    public boolean place(Dir d, String blockId) {
        if (!AllowedBlocks.IDS.contains(blockId)) throw new ProgramError("Агенту нельзя ставить блок «" + blockId + "»");
        BlockPos t = target(d);
        ServerLevel level = level();
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft", blockId));
        if (block == Blocks.AIR) return false;
        BlockState current = level.getBlockState(t);
        if (!current.canBeReplaced()) return false;
        BlockState state = Block.updateFromNeighbourShapes(block.defaultBlockState(), level, t);
        if (!state.canSurvive(level, t)) return false;
        if (!level.isUnobstructed(state, t, CollisionContext.empty())) return false;
        level.setBlock(t, state, Block.UPDATE_ALL);
        SoundType sound = state.getSoundType();
        level.playSound(null, t, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        return true;
    }

    @Override
    public boolean destroy(Dir d) {
        BlockPos t = target(d);
        ServerLevel level = level();
        BlockState s = level.getBlockState(t);
        if (s.isAir() || s.getDestroySpeed(level, t) < 0) return false;
        return level.destroyBlock(t, false);
    }

    @Override
    public boolean detect(Dir d) {
        BlockPos t = target(d);
        ServerLevel level = level();
        return !level.getBlockState(t).getCollisionShape(level, t).isEmpty();
    }

    @Override
    public void teleportToPlayer() {
        ServerPlayer p = owner();
        Entity e = entity();
        if (e == null || e.level() != p.level()) {
            spawnAt(p);
        } else {
            pos = p.blockPosition();
            facing = p.getDirection();
            place(e);
        }
    }

    @Override
    public void say(String text) {
        owner().sendSystemMessage(Component.literal("[Агент] " + text));
    }
}
