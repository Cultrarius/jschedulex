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

public class ConstraintDecision {
    
    private final boolean hardConstraint;
    private final boolean isFulfilled;
    private final int violationValue;
    
    public ConstraintDecision(boolean hardConstraint, boolean isFulfilled, int violationValue) {
        this.hardConstraint = hardConstraint;
        this.isFulfilled = isFulfilled;
        this.violationValue = violationValue;
    }

    public boolean isHardConstraint() {
        return hardConstraint;
    }

    public boolean isFulfilled() {
        return isFulfilled;
    }

    public int getViolationValue() {
        return violationValue;
    }

}
