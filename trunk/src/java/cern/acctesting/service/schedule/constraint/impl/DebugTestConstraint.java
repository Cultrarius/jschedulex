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

import cern.acctesting.service.schedule.ItemToSchedule;
import cern.acctesting.service.schedule.Lane;
import cern.acctesting.service.schedule.ScheduleUtil;
import cern.acctesting.service.schedule.ScheduledItem;
import cern.acctesting.service.schedule.constraint.ConstraintDecision;
import cern.acctesting.service.schedule.constraint.ConstraintPrediction;
import cern.acctesting.service.schedule.constraint.ItemPairConstraint;
import cern.acctesting.service.schedule.constraint.ConstraintPrediction.Prediction;

public class DebugTestConstraint implements ItemPairConstraint {

    @Override
    public ConstraintDecision check(ScheduledItem item1, ScheduledItem item2) {
        boolean isFulfilled = true;
        int overlappedValue = 0;
        if (item1.getItemToSchedule().getId() % 10 == item2.getItemToSchedule().getId() % 10) {
            for (Lane lane1 : item1.getItemToSchedule().getAffectedLanes()) {
                for (Lane lane2 : item2.getItemToSchedule().getAffectedLanes()) {
                    int overlapping = ScheduleUtil.getOverlappingValue(item1.getStart(), item1.getEnd(lane1), item2.getStart(),
                            item2.getEnd(lane2));
                    if (overlapping > 0) {
                        isFulfilled = false;
                        overlappedValue += overlapping;
                    }
                }
            }
        }
        return new ConstraintDecision(true, isFulfilled, overlappedValue);
    }

    @Override
    public boolean needsChecking(ItemToSchedule item1, ItemToSchedule item2) {
        return item1.getId() % 10 == item2.getId() % 10;
    }

    @Override
    public ConstraintPrediction predictDecision(ItemToSchedule movedItem, ItemToSchedule fixItem) {
        boolean idsCollide = movedItem.getId() % 10 == fixItem.getId() % 10;
        int overlappedValue = 0;
        if (idsCollide) {
            for (Lane lane1 : movedItem.getAffectedLanes()) {
                for (Lane lane2 : fixItem.getAffectedLanes()) {
                    overlappedValue += ScheduleUtil.getOverlappingValue(0, movedItem.getDuration(lane1), 0, fixItem.getDuration(lane2));
                }
            }
        }
        return new ConstraintPrediction(Prediction.NO_CONFLICT, idsCollide ? Prediction.CONFLICT : Prediction.NO_CONFLICT,
                Prediction.NO_CONFLICT, overlappedValue);
    }

}
