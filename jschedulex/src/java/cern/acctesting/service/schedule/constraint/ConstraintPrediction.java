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

public class ConstraintPrediction {
    private final Prediction conflictsWhenBefore;
    private final Prediction conflictsWhenTogether;
    private final Prediction conflictsWhenAfter;
    private final int predictedConflictValue;

    public ConstraintPrediction(Prediction conflictsWhenBefore, Prediction conflictsWhenTogether, Prediction conflictsWhenAfter, int predictedConflictValue) {
        this.conflictsWhenBefore = conflictsWhenBefore;
        this.conflictsWhenTogether = conflictsWhenTogether;
        this.conflictsWhenAfter = conflictsWhenAfter;
        this.predictedConflictValue = predictedConflictValue;
    }
    
    public Prediction getConflictsWhenBefore() {
        return conflictsWhenBefore;
    }

    public Prediction getConflictsWhenTogether() {
        return conflictsWhenTogether;
    }

    public Prediction getConflictsWhenAfter() {
        return conflictsWhenAfter;
    }

    public int getPredictedConflictValue() {
        return predictedConflictValue;
    }

    public enum Prediction {
        CONFLICT, NO_CONFLICT, UNKNOWN
    }
}
