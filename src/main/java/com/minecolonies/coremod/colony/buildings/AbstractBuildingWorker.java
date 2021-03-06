package com.minecolonies.coremod.colony.buildings;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.coremod.MineColonies;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.coremod.colony.CitizenData;
import com.minecolonies.coremod.colony.Colony;
import com.minecolonies.coremod.colony.ColonyView;
import com.minecolonies.coremod.colony.jobs.AbstractJob;
import com.minecolonies.coremod.entity.EntityCitizen;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemFood;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.minecolonies.api.util.constant.ToolLevelConstants.TOOL_LEVEL_MAXIMUM;
import static com.minecolonies.api.util.constant.ToolLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;

/**
 * The abstract class for each worker building.
 */
public abstract class AbstractBuildingWorker extends AbstractBuildingHut
{
    /**
     * Minimal level to ask for wood tools. (WOOD_HUT_LEVEL + 1 == stone)
     */
    public static final int WOOD_HUT_LEVEL = 0;

    /**
     * Tag used to store the worker to nbt.
     */
    private static final String TAG_WORKER = "worker";

    /**
     * Tag to store the id to NBT.
     */
    private static final String TAG_ID = "workerId";

    /**
     * List of workers assosiated to the building.
     */
    private final List<CitizenData> workers = new ArrayList();

    /**
     * The abstract constructor of the building.
     *
     * @param c the colony
     * @param l the position
     */
    public AbstractBuildingWorker(@NotNull final Colony c, final BlockPos l)
    {
        super(c, l);
        keepX.put(itemStack -> !ItemStackUtils.isEmpty(itemStack) && itemStack.getItem() instanceof ItemFood, getBuildingLevel() * 2);
    }

    /**
     * The abstract method which creates a job for the building.
     *
     * @param citizen the citizen to take the job.
     * @return the Job.
     */
    @NotNull
    public abstract AbstractJob createJob(CitizenData citizen);

    /**
     * Get the main worker of the building (the first in the list).
     *
     * @return the matching CitizenData.
     */
    public CitizenData getMainWorker()
    {
        if (workers.isEmpty())
        {
            return null;
        }
        return workers.get(0);
    }

    /**
     * Check if a certain ItemStack is in the request of a worker.
     *
     * @param stack the stack to chest.
     * @return true if so.
     */
    public boolean isItemStackInRequest(@Nullable final ItemStack stack)
    {
        if (stack == null || stack.getItem() == null)
        {
            return false;
        }

        for (final CitizenData data : getWorker())
        {
            for (final IRequest request : getOpenRequests(data))
            {
                if (request.getDelivery().isItemEqualIgnoreDurability(stack))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the worker of the current building.
     *
     * @return {@link CitizenData} of the current building
     */
    public List<CitizenData> getWorker()
    {
        return new ArrayList<>(workers);
    }

    /**
     * Set the worker of the current building.
     *
     * @param citizen {@link CitizenData} of the worker
     */
    public void setWorker(final CitizenData citizen)
    {
        if (workers.contains(citizen))
        {
            return;
        }

        // If we set a worker, inform it of such
        if (citizen != null)
        {
            final EntityCitizen tempCitizen = citizen.getCitizenEntity();
            if (tempCitizen != null)
            {
                if(!tempCitizen.getLastJob().isEmpty() && !tempCitizen.getLastJob().equals(getJobName()))
                {
                    citizen.resetExperienceAndLevel();
                }
                tempCitizen.setLastJob(getJobName());
            }
            workers.add(citizen);
            citizen.setWorkBuilding(this);
        }

        markDirty();
    }

    /**
     * Returns the {@link net.minecraft.entity.Entity} of the worker.
     *
     * @return {@link net.minecraft.entity.Entity} of the worker
     */
    @Nullable
    public List<EntityCitizen> getWorkerEntities()
    {
        final List<EntityCitizen> entities = new ArrayList<>();
        for (final CitizenData data : workers)
        {
            if (data != null && data.getCitizenEntity() != null)
            {
                entities.add(data.getCitizenEntity());
            }
        }

        return entities;
    }

    @Override
    public void readFromNBT(@NotNull final NBTTagCompound compound)
    {
        super.readFromNBT(compound);
        if (compound.hasKey(TAG_WORKER))
        {
            try
            {
                final NBTTagList workersTagList = compound.getTagList(TAG_WORKER, Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < workersTagList.tagCount(); ++i)
                {
                    final CitizenData data = getColony().getCitizenManager().getCitizen(workersTagList.getCompoundTagAt(i).getInteger(TAG_ID));
                    if (data != null)
                    {
                        data.setWorkBuilding(this);
                        workers.add(data);
                    }
                }
            }
            catch (final Exception e)
            {
                MineColonies.getLogger().warn("Warning: Updating data structures:", e);
                final CitizenData worker = getColony().getCitizenManager().getCitizen(compound.getInteger(TAG_WORKER));
                workers.add(worker);
                if (worker != null)
                {
                    worker.setWorkBuilding(this);
                }
            }
        }
    }

    @Override
    public void writeToNBT(@NotNull final NBTTagCompound compound)
    {
        super.writeToNBT(compound);

        if (!workers.isEmpty())
        {
            @NotNull final NBTTagList workersTagList = new NBTTagList();
            for (@NotNull final CitizenData data : workers)
            {
                if (data != null)
                {
                    final NBTTagCompound idCompound = new NBTTagCompound();
                    idCompound.setInteger(TAG_ID, data.getId());
                    workersTagList.appendTag(idCompound);
                }
            }
            compound.setTag(TAG_WORKER, workersTagList);
        }
    }

    @Override
    public void onDestroyed()
    {
        if (hasEnoughWorkers())
        {
            // EntityCitizen will detect the workplace is gone and fix up it's
            // Entity properly
            workers.clear();
        }

        super.onDestroyed();
    }

    /**
     * Executed when a new day start.
     */
    @Override
    public void onWakeUp()
    {

    }

    /**
     * Returns whether or not the building has a worker.
     *
     * @return true if building has worker, otherwise false.
     */
    public boolean hasEnoughWorkers()
    {
        return !workers.isEmpty();
    }

    @Override
    public void removeCitizen(final CitizenData citizen)
    {
        if (isWorker(citizen))
        {
            citizen.setWorkBuilding(null);
            workers.remove(citizen);
        }
        markDirty();
    }

    /**
     * Returns if the {@link CitizenData} is the same as the worker.
     *
     * @param citizen {@link CitizenData} you want to compare
     * @return true if same citizen, otherwise false
     */
    public boolean isWorker(final CitizenData citizen)
    {
        return workers.contains(citizen);
    }

    /**
     * @see AbstractBuilding#onUpgradeComplete(int)
     */
    @Override
    public void onWorldTick(@NotNull final TickEvent.WorldTickEvent event)
    {
        super.onWorldTick(event);

        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        // If we have no active worker, grab one from the Colony
        // TODO Maybe the Colony should assign jobs out, instead?
        if (!hasEnoughWorkers()
              && (getBuildingLevel() > 0 || this instanceof BuildingBuilder)
              && !this.getColony().isManualHiring())
        {
            final CitizenData joblessCitizen = getColony().getCitizenManager().getJoblessCitizen();
            if (joblessCitizen != null)
            {
                setWorker(joblessCitizen);
            }
        }
    }

    /**
     * The abstract method which returns the name of the job.
     *
     * @return the job name.
     */
    @NotNull
    public abstract String getJobName();

    @Override
    public void serializeToView(@NotNull final ByteBuf buf)
    {
        super.serializeToView(buf);
        buf.writeInt(workers.size());
        for (final CitizenData data : workers)
        {
            buf.writeInt(data == null ? 0 : data.getId());
        }
    }

    /**
     * Returns the first worker in the list.
     *
     * @return the EntityCitizen of that worker.
     */
    public EntityCitizen getMainWorkerEntity()
    {
        if (workers.isEmpty())
        {
            return null;
        }
        return workers.get(0).getCitizenEntity();
    }

    /**
     * Get the max tool level useable by the worker.
     *
     * @return the integer.
     */
    public int getMaxToolLevel()
    {
        if (getBuildingLevel() >= getMaxBuildingLevel())
        {
            return TOOL_LEVEL_MAXIMUM;
        }
        else if (getBuildingLevel() <= WOOD_HUT_LEVEL)
        {
            return TOOL_LEVEL_WOOD_OR_GOLD;
        }
        return getBuildingLevel() - WOOD_HUT_LEVEL;
    }

    /**
     * Available skills of the citizens.
     */
    public enum Skill
    {
        STRENGTH,
        ENDURANCE,
        CHARISMA,
        INTELLIGENCE,
        DEXTERITY,
        PLACEHOLDER
    }

    /**
     * AbstractBuildingWorker View for clients.
     */
    public static class View extends AbstractBuildingHut.View
    {
        /**
         * List of the worker ids.
         */
        private final List<Integer> workerIDs = new ArrayList<>();

        /**
         * Creates the view representation of the building.
         *
         * @param c the colony.
         * @param l the location.
         */
        public View(final ColonyView c, @NotNull final BlockPos l)
        {
            super(c, l);
        }

        /**
         * Returns the id of the worker.
         *
         * @return 0 if there is no worker else the correct citizen id.
         */
        public List<Integer> getWorkerId()
        {
            return new ArrayList<>(workerIDs);
        }

        /**
         * Sets the id of the worker.
         *
         * @param workerId the id to set.
         */
        public void addWorkerId(final int workerId)
        {
            workerIDs.add(workerId);
        }

        @Override
        public void deserialize(@NotNull final ByteBuf buf)
        {
            super.deserialize(buf);
            final int size = buf.readInt();
            workerIDs.clear();
            for (int i = 0; i < size; i++)
            {
                workerIDs.add(buf.readInt());
            }
        }

        @NotNull
        public Skill getPrimarySkill()
        {
            return Skill.PLACEHOLDER;
        }

        @NotNull
        public Skill getSecondarySkill()
        {
            return Skill.PLACEHOLDER;
        }

        /**
         * Remove a worker from the list.
         *
         * @param id the id to remove.
         */
        public void removeWorkerId(final int id)
        {
            for (int i = 0; i < workerIDs.size(); i++)
            {
                final int workerId = workerIDs.get(i);
                if (workerId == id)
                {
                    workerIDs.remove(i);
                }
            }
        }

        /**
         * Check if it has enough workers.
         *
         * @return true if so.
         */
        public boolean hasEnoughWorkers()
        {
            return !workerIDs.isEmpty();
        }
    }
}
