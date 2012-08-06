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
 * This utility-class provides some static methods that are used by various parts of the scheduling algorithm.
 * 
 * @author mgaletzk
 */
public final class ScheduleUtil {

    private ScheduleUtil() {
    }

    /**
     * Returns for two given items the value that states how much they overlap each other. If, for example, there are
     * the following two items:<br>
     * item1: start = 0, end = 10<br>
     * item2: start = 6, end = 20<br>
     * Then they overlap each other in the range from 6 to 10 and therefore have an overlapping value of 4.
     * 
     * @param startItem1 the start of the first item
     * @param endItem1 the end of the first item
     * @param startItem2 the start of the second item
     * @param endItem2 the end of the second item
     * @return the overlapping value of the two items
     */
    public static int getOverlappingValue(int startItem1, int endItem1, int startItem2, int endItem2) {
        if (startItem1 == startItem2) {
            // item1 starts together with item2
            return Math.min(endItem1 - startItem1, endItem2 - startItem2);
        } else if (startItem1 < startItem2 && endItem1 > startItem2) {
            // item2 starts in the middle of item1
            return Math.min(endItem1, endItem2) - startItem2;
        } else if (startItem1 > startItem2 && endItem2 > startItem1) {
            // item1 starts in the middle of item2
            return Math.min(endItem1, endItem2) - startItem1;
        }
        return 0;
    }

    public static int getMinimumDistanceToEnd(ScheduledItem item1, ScheduledItem item2) {
        return item2.getStart() - (item1.getStart() + item1.getItemToSchedule().getMaxDuration());
    }
}
