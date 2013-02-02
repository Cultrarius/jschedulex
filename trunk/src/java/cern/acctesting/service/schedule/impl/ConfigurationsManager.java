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

import cern.acctesting.service.schedule.ScheduledItem;
import cern.acctesting.service.schedule.exception.ViolatorUpdateInvalid;

public class ConfigurationsManager {

    private PlanConfiguration bestPlanConfiguration;
    private final ViolationsManager violationsManager;
    private Violator referenceViolator;
    private Configuration referenceConfiguration;
    private Configuration bestConfiguration;

    public ConfigurationsManager(ViolationsManager violationsManager) {
        this.violationsManager = violationsManager;
    }

    public boolean addConfiguration(Violator violator, SchedulePlan plan, int start) {
        if (referenceConfiguration.violator.getScheduledItem().getStart() == start) {
            return false;
        }

        ScheduledItem newItem = plan.moveScheduledItem(violator.getScheduledItem().getItemToSchedule(), start);
        ViolatorUpdate violatorUpdate;
        try {
            violatorUpdate = violationsManager.tryViolatorUpdate(newItem, plan);
        } catch (ViolatorUpdateInvalid e) {
            // the update failed since the new item conflicts against more constraints than the reference
            return false;
        }

        int hardValue = violatorUpdate.getUpdatedViolator().getHardViolationsValue();
        int softValue = violatorUpdate.getUpdatedViolator().getSoftViolationsValue();

        int referenceHardValue = referenceViolator.getHardViolationsValue();
        if (referenceHardValue > hardValue || (referenceHardValue == hardValue && referenceViolator.getSoftViolationsValue() > softValue)) {
            Configuration newConfiguration = new Configuration(violatorUpdate, plan.getMakespan());
            if (bestConfiguration == null || newConfiguration.compareTo(bestConfiguration) == -1) {
                bestConfiguration = newConfiguration;
            }
        }
        return true;
    }

    public boolean applyBestConfiguration(SchedulePlan plan) {
        if (bestConfiguration == null) {
            return false;
        }

        ScheduledItem oldItem = bestConfiguration.violator.getScheduledItem();
        plan.moveScheduledItem(oldItem.getItemToSchedule(), oldItem.getStart());
        violationsManager.updateViolator(bestConfiguration.violatorUpdate);

        return true;
    }

    public void applyReferenceConfiguration(SchedulePlan plan) {
        ScheduledItem oldItem = referenceConfiguration.violator.getScheduledItem();
        plan.moveScheduledItem(oldItem.getItemToSchedule(), oldItem.getStart());
    }

    public ScheduledItem getBestConfiguration() {
        return bestConfiguration == null ? referenceConfiguration.violator.getScheduledItem() : bestConfiguration.violator
                .getScheduledItem();
    }

    private class Configuration implements Comparable<Configuration> {
        private final int planMakespan;
        private final int durationSummary;
        private final Violator violator;
        private final ViolatorUpdate violatorUpdate;

        public Configuration(Violator violator, int planMakespan) {
            this.violator = violator;
            this.planMakespan = planMakespan;
            this.durationSummary = violator.getScheduledItem().getItemToSchedule().getDurationSummary();
            violatorUpdate = null;
        }

        public Configuration(ViolatorUpdate violatorUpdate, int planMakespan) {
            this.violatorUpdate = violatorUpdate;
            this.violator = violatorUpdate.getUpdatedViolator();
            this.planMakespan = planMakespan;
            this.durationSummary = violator.getScheduledItem().getItemToSchedule().getDurationSummary();
        }

        @Override
        public int compareTo(Configuration o) {
            int result = (planMakespan < o.planMakespan ? -1 : (planMakespan == o.planMakespan ? 0 : 1));
            if (result == 0) {
                result = (violator.getHardViolationsValue() < o.violator.getHardViolationsValue() ? -1
                        : (violator.getHardViolationsValue() == o.violator.getHardViolationsValue() ? 0 : 1));
            }
            if (result == 0) {
                result = (violator.getSoftViolationsValue() < o.violator.getSoftViolationsValue() ? -1
                        : (violator.getSoftViolationsValue() == o.violator.getSoftViolationsValue() ? 0 : 1));
            }
            if (result == 0) {
                result = (durationSummary < o.durationSummary ? -1 : (durationSummary == o.durationSummary ? 0 : 1));
            }
            return result;
        }
    }

    public void resetConfigurations(Violator violator, SchedulePlan plan) {
        this.referenceViolator = violator;
        referenceConfiguration = new Configuration(violator, plan.getMakespan());
        bestConfiguration = null;
    }

    public void resetPlanConfigurations() {
        bestPlanConfiguration = null;
    }

    public void addPlanConfiguration(SchedulePlan plan) {
        ViolatorValues planValues = violationsManager.checkViolationsForPlan(plan);
        PlanConfiguration newConfiguration = new PlanConfiguration(plan, planValues);
        if (bestPlanConfiguration == null || newConfiguration.compareTo(bestPlanConfiguration) == -1) {
            bestPlanConfiguration = newConfiguration;
        }
    }

    public SchedulePlan getBestPlanConfiguration() {
        return bestPlanConfiguration.plan;
    }

    private class PlanConfiguration implements Comparable<PlanConfiguration> {
        private final SchedulePlan plan;
        private final int hardViolationsSum;
        private final int softViolationsSum;

        public PlanConfiguration(SchedulePlan plan, ViolatorValues planValues) {
            this.plan = plan;
            this.hardViolationsSum = planValues.hardViolationsValue;
            this.softViolationsSum = planValues.softViolationsValue;
        }

        @Override
        public int compareTo(PlanConfiguration o) {
            int result = (hardViolationsSum < o.hardViolationsSum ? -1 : (hardViolationsSum == o.hardViolationsSum ? 0 : 1));
            if (result == 0) {
                result = (plan.getMakespan() < o.plan.getMakespan() ? -1 : (plan.getMakespan() == o.plan.getMakespan() ? 0 : 1));
            }
            if (result == 0) {
                result = (softViolationsSum < o.softViolationsSum ? -1 : (softViolationsSum == o.softViolationsSum ? 0 : 1));
            }
            return result;
        }
    }
}
