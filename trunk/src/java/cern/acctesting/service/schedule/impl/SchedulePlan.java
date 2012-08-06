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

package cern.acctesting.service.schedule.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import cern.acctesting.service.schedule.ItemToSchedule;
import cern.acctesting.service.schedule.Lane;
import cern.acctesting.service.schedule.ScheduledItem;

public class SchedulePlan implements Cloneable {
    private final Map<Integer, ScheduledItem> scheduledItems;
    private final Map<Integer, Integer> startValues;
    private int makespan = 0;
    private final Map<ItemToSchedule, Collection<ItemToSchedule>> dependentItems;
    private final Set<ScheduledItem> fixedItems;

    public SchedulePlan() {
        scheduledItems = new HashMap<Integer, ScheduledItem>();
        startValues = new HashMap<Integer, Integer>();
        dependentItems = new HashMap<ItemToSchedule, Collection<ItemToSchedule>>();
        fixedItems = new HashSet<ScheduledItem>();
    }

    private SchedulePlan(Map<Integer, ScheduledItem> scheduledItems, Map<Integer, Integer> startValues,
            Map<ItemToSchedule, Collection<ItemToSchedule>> dependentItems, Set<ScheduledItem> fixedItems) {
        this.scheduledItems = new HashMap<Integer, ScheduledItem>(scheduledItems);
        this.startValues = new HashMap<Integer, Integer>(startValues);
        this.dependentItems = new HashMap<ItemToSchedule, Collection<ItemToSchedule>>(dependentItems);
        this.fixedItems = new HashSet<ScheduledItem>(fixedItems);

        makespan = 0;
        for (Integer newEnd : startValues.keySet()) {
            if (newEnd > makespan) {
                makespan = newEnd;
            }
        }
    }

    public List<ScheduledItem> getScheduledItems() {
        return new ArrayList<ScheduledItem>(scheduledItems.values());
    }
    
    public List<ScheduledItem> getFixedItems() {
        return new ArrayList<ScheduledItem>(fixedItems);
    }

    public ScheduledItem add(ItemToSchedule itemToSchedule, int start) {
        if (scheduledItems.containsKey(itemToSchedule.getId())) {
            throw new IllegalArgumentException("The plan already contains this item: " + itemToSchedule);
        }
        
        for (ItemToSchedule required : itemToSchedule.getRequiredItems()) {
            Collection<ItemToSchedule> items = dependentItems.get(required);
            if (items == null) {
                items = new HashSet<ItemToSchedule>();
            }
            items.add(itemToSchedule);
            dependentItems.put(required, items);
        }

        for (Lane lane : itemToSchedule.getAffectedLanes()) {
            if (start + itemToSchedule.getDuration(lane) > makespan) {
                makespan = start + itemToSchedule.getDuration(lane);
            }
        }

        ScheduledItem scheduledItem = new ScheduledItem(itemToSchedule, start);
        scheduledItems.put(scheduledItem.getItemToSchedule().getId(), scheduledItem);
        addToStartValues(scheduledItem);
        return scheduledItem;
    }

    public void fixateItem(ScheduledItem itemToFixate) {
        if (!scheduledItems.values().contains(itemToFixate)) {
            throw new IllegalArgumentException("The plan does not contain this scheduled item (start value error): " + itemToFixate);
        }
        fixedItems.add(itemToFixate);
    }

    public boolean canBeMoved(ScheduledItem itemToMove) {
        return !fixedItems.contains(itemToMove);
    }

    private void addToStartValues(ScheduledItem itemToAdd) {
        int start = itemToAdd.getStart();
        Integer count = startValues.get(start);
        count = (count == null) ? 1 : (count + 1);
        startValues.put(start, count);

        for (Lane lane : itemToAdd.getItemToSchedule().getAffectedLanes()) {
            int end = itemToAdd.getEnd(lane);
            count = startValues.get(end);
            count = (count == null) ? 1 : (count + 1);
            startValues.put(end, count);
        }
    }

    private void removeFromStartValues(ScheduledItem itemToRemove) {
        decreaseStartValue(itemToRemove, itemToRemove.getStart());
        for (Lane lane : itemToRemove.getItemToSchedule().getAffectedLanes()) {
            decreaseStartValue(itemToRemove, itemToRemove.getEnd(lane));
        }
    }

    private void decreaseStartValue(ScheduledItem item, int startValue) {
        Integer count;
        count = startValues.get(startValue);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("The plan does not contain this scheduled item (start value error): " + item);
        }
        count--;
        if (count == 0) {
            startValues.remove(startValue);
        } else {
            startValues.put(startValue, count);
        }
    }

    public SortedSet<Integer> getExistingStartValues() {
        SortedSet<Integer> start = new TreeSet<Integer>(startValues.keySet());
        start.add(0);
        return start;
    }

    @Override
    protected SchedulePlan clone() {
        return new SchedulePlan(scheduledItems, startValues, dependentItems, fixedItems);
    }

    public ScheduledItem moveScheduledItem(ItemToSchedule itemToMove, int newStart) {
        final int itemId = itemToMove.getId();
        ScheduledItem oldItem = scheduledItems.get(itemId);
        if (oldItem == null) {
            throw new IllegalArgumentException("The plan does not contain this scheduled item!");
        } else if (fixedItems.contains(oldItem)) {
            throw new IllegalArgumentException("The item " + oldItem + " has been fixated and must not be moved!");
        }
        ScheduledItem newItem = oldItem.changeStart(newStart);

        // update start values
        removeFromStartValues(oldItem);
        addToStartValues(newItem);

        // update item collection
        scheduledItems.put(itemId, newItem);

        updateMakespan();

        return newItem;
    }

    public int getMakespan() {
        return makespan;
    }

    @Override
    public String toString() {
        return "Scheduling Plan: " + scheduledItems;
    }

    public void shiftAll(int shiftValue) {
        // TODO: check if the shift leads to negative values
        Map<Integer, ScheduledItem> newScheduledItems = new HashMap<Integer, ScheduledItem>();
        for (ScheduledItem oldItem : scheduledItems.values()) {
            if (fixedItems.contains(oldItem)) {
                newScheduledItems.put(oldItem.getItemToSchedule().getId(), oldItem);
            } else {
                ScheduledItem newItem = oldItem.changeStart(oldItem.getStart() + shiftValue);
                newScheduledItems.put(newItem.getItemToSchedule().getId(), newItem);
            }
        }

        Map<Integer, Integer> newStartValues = new HashMap<Integer, Integer>();
        for (Entry<Integer, Integer> entry : startValues.entrySet()) {
            newStartValues.put(entry.getKey() + shiftValue, entry.getValue());
        }

        scheduledItems.clear();
        scheduledItems.putAll(newScheduledItems);

        startValues.clear();
        startValues.putAll(newStartValues);

        updateMakespan();
    }

    public PriorityQueue<ScheduledItem> getDependentItems(ItemToSchedule item) {
        PriorityQueue<ScheduledItem> dependent = new PriorityQueue<ScheduledItem>();
        Collection<ItemToSchedule> items = dependentItems.get(item);
        if (items != null) {
            for (ScheduledItem scheduled : scheduledItems.values()) {
                ItemToSchedule itemToSchedule = scheduled.getItemToSchedule();
                if (items.contains(itemToSchedule)) {
                    dependent.add(scheduled);
                }
            }
        }
        return dependent;
    }

    public ScheduledItem getScheduledItem(ItemToSchedule item) {
        return scheduledItems.get(item.getId());
    }

    public void unschedule(ScheduledItem scheduledItem) {
        if (fixedItems.contains(scheduledItem)) {
            throw new IllegalArgumentException("The item " + scheduledItem + " has been fixated and must not be unscheduled!");
        }

        // update start values
        removeFromStartValues(scheduledItem);

        // update item collection
        scheduledItems.remove(scheduledItem.getItemToSchedule().getId());

        updateMakespan();
    }

    private void updateMakespan() {
        makespan = 0;
        for (Integer newEnd : startValues.keySet()) {
            if (newEnd > makespan) {
                makespan = newEnd;
            }
        }
    }

    public void schedule(ScheduledItem scheduledItem) {
        // update start values
        addToStartValues(scheduledItem);

        // update item collection
        scheduledItems.put(scheduledItem.getItemToSchedule().getId(), scheduledItem);

        updateMakespan();
    }
}
