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

import java.util.Set;

import cern.acctesting.service.schedule.ScheduledItem;
import cern.acctesting.service.schedule.constraint.ConstraintDecision;
import cern.acctesting.service.schedule.constraint.SingleItemConstraint;
import cern.acctesting.service.schedule.impl.ViolationsManager.ConstraintPartner;

public class Violator implements Comparable<Violator> {
	private final ScheduledItem scheduledItem;
	private int hardViolationsValue;
	private int softViolationsValue;
	private ViolationsManager manager;

	protected Violator(ScheduledItem scheduledItem, ViolationsManager manager) {
	    this.scheduledItem = scheduledItem;
	    this.manager = manager;
	    checkSingleConstraints();
	    getPairConstraintDecisions();
	}

	protected Violator(ScheduledItem scheduledItem, int hardViolationsValue, int softViolationsValue, ViolationsManager manager) {
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
	    Set<ConstraintPartner> partners = manager.constraintMap.get(scheduledItem.getItemToSchedule());
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
	    for (SingleItemConstraint constraint : manager.singleConstraints) {
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
