// @formatter:off
 /*******************************************************************************
 *
 * This file is part of JScheduleX.
 * 
 * Copyright (c) 2012 CERN.
 *
 * This software is distributed under the terms of the GNU Lesser General
 * Public Licence version 3 (LGPL Version 3), copied verbatim in the file “COPYING”
 * 
 * In applying this licence, CERN does not waive the privileges and immunities
 * granted to it by virtue of its status as an Intergovernmental Organization
 * or submit itself to any jurisdiction.
 * 
 ******************************************************************************/
// @formatter:on

package cern.acctesting.service.schedule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This represents the entity that the scheduling algorithm has to create the schedule from. This could be jobs on
 * different machines or a shifts of different workers. Each item is present on one or more {@link Lane}. So, one
 * ItemToSchedule does not necessarily have to correspond to exactly one job or shift, but groups severeal of them
 * together. The point is that all of these grouped entries will be scheduled to have the same start time.
 * <p>
 * For every grouped entry, the item contains one duration that corresponds to the execution time of that item on the
 * given lane. These duration may differ from each other, but they are always bigger than zero.
 * <p>
 * Objects of this class are immutable and can therefore not be modified after creation.
 * 
 * @author mgaletzk
 * @see ScheduledItem
 * @see Lane
 */
public class ItemToSchedule implements Comparable<ItemToSchedule> {

    private final Map<Lane, Integer> durations;
    private final List<ItemToSchedule> requiredItems;
    private final int id;
    private final int durationSummary;
    private final Collection<Lane> lanes;
    private int maxDuration;

    /**
     * Creates a new item.
     * 
     * @param id The unique id of the item.
     * @param durations the duration that this item requires on each lane.
     * @param requiredItems the items that are required by this item. This parameter can be used by some constraints and
     *            to create a good start configuration of the plan.
     */
    public ItemToSchedule(int id, Map<Lane, Integer> durations, Collection<ItemToSchedule> requiredItems) {
        if (durations == null || durations.isEmpty()) {
            throw new IllegalArgumentException("Every item to schedule must have at least one duration for a lane.");
        }

        this.id = id;
        this.requiredItems = new ArrayList<ItemToSchedule>(requiredItems);
        this.durations = new HashMap<Lane, Integer>(durations);
        lanes = new ArrayList<Lane>(durations.keySet());

        int allDurations = 0;
        for (Integer duration : durations.values()) {
            if (duration <= 0) {
                throw new IllegalArgumentException("Every item to schedule must have a minimum duration greater 0");
            }
            if (duration > maxDuration) {
                maxDuration = duration;
            }
            allDurations += duration;
        }
        durationSummary = allDurations;
    }

    /**
     * Returns the duration this item requires on the given lane.
     * 
     * @param lane the lane the duration should be retrieved for
     * @return the duration on the lane
     */
    public int getDuration(Lane lane) {
        return durations.get(lane);
    }

    /**
     * The result of this method is the same as looping through all affected lanes, retrieving the corresponding
     * duration and then selecting the longest one.
     * 
     * @return the maximum duration that this item requires on any lane
     */
    public int getMaxDuration() {
        return maxDuration;
    }

    /**
     * The result of this method is the same as looping through all affected lanes, retrieving the corresponding
     * duration and then summarizing them.
     * 
     * @return the summary of all durations of all affected lanes
     */
    public int getDurationSummary() {
        return durationSummary;
    }

    /**
     * @return the lanes that this item is active on (has a duration bigger than zero).
     */
    public Collection<Lane> getAffectedLanes() {
        return new ArrayList<Lane>(lanes);
    }

    /**
     * This parameter can be used by some constraints and to create a good start configuration of the plan, but it is
     * not necessarily set.
     * 
     * @return the items required by this item
     */
    public List<ItemToSchedule> getRequiredItems() {
        return new ArrayList<ItemToSchedule>(requiredItems);
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final ItemToSchedule other = (ItemToSchedule) obj;
        if (id != other.id)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "[id: " + id + ", durations: " + durations + ", required: " + requiredItems + "]";
    }

    /**
     * Returns the id of this item. The id is a unique identifier used to distinguish different items.
     * 
     * @return the id of the item
     */
    public int getId() {
        return id;
    }

    @Override
    public int compareTo(ItemToSchedule o) {
        int thisVal = this.id;
        int anotherVal = o.id;
        return (thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
    }
}
