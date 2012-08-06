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

package cern.acctesting.service.schedule.impl;

import java.util.List;

import cern.acctesting.service.schedule.impl.ViolationsManager.PartnerUpdate;
import cern.acctesting.service.schedule.impl.ViolationsManager.Violator;

public class ViolatorUpdate {

    private final Violator updatedViolator;
    private final List<PartnerUpdate> partnerUpdates;

    public ViolatorUpdate(Violator updatedViolator, List<PartnerUpdate> partnerUpdates) {
        this.updatedViolator = updatedViolator;
        this.partnerUpdates = partnerUpdates;
    }

    public Violator getUpdatedViolator() {
        return updatedViolator;
    }

    public List<PartnerUpdate> getPartnerUpdates() {
        return partnerUpdates;
    }

}
