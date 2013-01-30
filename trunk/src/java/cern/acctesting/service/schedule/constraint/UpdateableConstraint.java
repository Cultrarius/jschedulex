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

/**
 * All constraints implementing this interface have the chance to update themselves before every scheduling run. For example, it could get
 * the latest information from a database or set some internal clock.
 * 
 * @author Michael Galetzka
 * 
 */
public interface UpdateableConstraint {

    /**
     * This method is called before every scheduling run to give the constraint the possibility to update itself (e.g. getting information
     * from a database).
     */
    void updateConstraint();
}
