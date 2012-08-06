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

package cern.acctesting.service.schedule.constraint.impl;

import java.util.Collection;

import cern.acctesting.service.schedule.ItemToSchedule;
import cern.acctesting.service.schedule.Lane;
import cern.acctesting.service.schedule.ScheduleUtil;
import cern.acctesting.service.schedule.ScheduledItem;
import cern.acctesting.service.schedule.constraint.ConstraintDecision;
import cern.acctesting.service.schedule.constraint.ConstraintPrediction;
import cern.acctesting.service.schedule.constraint.ItemPairConstraint;
import cern.acctesting.service.schedule.constraint.ConstraintPrediction.Prediction;

public class NoOverlappingConstraint implements ItemPairConstraint {

    @Override
    public ConstraintDecision check(ScheduledItem item1, ScheduledItem item2) {
        boolean isFulfilled = true;
        int overlappedValue = 0;
        Collection<Lane> lanes = item1.getItemToSchedule().getAffectedLanes();
        lanes.retainAll(item2.getItemToSchedule().getAffectedLanes());
        for (Lane lane : lanes) {
            int overlapping = ScheduleUtil.getOverlappingValue(item1.getStart(), item1.getEnd(lane), item2.getStart(), item2.getEnd(lane));
            if (overlapping > 0) {
                isFulfilled = false;
                overlappedValue += overlapping;
            }
        }
        return new ConstraintDecision(true, isFulfilled, overlappedValue);
    }

    @Override
    public boolean needsChecking(ItemToSchedule item1, ItemToSchedule item2) {
        Collection<Lane> lanes = item1.getAffectedLanes();
        lanes.retainAll(item2.getAffectedLanes());
        return !lanes.isEmpty();
    }

    @Override
    public ConstraintPrediction predictDecision(ItemToSchedule movedItem, ItemToSchedule fixItem) {
        Collection<Lane> lanes = movedItem.getAffectedLanes();
        lanes.retainAll(fixItem.getAffectedLanes());
        int overlappedValue = 0;
        for (Lane lane : lanes) {
            overlappedValue += ScheduleUtil.getOverlappingValue(0, movedItem.getDuration(lane), 0, fixItem.getDuration(lane));
        }
        return new ConstraintPrediction(Prediction.NO_CONFLICT, lanes.isEmpty() ? Prediction.NO_CONFLICT : Prediction.CONFLICT,
                Prediction.NO_CONFLICT, overlappedValue);
    }

}
