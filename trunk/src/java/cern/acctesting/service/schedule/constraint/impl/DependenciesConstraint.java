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
import cern.acctesting.service.schedule.ScheduleUtil;
import cern.acctesting.service.schedule.ScheduledItem;
import cern.acctesting.service.schedule.constraint.ConstraintDecision;
import cern.acctesting.service.schedule.constraint.ConstraintPrediction;
import cern.acctesting.service.schedule.constraint.ItemPairConstraint;
import cern.acctesting.service.schedule.constraint.ConstraintPrediction.Prediction;

public class DependenciesConstraint implements ItemPairConstraint {

    @Override
    public ConstraintDecision check(ScheduledItem item1, ScheduledItem item2) {
        boolean isFulfilled = true;
        int overlappedValue = 0;
        ItemToSchedule itemToSchedule1 = item1.getItemToSchedule();
        ItemToSchedule itemToSchedule2 = item2.getItemToSchedule();
        int distanceToEnd = 0;
        if (itemToSchedule1.getRequiredItems().contains(itemToSchedule2)) {
            distanceToEnd = ScheduleUtil.getMinimumDistanceToEnd(item2, item1);
        } else if (itemToSchedule2.getRequiredItems().contains(itemToSchedule1)) {
            distanceToEnd = ScheduleUtil.getMinimumDistanceToEnd(item1, item2);
        }
        if (distanceToEnd < 0) {
            isFulfilled = false;
            overlappedValue = Math.max(item1.getItemToSchedule().getDurationSummary(), item2.getItemToSchedule().getDurationSummary());
        }
        return new ConstraintDecision(true, isFulfilled, overlappedValue);
    }

    @Override
    public boolean needsChecking(ItemToSchedule item1, ItemToSchedule item2) {
        return item1.getRequiredItems().contains(item2) || item2.getRequiredItems().contains(item1);
    }

    @Override
    public ConstraintPrediction predictDecision(ItemToSchedule movedItem, ItemToSchedule fixItem) {
        int conflictValue = Math.max(movedItem.getDurationSummary(), fixItem.getDurationSummary());
        if (fixItem.getRequiredItems().contains(movedItem)) {
            return new ConstraintPrediction(Prediction.NO_CONFLICT, Prediction.CONFLICT, Prediction.CONFLICT, conflictValue);
        } else if (movedItem.getRequiredItems().contains(fixItem)) {
            return new ConstraintPrediction(Prediction.CONFLICT, Prediction.CONFLICT, Prediction.NO_CONFLICT, conflictValue);
        }
        return new ConstraintPrediction(Prediction.NO_CONFLICT, Prediction.NO_CONFLICT, Prediction.NO_CONFLICT, 0);
    }

}
