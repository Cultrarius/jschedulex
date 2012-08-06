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

import cern.acctesting.service.schedule.ScheduledItem;
import cern.acctesting.service.schedule.constraint.ConstraintDecision;
import cern.acctesting.service.schedule.constraint.SingleItemConstraint;

public class StartNowConstraint implements SingleItemConstraint {

    @Override
    public ConstraintDecision check(ScheduledItem item) {
        return new ConstraintDecision(false, item.getStart() == 0, item.getStart() + item.getItemToSchedule().getDurationSummary());
    }

}
