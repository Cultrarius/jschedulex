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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import cern.acctesting.service.schedule.ItemToSchedule;
import cern.acctesting.service.schedule.Lane;
import cern.acctesting.service.schedule.ScheduledItem;
import cern.acctesting.service.schedule.exception.SchedulingException;
import cern.acctesting.service.schedule.impl.ViolationsManager.Violator;

public class HeuristicRepairScheduling {
    private SchedulePlan plan;
    private final ViolationsManager violationsManager;
    private final ConfigurationsManager configurationsManager;
    private final List<Collection<ScheduledItem>> snapshots;
    private int backsteps;

    public HeuristicRepairScheduling(ViolationsManager manager) {
	violationsManager = manager;
	configurationsManager = new ConfigurationsManager(violationsManager);
	snapshots = new ArrayList<Collection<ScheduledItem>>();
    }

    /**
     * The main entry point for scheduling. This method will move all items provided as parameters as it sees fit. If some of the items must
     * be fixed and should not be moved then provide them as an additional collection.
     * 
     * @param itemsToSchedule
     *            all the items that shall be scheduled. These do not contain the fixed scheduled items.
     * @return
     */
    public SchedulePlan schedule(Collection<ItemToSchedule> itemsToSchedule) {
	return schedule(itemsToSchedule, Collections.<ScheduledItem> emptyList());
    }

    /**
     * The main entry point for scheduling.
     * 
     * @param itemsToSchedule
     *            all the items that shall be scheduled. These do not contain the fixed scheduled items.
     * @param fixedItems
     *            the items in the scheduling plan, that must not be moved by the scheduler.
     * @return the plan created by scheduling the given items by using the constraints from the {@link ViolationsManager}.
     */
    public SchedulePlan schedule(Collection<ItemToSchedule> itemsToSchedule, Collection<ScheduledItem> fixedItems) {
	backsteps = 0;
	snapshots.clear();
	createStartPlan(itemsToSchedule, fixedItems);
	if (!itemsToSchedule.isEmpty()) {
	    violationsManager.initialize(plan);
	    satisfyConstraints();
	}
	return plan;
    }

    private void satisfyConstraints() {
	boolean hardConstraintsSatisfied = false;
	Violator violator = violationsManager.getBiggestViolator(null);

	if (violator != null && violator.getHardViolationsValue() == 0) {
	    hardConstraintsSatisfied = true;
	    if (violator.getSoftViolationsValue() == 0) {
		violator = null;
	    }
	}

	while (violator != null) {
	    configurationsManager.resetConfigurations(violator, plan);

	    if (plan.canBeMoved(violator.getScheduledItem())) {
		boolean foundConfiguration = false;
		for (int possibleStart : plan.getExistingStartValues()) {
		    if (foundConfiguration
			    && plan.getMakespan() < (violator.getScheduledItem().getItemToSchedule().getMaxDuration() + possibleStart)) {
			// all following start values would not be accepted over the current best one
			break;
		    }
		    foundConfiguration |= configurationsManager.addConfiguration(violator, plan, possibleStart);
		}
	    }

	    boolean wasPossible = configurationsManager.applyBestConfiguration(plan);

	    if (!wasPossible) {
		configurationsManager.applyReferenceConfiguration(plan);
		backsteps++;
		violator = violationsManager.getBiggestViolator(violator);

		if (violator == null && hardConstraintsSatisfied) {
		    /*
		     * the hard constraints are satisfied and the soft constraints can not be refined any further without breaking something
		     */
		    break;
		}
		else if (violator == null && !hardConstraintsSatisfied) {
		    /*
		     * a suitable place could not be found, not even at the end of the plan. The reason could be that some constraints are
		     * violated - after moving the item to the end of the plan - even though they are not violated now. The plan is captured
		     * in a local optimum and has to be lifted out of it in order to proceed its search.
		     */
		    escapeFromLocalOptimum();
		}
		else {
		    continue;
		}
	    }

	    snapshots.add(plan.getScheduledItems());
	    violator = violationsManager.getBiggestViolator(null);
	    if (violator == null || (!hardConstraintsSatisfied && violator.getHardViolationsValue() == 0)) {
		hardConstraintsSatisfied = true;
	    }
	}
    }

    private void escapeFromLocalOptimum() {
	Violator violator = violationsManager.getBiggestViolator(null);
	configurationsManager.resetPlanConfigurations();
	configurationsManager.addPlanConfiguration(plan);

	tryToMoveRequiredItems(violator);
	SchedulePlan bestPlan = configurationsManager.getBestPlanConfiguration();
	if (bestPlan == plan) {
	    tryToMoveRigth(violator);
	    bestPlan = configurationsManager.getBestPlanConfiguration();
	}
	if (bestPlan == plan) {
	    tryToMoveLeft(violator);
	    bestPlan = configurationsManager.getBestPlanConfiguration();
	}

	if (bestPlan == plan) {
	    throw new SchedulingException(
		    "Unable to esacape from local optimum in scheduling plan. The scheduling can not be completed! Plan: " + plan);
	}
	else {
	    violationsManager.planHasBeenUpdated(plan, bestPlan);
	    plan = bestPlan;
	}
    }

    private void tryToMoveRequiredItems(Violator violator) {
	SchedulePlan newPlan = plan.clone();

	Map<ItemToSchedule, DependencyNode> dependencyLevels = new HashMap<ItemToSchedule, DependencyNode>();
	addToTree(violator.getScheduledItem().getItemToSchedule(), dependencyLevels, newPlan, 0);

	SortedSet<DependencyNode> dependencyTree = new TreeSet<DependencyNode>(dependencyLevels.values());

	for (DependencyNode dependencyNode : dependencyTree) {
	    newPlan.unschedule(dependencyNode.scheduledItem);
	}

	for (DependencyNode dependencyNode : dependencyTree) {
	    ViolatorValues bestValues = null;
	    ScheduledItem bestItem = null;
	    for (int possibleStart : newPlan.getExistingStartValues()) {
		ScheduledItem newItem = new ScheduledItem(dependencyNode.scheduledItem.getItemToSchedule(), possibleStart);
		ViolatorValues violatorValues = violationsManager.checkViolationsForItem(newItem, newPlan);
		if (bestValues == null
			|| (violatorValues.hardViolationsValue < bestValues.hardViolationsValue)
			|| (violatorValues.hardViolationsValue == bestValues.hardViolationsValue && violatorValues.softViolationsValue < bestValues.softViolationsValue)) {
		    bestValues = violatorValues;
		    bestItem = newItem;
		}
	    }
	    newPlan.schedule(bestItem);
	}

	configurationsManager.addPlanConfiguration(newPlan);
    }

    private void addToTree(ItemToSchedule item, Map<ItemToSchedule, DependencyNode> dependencyLevels, SchedulePlan newPlan, int level) {
	DependencyNode node = dependencyLevels.get(item);
	if (node == null) {
	    node = new DependencyNode(newPlan.getScheduledItem(item), level);
	    dependencyLevels.put(item, node);
	}
	if (node.level < level) {
	    node.level = level;
	}
	for (ScheduledItem scheduled : newPlan.getDependentItems(item)) {
	    if (newPlan.canBeMoved(scheduled)) {
		addToTree(scheduled.getItemToSchedule(), dependencyLevels, newPlan, level + 1);
	    }
	}
    }

    private class DependencyNode implements Comparable<DependencyNode> {

	private final ScheduledItem scheduledItem;
	private int level;

	public DependencyNode(ScheduledItem scheduledItem, int level) {
	    this.scheduledItem = scheduledItem;
	    this.level = level;
	}

	@Override
	public int compareTo(DependencyNode o) {
	    int result = (level < o.level ? -1 : (level == o.level ? 0 : 1));
	    if (result == 0) {
		result = (scheduledItem.getStart() < o.scheduledItem.getStart() ? -1 : (scheduledItem.getStart() == o.scheduledItem
			.getStart() ? 0 : 1));
	    }
	    return result;
	}

	@Override
	public int hashCode() {
	    return scheduledItem.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
	    if (this == obj)
		return true;
	    if (obj == null)
		return false;
	    if (getClass() != obj.getClass())
		return false;
	    return scheduledItem.equals(((DependencyNode) obj).scheduledItem);
	}
    }

    private void tryToMoveLeft(Violator violator) {
	/*
	 * shift the complete plan to the rigth and move the items to the left (before the start of the current plan)
	 */
	SchedulePlan toStartPlan = plan.clone();
	toStartPlan.shiftAll(plan.getMakespan());
	Collection<ScheduledItem> items = new HashSet<ScheduledItem>();
	items.add(toStartPlan.getScheduledItem(violator.getScheduledItem().getItemToSchedule()));

	shiftAndLock(items, new HashSet<ScheduledItem>(), toStartPlan, -plan.getMakespan());
	configurationsManager.addPlanConfiguration(toStartPlan);

    }

    private void tryToMoveRigth(Violator violator) {
	/*
	 * move the items to the rigth (to the end of the current plan)
	 */
	SchedulePlan toEndPlan;
	toEndPlan = plan.clone();
	Collection<ScheduledItem> items = new HashSet<ScheduledItem>();
	items.add(violator.getScheduledItem());

	shiftAndLock(items, new HashSet<ScheduledItem>(), toEndPlan, plan.getMakespan());
	configurationsManager.addPlanConfiguration(toEndPlan);

    }

    private void shiftAndLock(Collection<ScheduledItem> items, Collection<ScheduledItem> lockedItems, SchedulePlan toEndPlan, int shiftValue) {

	// retrieve all items which items are violated rigth now by the items to shift
	Collection<ScheduledItem> violatedItems = new HashSet<ScheduledItem>();
	for (ScheduledItem itemToShift : items) {
	    violatedItems.addAll(violationsManager.getHardViolatedItems(itemToShift, toEndPlan));
	}

	// shift the items that need it and lock them
	Collection<ScheduledItem> shiftedItems = new HashSet<ScheduledItem>();
	for (ScheduledItem itemToShift : items) {
	    shiftedItems.add(toEndPlan.moveScheduledItem(itemToShift.getItemToSchedule(), itemToShift.getStart() + shiftValue));
	}
	lockedItems.addAll(shiftedItems);

	// check which items are violated after the shift
	Collection<ScheduledItem> newViolatedItems = new HashSet<ScheduledItem>();
	for (ScheduledItem shiftedItem : shiftedItems) {
	    newViolatedItems.addAll(violationsManager.getHardViolatedItems(shiftedItem, toEndPlan));
	}

	// check if other, additional items are violated because of the shift
	newViolatedItems.removeAll(violatedItems);
	if (!newViolatedItems.isEmpty()) {

	    // check if one of the newly violated items has already been shifted before and is locked now
	    Collection<ScheduledItem> checkCollection = new HashSet<ScheduledItem>(newViolatedItems);
	    checkCollection.retainAll(lockedItems);
	    if (!checkCollection.isEmpty()) { throw new SchedulingException(
		    "The current plan can not be scheduled because it most likely contains a circular constraint of some kind. Dumping variable assignments. "
			    + "lockedItems: " + lockedItems + ", newViolatedItems: " + newViolatedItems + ", toEndPlan: " + toEndPlan
			    + ", original plan: " + plan); }

	    // recursively shift the new violated items and lock them
	    shiftAndLock(newViolatedItems, lockedItems, toEndPlan, shiftValue);
	}
    }

    private void createStartPlan(Collection<ItemToSchedule> itemsToSchedule, Collection<ScheduledItem> fixedItems) {
	plan = new SchedulePlan();

	// @formatter:off
        /*
         * possibilities:
         * - place them all at 0 (all overlapping) 
         * - place them as they come to the current possible end (<---- currently implemented)
         * - sort them according to duration summary and place them to the current possible end (big to small) 
         * - as before, but inverse (small to big) 
         * - shuffle them and place them to the current possible end
         */
         // @formatter:off
        
        for (ScheduledItem fixedItem : fixedItems) {
            plan.schedule(fixedItem);
            plan.fixateItem(fixedItem);
        }
        

        Map<Lane, Integer> maximumValues = new HashMap<Lane, Integer>();
        for (ItemToSchedule itemToSchedule : itemsToSchedule) {
            int start = getPossibleStart(maximumValues, itemToSchedule);
            ScheduledItem scheduledItem = plan.add(itemToSchedule, start);
            updateMaxLaneValues(maximumValues, scheduledItem);
        }
        
        // take a snapshot
        snapshots.add(plan.getScheduledItems());
    }

    private void updateMaxLaneValues(Map<Lane, Integer> maximumValues, ScheduledItem scheduledItem) {
        for (Lane lane : scheduledItem.getItemToSchedule().getAffectedLanes()) {
            maximumValues.put(lane, scheduledItem.getStart() + scheduledItem.getItemToSchedule().getDuration(lane));
        }
    }

    private int getPossibleStart(Map<Lane, Integer> maximumValues, ItemToSchedule itemToSchedule) {
        int start = 0;
        for (Lane lane : itemToSchedule.getAffectedLanes()) {
            Integer currentMaximum = maximumValues.get(lane);
            if (currentMaximum != null && currentMaximum > start) {
                start = currentMaximum;
            }
        }
        return start;
    }

    public List<Collection<ScheduledItem>> getSnapshots() {
        return new ArrayList<Collection<ScheduledItem>>(snapshots);
    }

    public int getBacksteps() {
        return backsteps;
    }

}
