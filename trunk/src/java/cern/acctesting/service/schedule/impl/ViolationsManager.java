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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import cern.acctesting.service.schedule.ItemToSchedule;
import cern.acctesting.service.schedule.ScheduledItem;
import cern.acctesting.service.schedule.constraint.ConstraintDecision;
import cern.acctesting.service.schedule.constraint.ItemPairConstraint;
import cern.acctesting.service.schedule.constraint.SingleItemConstraint;
import cern.acctesting.service.schedule.constraint.UpdateableConstraint;
import cern.acctesting.service.schedule.exception.SchedulingException;
import cern.acctesting.service.schedule.exception.ViolatorUpdateInvalid;
import cern.acctesting.service.schedule.impl.Predictor.ConflictPrediction;

/**
 * This class is responsible for the management of all constraints and their violations by scheduled items. It tries to manage constraint
 * violations in an efficient way and also handles updates of the {@link SchedulePlan}.
 * 
 * @author Michael
 * 
 */
public class ViolationsManager {

    private final List<SingleItemConstraint> singleConstraints;
    private final List<ItemPairConstraint> pairConstraints;

    /**
     * The constraint map defines which items are connected to other items through one or more constraints.
     */
    private final Map<ItemToSchedule, Set<ConstraintPartner>> constraintMap;

    /**
     * The violationsTree is an ordered set of all the constraint violators (ordered by their violation value). It is similar to an ordered
     * list, but it guarantees that an item can be contained at most once. In addition, it provides efficient log(n) operations to add and
     * remove items.
     */
    private final TreeSet<Violator> violationsTree;
    private final Map<ItemToSchedule, Violator> violationsMapping;
    private Predictor predictor;

    /**
     * Creates a new instance of the manager that uses the given constraints to determine schedule violations.
     * 
     * @param singleConstraints
     *            all the constraints that apply to single items
     * @param pairConstraints
     *            all the constraints that apply to a pair of items
     */
    public ViolationsManager(List<SingleItemConstraint> singleConstraints, List<ItemPairConstraint> pairConstraints) {
	this.singleConstraints = singleConstraints;
	this.pairConstraints = pairConstraints;
	constraintMap = new HashMap<ItemToSchedule, Set<ConstraintPartner>>();
	violationsTree = new TreeSet<Violator>();
	violationsMapping = new HashMap<ItemToSchedule, Violator>();
    }

    /**
     * Initializes the manager with the given {@link SchedulePlan}. This is used to determine which items violate which constraints.
     * 
     * @param plan
     *            the plan containing scheduled items
     */
    public void initialize(SchedulePlan plan) {
	constraintMap.clear();
	violationsTree.clear();
	updateConstraints();
	List<ItemToSchedule> items = new ArrayList<ItemToSchedule>();
	for (ScheduledItem item : plan.getScheduledItems()) {
	    items.add(item.getItemToSchedule());
	}

	if (items.isEmpty()) { return; }
	initializeConstraintMap(items);
	initializeViolationTree(plan);
	predictor = new Predictor(plan, constraintMap);
    }

    private void updateConstraints() {
	for (SingleItemConstraint constraint : singleConstraints) {
	    if (constraint instanceof UpdateableConstraint) {
		((UpdateableConstraint) constraint).updateConstraint();
	    }
	}

	for (ItemPairConstraint constraint : pairConstraints) {
	    if (constraint instanceof UpdateableConstraint) {
		((UpdateableConstraint) constraint).updateConstraint();
	    }
	}
    }

    private void initializeViolationTree(SchedulePlan plan) {
	for (ScheduledItem item : plan.getScheduledItems()) {
	    if (plan.getFixedItems().contains(item)) {
		continue;
	    }
	    ItemToSchedule itemToSchedule = item.getItemToSchedule();
	    Set<ConstraintPartner> pairs = constraintMap.get(itemToSchedule);
	    if (pairs != null && !pairs.isEmpty()) {
		checkPairConstraints(item, plan, pairs, false);
	    }

	    Violator violator = new Violator(item);
	    violationsTree.add(violator);
	    violationsMapping.put(itemToSchedule, violator);
	}
    }

    private void initializeConstraintMap(List<ItemToSchedule> itemsToSchedule) {
	Iterator<ItemToSchedule> outer = itemsToSchedule.iterator();
	while (outer.hasNext()) {
	    ItemToSchedule itemOuter = outer.next();
	    ListIterator<ItemToSchedule> inner = itemsToSchedule.listIterator(itemsToSchedule.size());
	    while (inner.hasPrevious()) {
		ItemToSchedule itemInner = inner.previous();
		if (itemOuter == itemInner) {
		    break;
		}
		List<ItemPairConstraint> constraints = new ArrayList<ItemPairConstraint>(pairConstraints.size());
		for (ItemPairConstraint constraint : pairConstraints) {
		    if (constraint.needsChecking(itemOuter, itemInner)) {
			constraints.add(constraint);
		    }
		}

		if (!constraints.isEmpty()) {
		    ViolationsContainer container = new ViolationsContainer(new ArrayList<ConstraintDecision>(pairConstraints.size()));
		    addPair(itemOuter, itemInner, container, constraints);
		    addPair(itemInner, itemOuter, container, constraints);
		}
	    }

	    if (!constraintMap.containsKey(itemOuter)) {
		constraintMap.put(itemOuter, new HashSet<ViolationsManager.ConstraintPartner>());
	    }
	}
    }

    private void addPair(ItemToSchedule item1, ItemToSchedule item2, ViolationsContainer container, List<ItemPairConstraint> constraints) {
	Set<ConstraintPartner> pairs = constraintMap.get(item1);
	if (pairs == null) {
	    pairs = new HashSet<ConstraintPartner>();
	}
	pairs.add(new ConstraintPartner(item2, container, constraints));
	constraintMap.put(item1, pairs);
    }

    public ViolatorUpdate tryViolatorUpdate(ScheduledItem newItem, SchedulePlan plan) throws ViolatorUpdateInvalid {
	ItemToSchedule itemToSchedule = newItem.getItemToSchedule();
	Violator violator = violationsMapping.get(itemToSchedule);

	ViolatorValues newValues = new ViolatorValues();

	calculateSingleConstraintValues(newItem, violator, newValues);

	ConflictPrediction prediction = predictor.predictConflicts(newItem);
	checkUpdateValid(violator, newValues.hardViolationsValue + prediction.getDefinedHardConflictValue(), newValues.softViolationsValue);

	Set<ConstraintPartner> partners = constraintMap.get(itemToSchedule);
	List<PartnerUpdate> partnerUpdates = new ArrayList<PartnerUpdate>(partners == null ? 0 : partners.size());
	if (partners != null) {
	    for (ConstraintPartner partner : partners) {
		ScheduledItem partnerItem = plan.getScheduledItem(partner.getPartnerItem());
		ViolatorValues newPartnerValues = new ViolatorValues();
		calculatePairConstraintValues(newItem, violator, newValues, partner, partnerItem, newPartnerValues);
		updatePartnerViolator(partnerUpdates, partner, partnerItem, newPartnerValues);
	    }
	}

	Violator updatedViolator = new Violator(newItem, newValues.hardViolationsValue, newValues.softViolationsValue);

	return new ViolatorUpdate(updatedViolator, partnerUpdates);
    }

    private void updatePartnerViolator(List<PartnerUpdate> partnerUpdates, ConstraintPartner partner, ScheduledItem partnerItem,
	    ViolatorValues newPartnerValues) {
	Violator partnerViolator = violationsMapping.get(partner.getPartnerItem());
	if (partnerViolator != null) {
	    ViolatorValues oldParterValues = partner.violationsContainer.values;
	    int newHardValue = partnerViolator.hardViolationsValue
		    + (newPartnerValues.hardViolationsValue - oldParterValues.hardViolationsValue);
	    int newSoftValue = partnerViolator.softViolationsValue
		    + (newPartnerValues.softViolationsValue - oldParterValues.softViolationsValue);
	    Violator updatedPartner = new Violator(partnerItem, newHardValue, newSoftValue);
	    partnerUpdates.add(new PartnerUpdate(partner, newPartnerValues, partnerViolator, updatedPartner));
	}
    }

    private void calculatePairConstraintValues(ScheduledItem newItem, Violator violator, ViolatorValues newValues,
	    ConstraintPartner partner, ScheduledItem partnerItem, ViolatorValues newPartnerValues) throws ViolatorUpdateInvalid {
	for (ItemPairConstraint constraint : partner.getConstraints()) {
	    ConstraintDecision decision = constraint.check(newItem, partnerItem);
	    if (!decision.isFulfilled()) {
		if (decision.isHardConstraint()) {
		    newValues.hardViolationsValue += decision.getViolationValue();
		    newPartnerValues.hardViolationsValue += decision.getViolationValue();
		}
		else {
		    newValues.softViolationsValue += decision.getViolationValue();
		    newPartnerValues.softViolationsValue += decision.getViolationValue();
		}

		checkUpdateValid(violator, newValues.hardViolationsValue, newValues.softViolationsValue);
	    }
	}
    }

    private void calculateSingleConstraintValues(ScheduledItem newItem, Violator violator, ViolatorValues newValues)
	    throws ViolatorUpdateInvalid {
	for (SingleItemConstraint constraint : singleConstraints) {
	    ConstraintDecision decision = constraint.check(newItem);
	    if (!decision.isFulfilled()) {
		if (decision.isHardConstraint()) {
		    newValues.hardViolationsValue += decision.getViolationValue();
		}
		else {
		    newValues.softViolationsValue += decision.getViolationValue();
		}
	    }

	    checkUpdateValid(violator, newValues.hardViolationsValue, newValues.softViolationsValue);
	}
    }

    protected class PartnerUpdate {
	private final ConstraintPartner partner;
	private final ViolatorValues newContainerValues;
	private final Violator oldViolator;
	private final Violator updatedViolator;

	public PartnerUpdate(ConstraintPartner partner, ViolatorValues newContainerValues, Violator oldViolator, Violator updatedViolator) {
	    this.partner = partner;
	    this.newContainerValues = newContainerValues;
	    this.oldViolator = oldViolator;
	    this.updatedViolator = updatedViolator;
	}

    }

    public void updateViolator(ViolatorUpdate update) {
	Violator newViolator = update.getUpdatedViolator();
	ItemToSchedule itemToSchedule = newViolator.scheduledItem.getItemToSchedule();
	Violator oldViolator = violationsMapping.get(itemToSchedule);

	for (PartnerUpdate partnerUpdate : update.getPartnerUpdates()) {
	    partnerUpdate.partner.violationsContainer.updateValues(partnerUpdate.newContainerValues);
	    if (violationsTree.remove(partnerUpdate.oldViolator)) {
		violationsTree.add(partnerUpdate.updatedViolator);
	    }

	    violationsMapping.put(partnerUpdate.updatedViolator.scheduledItem.getItemToSchedule(), partnerUpdate.updatedViolator);
	}

	// XXX maybe needed for fixed items?
	// if (!violationsTree.remove(oldViolator)) {
	// violationsTree.add(newViolator);
	// }

	violationsTree.remove(oldViolator);
	violationsTree.add(newViolator);

	violationsMapping.put(itemToSchedule, newViolator);

	predictor.itemWasMoved(itemToSchedule);
    }

    private void checkPairConstraints(ScheduledItem scheduledItem, SchedulePlan plan, Set<ConstraintPartner> partners,
	    boolean updateConnected, ViolatorValues newValues, Violator violator) throws ViolatorUpdateInvalid {
	for (ConstraintPartner partner : partners) {
	    ScheduledItem partnerItem = plan.getScheduledItem(partner.getPartnerItem());
	    ViolationsContainer container = partner.violationsContainer;

	    ViolatorValues oldParterValues = null;
	    if (updateConnected) {
		oldParterValues = container.values;
	    }

	    List<ConstraintDecision> violations = new ArrayList<ConstraintDecision>(partner.getConstraints().size());
	    for (ItemPairConstraint constraint : partner.getConstraints()) {
		ConstraintDecision decision = constraint.check(scheduledItem, partnerItem);
		if (!decision.isFulfilled()) {
		    violations.add(decision);
		    if (newValues != null) {
			if (decision.isHardConstraint()) {
			    newValues.hardViolationsValue += decision.getViolationValue();
			}
			else {
			    newValues.softViolationsValue += decision.getViolationValue();
			}

			checkUpdateValid(violator, newValues.hardViolationsValue, newValues.softViolationsValue);
		    }
		}
	    }
	    container.updateValues(violations);

	    if (updateConnected) {
		/*
		 * update the violations tree for the partner node
		 */
		updatePartner(partner, partnerItem, container, oldParterValues);
	    }
	}
    }

    private void updatePartner(ConstraintPartner partner, ScheduledItem partnerItem, ViolationsContainer container,
	    ViolatorValues oldParterValues) {
	Violator partnerViolator = violationsMapping.get(partner.getPartnerItem());
	if (partnerViolator != null) {
	    ViolatorValues newParterValues = container.values;
	    if (!violationsTree.remove(partnerViolator)) { throw new SchedulingException("Fixed item?"); }
	    int newHardValue = partnerViolator.hardViolationsValue
		    + (newParterValues.hardViolationsValue - oldParterValues.hardViolationsValue);
	    int newSoftValue = partnerViolator.softViolationsValue
		    + (newParterValues.softViolationsValue - oldParterValues.softViolationsValue);
	    Violator updatedViolator = new Violator(partnerItem, newHardValue, newSoftValue);
	    violationsTree.add(updatedViolator);
	    violationsMapping.put(partnerItem.getItemToSchedule(), updatedViolator);
	}
    }

    private void checkUpdateValid(Violator violator, int newHardViolationsValue, int newSoftViolationsValue) throws ViolatorUpdateInvalid {
	if (newHardViolationsValue > violator.hardViolationsValue
		|| (newHardViolationsValue == violator.hardViolationsValue && newSoftViolationsValue > violator.softViolationsValue)) { throw new ViolatorUpdateInvalid(); }
    }

    private void checkPairConstraints(ScheduledItem scheduledItem, SchedulePlan plan, Set<ConstraintPartner> partners,
	    boolean updateConnected) {
	try {
	    checkPairConstraints(scheduledItem, plan, partners, updateConnected, null, null);
	}
	catch (ViolatorUpdateInvalid e) {
	    // XXX this should never happen!!
	    throw new RuntimeException(e);
	}
    }

    public Violator getBiggestViolator(Violator upperBound) {
	return violationsTree.isEmpty() ? null : (upperBound == null ? violationsTree.last() : violationsTree.lower(upperBound));
    }

    public Collection<ScheduledItem> getHardViolatedItems(ScheduledItem itemToCheck, SchedulePlan plan) {
	Collection<ScheduledItem> violatedItems = new ArrayList<ScheduledItem>();
	for (ConstraintPartner constraintPartner : constraintMap.get(itemToCheck.getItemToSchedule())) {
	    ScheduledItem constraintItem = plan.getScheduledItem(constraintPartner.partnerItem);
	    for (ItemPairConstraint constraint : constraintPartner.constraints) {
		ConstraintDecision decision = constraint.check(itemToCheck, constraintItem);
		if (!decision.isFulfilled() && decision.isHardConstraint()) {
		    violatedItems.add(constraintItem);
		}
	    }
	}
	return violatedItems;
    }

    protected class ConstraintPartner {
	private final ItemToSchedule partnerItem;
	private final ViolationsContainer violationsContainer;
	private final List<ItemPairConstraint> constraints;

	public ConstraintPartner(ItemToSchedule partnerItem, ViolationsContainer violationsContainer, List<ItemPairConstraint> constraints) {
	    this.partnerItem = partnerItem;
	    this.violationsContainer = violationsContainer;
	    this.constraints = constraints;
	}

	public List<ItemPairConstraint> getConstraints() {
	    return constraints;
	}

	public ItemToSchedule getPartnerItem() {
	    return partnerItem;
	}
    }

    private class ViolationsContainer {

	private final ViolatorValues values;

	public ViolationsContainer(List<ConstraintDecision> violations) {
	    values = new ViolatorValues();
	    updateValues(violations);
	}

	public void updateValues(ViolatorValues newContainerValues) {
	    values.hardViolationsValue = newContainerValues.hardViolationsValue;
	    values.softViolationsValue = newContainerValues.softViolationsValue;
	}

	public final void updateValues(List<ConstraintDecision> violations) {
	    values.hardViolationsValue = 0;
	    values.softViolationsValue = 0;
	    for (ConstraintDecision decision : violations) {
		if (!decision.isFulfilled()) {
		    if (decision.isHardConstraint()) {
			values.hardViolationsValue += decision.getViolationValue();
		    }
		    else {
			values.softViolationsValue += decision.getViolationValue();
		    }
		}
	    }
	}
    }

    public class Violator implements Comparable<Violator> {
	private final ScheduledItem scheduledItem;
	private int hardViolationsValue;
	private int softViolationsValue;

	public Violator(ScheduledItem scheduledItem) {
	    this.scheduledItem = scheduledItem;
	    checkSingleConstraints();
	    getPairConstraintDecisions();
	}

	public Violator(ScheduledItem scheduledItem, int hardViolationsValue, int softViolationsValue) {
	    this.scheduledItem = scheduledItem;
	    this.hardViolationsValue = hardViolationsValue;
	    this.softViolationsValue = softViolationsValue;
	}

	private void aggregate(ConstraintDecision decision) {
	    if (!decision.isFulfilled()) {
		if (decision.isHardConstraint()) {
		    hardViolationsValue += decision.getViolationValue();
		}
		else {
		    softViolationsValue += decision.getViolationValue();
		}
	    }
	}

	private void getPairConstraintDecisions() {
	    Set<ConstraintPartner> partners = constraintMap.get(scheduledItem.getItemToSchedule());
	    if (partners != null && !partners.isEmpty()) {
		checkPartnerConstraints(partners);
	    }
	}

	private void checkPartnerConstraints(Set<ConstraintPartner> partners) {
	    for (ConstraintPartner partner : partners) {
		ViolatorValues partnerValues = partner.violationsContainer.values;
		hardViolationsValue += partnerValues.hardViolationsValue;
		softViolationsValue += partnerValues.softViolationsValue;
	    }
	}

	private void checkSingleConstraints() {
	    for (SingleItemConstraint constraint : singleConstraints) {
		aggregate(constraint.check(scheduledItem));
	    }
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
	    final Violator other = (Violator) obj;
	    return scheduledItem.getItemToSchedule().getId() == other.scheduledItem.getItemToSchedule().getId();
	}

	@Override
	public int compareTo(Violator o) {
	    int result = (hardViolationsValue < o.hardViolationsValue ? -1 : (hardViolationsValue == o.hardViolationsValue ? 0 : 1));
	    if (result == 0) {
		result = (softViolationsValue < o.softViolationsValue ? -1 : (softViolationsValue == o.softViolationsValue ? 0 : 1));
	    }
	    if (result == 0) {
		int summary = scheduledItem.getItemToSchedule().getDurationSummary();
		int otherSummary = o.scheduledItem.getItemToSchedule().getDurationSummary();
		result = (summary > otherSummary ? -1 : (summary == otherSummary ? 0 : 1));
	    }
	    if (result == 0) {
		int id = scheduledItem.getItemToSchedule().getId();
		int otherId = o.scheduledItem.getItemToSchedule().getId();
		result = (id < otherId ? -1 : (id == otherId ? 0 : 1));
	    }
	    return result;
	}

	public ScheduledItem getScheduledItem() {
	    return scheduledItem;
	}

	public int getHardViolationsValue() {
	    return hardViolationsValue;
	}

	public void setHardViolationsValue(int hardViolationsValue) {
	    this.hardViolationsValue = hardViolationsValue;
	}

	public int getSoftViolationsValue() {
	    return softViolationsValue;
	}

	public void setSoftViolationsValue(int softViolationsValue) {
	    this.softViolationsValue = softViolationsValue;
	}
    }

    public ViolatorValues checkViolationsForPlan(SchedulePlan plan) {
	ViolatorValues planValues = new ViolatorValues();

	// TODO: pair-violations are counted twice
	for (ScheduledItem itemToCheck : plan.getScheduledItems()) {
	    for (SingleItemConstraint constraint : singleConstraints) {
		ConstraintDecision decision = constraint.check(itemToCheck);
		if (!decision.isFulfilled()) {
		    if (decision.isHardConstraint()) {
			planValues.hardViolationsValue += decision.getViolationValue();
		    }
		    else {
			planValues.softViolationsValue += decision.getViolationValue();
		    }
		}
	    }

	    Set<ConstraintPartner> partners = constraintMap.get(itemToCheck.getItemToSchedule());
	    for (ConstraintPartner partner : partners) {
		ScheduledItem partnerItem = plan.getScheduledItem(partner.getPartnerItem());
		for (ItemPairConstraint constraint : partner.getConstraints()) {
		    ConstraintDecision decision = constraint.check(itemToCheck, partnerItem);
		    if (!decision.isFulfilled()) {
			if (decision.isHardConstraint()) {
			    planValues.hardViolationsValue += decision.getViolationValue();
			}
			else {
			    planValues.softViolationsValue += decision.getViolationValue();
			}
		    }
		}
	    }
	}

	return planValues;
    }

    public ViolatorValues checkViolationsForItem(ScheduledItem itemToCheck, SchedulePlan plan) {
	ViolatorValues values = new ViolatorValues();

	for (SingleItemConstraint constraint : singleConstraints) {
	    ConstraintDecision decision = constraint.check(itemToCheck);
	    if (!decision.isFulfilled()) {
		if (decision.isHardConstraint()) {
		    values.hardViolationsValue += decision.getViolationValue();
		}
		else {
		    values.softViolationsValue += decision.getViolationValue();
		}
	    }
	}

	Set<ConstraintPartner> partners = constraintMap.get(itemToCheck.getItemToSchedule());
	for (ConstraintPartner partner : partners) {
	    ScheduledItem partnerItem = plan.getScheduledItem(partner.getPartnerItem());
	    if (partnerItem == null) {
		// the partnerItem can be null if it has been removed from the plan
		continue;
	    }
	    for (ItemPairConstraint constraint : partner.getConstraints()) {
		ConstraintDecision decision = constraint.check(itemToCheck, partnerItem);
		if (!decision.isFulfilled()) {
		    if (decision.isHardConstraint()) {
			values.hardViolationsValue += decision.getViolationValue();
		    }
		    else {
			values.softViolationsValue += decision.getViolationValue();
		    }
		}
	    }
	}
	return values;
    }

    public void planHasBeenUpdated(SchedulePlan oldPlan, SchedulePlan newPlan) {
	// TODO: improve the update
	violationsTree.clear();
	initializeViolationTree(newPlan);

	predictor.planHasBeenUpdated(oldPlan, newPlan);
    }
}
