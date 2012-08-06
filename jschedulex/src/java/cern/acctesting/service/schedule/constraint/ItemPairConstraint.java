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

package cern.acctesting.service.schedule.constraint;

import cern.acctesting.service.schedule.ItemToSchedule;
import cern.acctesting.service.schedule.ScheduledItem;

public interface ItemPairConstraint {

    ConstraintDecision check(ScheduledItem item1, ScheduledItem item2);
    
    boolean needsChecking(ItemToSchedule item1, ItemToSchedule item2);
    
    //TODO: does not separate between hard- and soft constraints
    ConstraintPrediction predictDecision(ItemToSchedule movedItem, ItemToSchedule fixItem);
}
