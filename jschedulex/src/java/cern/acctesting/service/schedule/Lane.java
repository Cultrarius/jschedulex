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

package cern.acctesting.service.schedule;

/**
 * This class is used by the scheduling algorithm to represent one executing unit for the scheduled items. This could
 * for example be a processor that executes tasks or a roboter that executes movement-commands.
 * 
 * @author mgaletzk
 * @see ItemToSchedule
 * @see ScheduledItem
 */
public class Lane {

    private final int number;

    /**
     * Creates a new lane with the given number
     * 
     * @param number the number the new lane has. This is a unique identifier to distinguish this lane from other lanes.
     */
    public Lane(int number) {
        this.number = number;
    }

    /**
     * Returns the number of this lane. The number is a unique identifier used to distinguish different lanes.
     * 
     * @return the number of this lane
     */
    public int getNumber() {
        return number;
    }

    @Override
    public int hashCode() {
        return number;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Lane other = (Lane) obj;
        if (number != other.number)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Lane " + number;
    }
}
