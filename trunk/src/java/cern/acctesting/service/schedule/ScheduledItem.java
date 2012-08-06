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

import java.util.HashMap;
import java.util.Map;

/**
 * This class represents an {@link ItemToSchedule} that was scheduled to start at a specific point of time.
 * <p>
 * Objects of this class are immutable and can therefore not be modified after creation.
 * 
 * @author mgaletzk
 */
public class ScheduledItem implements Comparable<ScheduledItem> {

    private final ItemToSchedule itemToSchedule;
    private final int start;
    private final Map<Lane, Integer> ends;

    /**
     * Creates a new scheduled item with the specified start time
     * 
     * @param itemToSchedule the item that should be scheduled
     * @param start the start time of the new scheduled item
     */
    public ScheduledItem(ItemToSchedule itemToSchedule, int start) {
        this.itemToSchedule = itemToSchedule;
        this.start = start;
        ends = new HashMap<Lane, Integer>();
        for (Lane lane : itemToSchedule.getAffectedLanes()) {
            ends.put(lane, start + itemToSchedule.getDuration(lane));
        }
    }

    public ScheduledItem(ItemToSchedule itemToSchedule) {
        this(itemToSchedule, 0);
    }

    /**
     * @return the scheduled item
     */
    public ItemToSchedule getItemToSchedule() {
        return itemToSchedule;
    }

    /**
     * @return the start time of the scheduled item
     */
    public int getStart() {
        return start;
    }

    /**
     * Creates a new scheduled item from this one with a new start value. This method returns a new object beacuse
     * instances of this class are immutable.
     * 
     * @param newStart the start time of the new scheduled item
     * @return a new scheduled item like this one, but with a new start value
     */
    public ScheduledItem changeStart(int newStart) {
        return new ScheduledItem(itemToSchedule, newStart);
    }

    /**
     * Returns the end time of this scheduled item for a given lane. This is the same as adding the duration of the
     * ItemToSchedule to the start time of the scheduled item.
     * 
     * @param lane the lane the end value should be retireved for
     * @return the end value of this scheduled item
     */
    public int getEnd(Lane lane) {
        return ends.get(lane);
    }

    @Override
    public int hashCode() {
        return itemToSchedule.getId();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final ScheduledItem other = (ScheduledItem) obj;
        if (itemToSchedule.getId() != other.itemToSchedule.getId())
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "[Start: " + start + ", Item: " + itemToSchedule + "]";
    }

    @Override
    public int compareTo(ScheduledItem o) {
        int result = (start < o.start ? -1 : (start == o.start ? 0 : 1));
        if (result == 0) {
            result = (itemToSchedule.getId() < o.itemToSchedule.getId() ? -1 : (itemToSchedule.getId() == o.itemToSchedule.getId() ? 0 : 1));
        }
        return result;
    }
}
