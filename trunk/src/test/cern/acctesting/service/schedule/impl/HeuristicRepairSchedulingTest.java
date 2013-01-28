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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cern.acctesting.service.schedule.ItemToSchedule;
import cern.acctesting.service.schedule.Lane;
import cern.acctesting.service.schedule.ScheduledItem;
import cern.acctesting.service.schedule.constraint.ConstraintDecision;
import cern.acctesting.service.schedule.constraint.ConstraintPrediction;
import cern.acctesting.service.schedule.constraint.ConstraintPrediction.Prediction;
import cern.acctesting.service.schedule.constraint.ItemPairConstraint;
import cern.acctesting.service.schedule.constraint.SingleItemConstraint;
import cern.acctesting.service.schedule.constraint.impl.DebugTestConstraint;
import cern.acctesting.service.schedule.constraint.impl.DependenciesConstraint;
import cern.acctesting.service.schedule.constraint.impl.NoOverlappingConstraint;
import cern.acctesting.service.schedule.constraint.impl.StartNowConstraint;
import cern.acctesting.service.schedule.exception.SchedulingException;

public class HeuristicRepairSchedulingTest {

    private HeuristicRepairScheduling scheduling;
    private ViolationsManager manager;

    private HeuristicRepairScheduling noConstraintScheduling;
    private ViolationsManager noConstraintManager;
    private List<SingleItemConstraint> singleConstraints;
    private List<ItemPairConstraint> pairConstraints;

    @Before
    public void setUp() {
        singleConstraints = new ArrayList<SingleItemConstraint>();
        singleConstraints.add(new StartNowConstraint());

        pairConstraints = new ArrayList<ItemPairConstraint>();
        pairConstraints.add(new NoOverlappingConstraint());
        pairConstraints.add(new DependenciesConstraint());
        pairConstraints.add(new DebugTestConstraint());

        noConstraintManager = new ViolationsManager(new ArrayList<SingleItemConstraint>(), new ArrayList<ItemPairConstraint>());
        noConstraintScheduling = new HeuristicRepairScheduling(noConstraintManager);

        manager = new ViolationsManager(singleConstraints, pairConstraints);
        scheduling = new HeuristicRepairScheduling(manager);
    }

    public boolean allConstraintsSatisfied(SchedulePlan plan) {
        final List<ScheduledItem> scheduledItems = plan.getScheduledItems();
        for (ScheduledItem item1 : scheduledItems) {
            for (SingleItemConstraint constraint : singleConstraints) {
                ConstraintDecision decision = constraint.check(item1);
                if (decision.isHardConstraint() && !decision.isFulfilled()) {
                    System.out.println("Constraint violated! Item: " + item1 + ", Constraint: " + constraint.getClass());
                    return false;
                }
            }

            for (ScheduledItem item2 : scheduledItems) {
                if (item1 == item2) {
                    continue;
                }

                for (ItemPairConstraint constraint : pairConstraints) {
                    ConstraintDecision decision = constraint.check(item1, item2);
                    if (decision.isHardConstraint() && !decision.isFulfilled()) {
                        System.out.println("Constraint violated! Item1: " + item1 + ", Item2: " + item2 + ", Constraint: "
                                + constraint.getClass());
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @Test
    public void testScheduleNothing1() {
        SchedulePlan result = noConstraintScheduling.schedule(new ArrayList<ItemToSchedule>());

        assertTrue(result.getScheduledItems().isEmpty());
        assertTrue(result.getFixedItems().isEmpty());
        assertEquals(0, result.getMakespan());
    }

    @Test
    public void testScheduleNothing2() {
        SchedulePlan result = noConstraintScheduling.schedule(new ArrayList<ItemToSchedule>(), new ArrayList<ScheduledItem>());

        assertTrue(result.getScheduledItems().isEmpty());
        assertTrue(result.getFixedItems().isEmpty());
        assertEquals(0, result.getMakespan());
    }

    @Test
    public void testScheduleJustFixed1() {
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        durations.put(new Lane(0), 42);
        ItemToSchedule itemToSchedule = new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>());
        ScheduledItem fixedItem = new ScheduledItem(itemToSchedule, 0);
        fixedItems.add(fixedItem);
        SchedulePlan result = noConstraintScheduling.schedule(new ArrayList<ItemToSchedule>(), fixedItems);

        assertEquals(1, result.getScheduledItems().size());
        assertTrue(result.getScheduledItems().contains(fixedItem));
        assertEquals(42, result.getMakespan());
    }

    @Test
    public void testScheduleJustFixed2() {
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        durations.put(new Lane(0), 42);
        ItemToSchedule itemToSchedule = new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>());
        ScheduledItem fixedItem = new ScheduledItem(itemToSchedule, 10);
        fixedItems.add(fixedItem);
        SchedulePlan result = noConstraintScheduling.schedule(new ArrayList<ItemToSchedule>(), fixedItems);

        assertEquals(1, result.getScheduledItems().size());
        assertTrue(result.getScheduledItems().contains(fixedItem));
        assertEquals(52, result.getMakespan());
    }

    @Test
    public void testScheduleJustFixed3() {
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        durations.put(new Lane(0), 42);
        durations.put(new Lane(1), 50);
        ItemToSchedule itemToSchedule = new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>());
        ScheduledItem fixedItem = new ScheduledItem(itemToSchedule, 10);
        fixedItems.add(fixedItem);
        SchedulePlan result = noConstraintScheduling.schedule(new ArrayList<ItemToSchedule>(), fixedItems);

        assertEquals(1, result.getScheduledItems().size());
        assertTrue(result.getScheduledItems().contains(fixedItem));
        assertEquals(60, result.getMakespan());
    }

    @Test
    public void testScheduleJustFixed4() {
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        durations.put(new Lane(0), 42);
        durations.put(new Lane(1), 50);
        ItemToSchedule itemToSchedule1 = new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>());
        ItemToSchedule itemToSchedule2 = new ItemToSchedule(2, durations, new ArrayList<ItemToSchedule>());

        ScheduledItem fixedItem1 = new ScheduledItem(itemToSchedule1, 0);
        ScheduledItem fixedItem2 = new ScheduledItem(itemToSchedule2, 50);
        fixedItems.add(fixedItem1);
        fixedItems.add(fixedItem2);
        SchedulePlan result = noConstraintScheduling.schedule(new ArrayList<ItemToSchedule>(), fixedItems);

        assertEquals(2, result.getScheduledItems().size());
        assertTrue(result.getScheduledItems().contains(fixedItem1));
        assertTrue(result.getScheduledItems().contains(fixedItem2));
        assertEquals(100, result.getMakespan());
    }

    @Test
    public void testScheduleNoConstraint1() {
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        durations.put(new Lane(0), 42);
        ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();
        ItemToSchedule itemToSchedule = new ItemToSchedule(1, durations, items);
        items.add(itemToSchedule);
        SchedulePlan result = noConstraintScheduling.schedule(items, fixedItems);

        assertEquals(1, result.getScheduledItems().size());
        assertEquals(itemToSchedule, result.getScheduledItems().get(0).getItemToSchedule());
        assertEquals(42, result.getMakespan());
    }
    
    @Test
    public void testScheduleNoConstraint2Times() {
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        durations.put(new Lane(0), 42);
        ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();
        ItemToSchedule itemToSchedule = new ItemToSchedule(1, durations, items);
        items.add(itemToSchedule);
        noConstraintScheduling.schedule(items, fixedItems);
        SchedulePlan result = noConstraintScheduling.schedule(items, fixedItems);

        assertEquals(1, result.getScheduledItems().size());
        assertEquals(itemToSchedule, result.getScheduledItems().get(0).getItemToSchedule());
        assertEquals(42, result.getMakespan());
    }

    @Test(expected = SchedulingException.class)
    public void testScheduleUnsatisfiable() {
        // One fixed items leads to an unsatisfiable situation
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();

        Collection<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        durations.put(new Lane(0), 100);
        final ItemToSchedule item1 = new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>());
        items.add(item1);

        durations.clear();
        durations.put(new Lane(0), 100);
        Collection<ItemToSchedule> required = new ArrayList<ItemToSchedule>();
        required.add(item1);
        final ItemToSchedule item2 = new ItemToSchedule(11, durations, required);
        fixedItems.add(new ScheduledItem(item2));

        scheduling.schedule(items, fixedItems);
    }

    @Test
    public void testScheduleSimpleWithConstraint1() {
        // just one item
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        durations.put(new Lane(0), 42);
        ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();
        ItemToSchedule itemToSchedule = new ItemToSchedule(1, durations, items);
        items.add(itemToSchedule);

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(1, result.getScheduledItems().size());
        assertEquals(itemToSchedule, result.getScheduledItems().get(0).getItemToSchedule());
        assertEquals(42, result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }

    @Test
    public void testScheduleSimpleWithConstraint2() {
        // two items, one has to be moved to be scheduled after the other one
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        Collection<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        durations.put(new Lane(0), 100);
        items.add(new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>()));

        durations.clear();
        durations.put(new Lane(1), 100);
        items.add(new ItemToSchedule(11, durations, new ArrayList<ItemToSchedule>()));

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(2, result.getScheduledItems().size());
        assertEquals(200, result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }

    @Test
    public void testScheduleSimpleWithConstraint3() {
        // some different items, none of them collide, but one can be moved to the front, reducing the makespan
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        final ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        durations.put(new Lane(0), 22);
        items.add(new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>()));

        durations.clear();
        durations.put(new Lane(1), 130);
        items.add(new ItemToSchedule(2, durations, new ArrayList<ItemToSchedule>()));

        durations.clear();
        durations.put(new Lane(1), 240);
        durations.put(new Lane(2), 140);
        items.add(new ItemToSchedule(3, durations, new ArrayList<ItemToSchedule>()));

        durations.clear();
        durations.put(new Lane(2), 70);
        items.add(new ItemToSchedule(4, durations, new ArrayList<ItemToSchedule>()));

        durations.clear();
        durations.put(new Lane(2), 80);
        items.add(new ItemToSchedule(5, durations, new ArrayList<ItemToSchedule>()));
        durations.clear();
        durations.put(new Lane(3), 300);
        items.add(new ItemToSchedule(6, durations, new ArrayList<ItemToSchedule>()));

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertEquals(370, result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }
    
    @Test
    public void testScheduleSimpleWithConstraint3Rescheduled() {
        // some different items, none of them collide, but one can be moved to the front, reducing the makespan
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        final ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        durations.put(new Lane(0), 22);
        items.add(new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>()));

        durations.clear();
        durations.put(new Lane(1), 130);
        items.add(new ItemToSchedule(2, durations, new ArrayList<ItemToSchedule>()));

        durations.clear();
        durations.put(new Lane(1), 240);
        durations.put(new Lane(2), 140);
        items.add(new ItemToSchedule(3, durations, new ArrayList<ItemToSchedule>()));

        durations.clear();
        durations.put(new Lane(2), 70);
        items.add(new ItemToSchedule(4, durations, new ArrayList<ItemToSchedule>()));

        durations.clear();
        durations.put(new Lane(2), 80);
        items.add(new ItemToSchedule(5, durations, new ArrayList<ItemToSchedule>()));
        durations.clear();
        durations.put(new Lane(3), 300);
        items.add(new ItemToSchedule(6, durations, new ArrayList<ItemToSchedule>()));

        SchedulePlan result1 = scheduling.schedule(items, fixedItems);
        SchedulePlan result2 = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result2.getScheduledItems().size());
        assertEquals(370, result2.getMakespan());
        assertEquals(result1.getScheduledItems(), result2.getScheduledItems());
        assertTrue(allConstraintsSatisfied(result2));
    }

    @Test
    public void testScheduleSimpleWithConstraint4() {
        // some different items, most of them collide, some should be switched, but without increasing the makespan
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        final ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();

        // 1
        durations.put(new Lane(0), 200);
        durations.put(new Lane(1), 400);
        items.add(new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(new Lane(0), 200);
        items.add(new ItemToSchedule(2, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(new Lane(1), 200);
        items.add(new ItemToSchedule(3, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(new Lane(2), 400);
        items.add(new ItemToSchedule(13, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(new Lane(2), 200);
        items.add(new ItemToSchedule(12, durations, new ArrayList<ItemToSchedule>()));

        // 1
        durations.clear();
        durations.put(new Lane(3), 200);
        items.add(new ItemToSchedule(11, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(new Lane(3), 200);
        items.add(new ItemToSchedule(22, durations, new ArrayList<ItemToSchedule>()));

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertEquals(600, result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }

    @Test
    public void testScheduleSimpleWithConstraint5() {
        // some different items, most of them collide, there is some moving around required
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        final ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();

        // 1
        durations.clear();
        durations.put(new Lane(0), 100);
        items.add(new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>()));

        // 1
        durations.clear();
        durations.put(new Lane(1), 100);
        items.add(new ItemToSchedule(11, durations, new ArrayList<ItemToSchedule>()));

        // 1
        durations.clear();
        durations.put(new Lane(2), 100);
        items.add(new ItemToSchedule(21, durations, new ArrayList<ItemToSchedule>()));

        // 1
        durations.clear();
        durations.put(new Lane(3), 100);
        items.add(new ItemToSchedule(31, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(new Lane(0), 100);
        items.add(new ItemToSchedule(2, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(new Lane(1), 200);
        items.add(new ItemToSchedule(12, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(new Lane(2), 200);
        items.add(new ItemToSchedule(22, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(new Lane(3), 100);
        durations.put(new Lane(4), 100);
        items.add(new ItemToSchedule(32, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(new Lane(0), 100);
        items.add(new ItemToSchedule(3, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(new Lane(3), 100);
        items.add(new ItemToSchedule(33, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(new Lane(4), 200);
        items.add(new ItemToSchedule(13, durations, new ArrayList<ItemToSchedule>()));

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertTrue(700 >= result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }

    @Test
    public void testScheduleSimpleWithConstraint6() {
        // some different items, most of them collide, some should be switched, but without increasing the makespan
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        final ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();

        // 1
        durations.clear();
        durations.put(new Lane(0), 100);
        durations.put(new Lane(1), 200);
        items.add(new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>()));

        // 1
        durations.clear();
        durations.put(new Lane(3), 100);
        items.add(new ItemToSchedule(11, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(new Lane(1), 100);
        items.add(new ItemToSchedule(3, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(new Lane(2), 200);
        items.add(new ItemToSchedule(33, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(new Lane(0), 100);
        items.add(new ItemToSchedule(2, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(new Lane(2), 100);
        items.add(new ItemToSchedule(12, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(new Lane(3), 100);
        items.add(new ItemToSchedule(22, durations, new ArrayList<ItemToSchedule>()));

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertEquals(300, result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }

    @Test
    public void testScheduleSimpleWithConstraint7() {
        // a lot of different items, most of them collide, there is some moving around required
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        final ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        final Lane lane0 = new Lane(0);
        final Lane lane1 = new Lane(1);
        final Lane lane2 = new Lane(2);
        final Lane lane3 = new Lane(3);
        final Lane lane4 = new Lane(4);
        final Lane lane5 = new Lane(5);
        final Lane lane6 = new Lane(6);

        // 1
        durations.clear();
        durations.put(lane0, 100);
        durations.put(lane1, 200);
        durations.put(lane6, 100);
        ItemToSchedule unit1 = new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>());
        items.add(unit1);

        // 1
        durations.clear();
        durations.put(lane3, 100);
        ItemToSchedule unit2 = new ItemToSchedule(11, durations, new ArrayList<ItemToSchedule>());
        items.add(unit2);

        // 1
        durations.clear();
        durations.put(lane4, 100);
        items.add(new ItemToSchedule(21, durations, new ArrayList<ItemToSchedule>()));

        // 1
        durations.clear();
        durations.put(lane5, 100);
        items.add(new ItemToSchedule(31, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(lane0, 100);
        Collection<ItemToSchedule> required1 = new ArrayList<ItemToSchedule>();
        required1.add(unit1);
        items.add(new ItemToSchedule(3, durations, required1));

        // 3
        durations.clear();
        durations.put(lane3, 100);
        Collection<ItemToSchedule> required2 = new ArrayList<ItemToSchedule>();
        required2.add(unit2);
        items.add(new ItemToSchedule(13, durations, required2));

        // 3
        durations.clear();
        durations.put(lane2, 100);
        items.add(new ItemToSchedule(33, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(lane0, 100);
        items.add(new ItemToSchedule(2, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(lane3, 200);
        items.add(new ItemToSchedule(22, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(lane2, 200);
        durations.put(lane6, 200);
        items.add(new ItemToSchedule(12, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane1, 200);
        items.add(new ItemToSchedule(14, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane4, 100);
        items.add(new ItemToSchedule(24, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane3, 100);
        items.add(new ItemToSchedule(44, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane5, 100);
        items.add(new ItemToSchedule(34, durations, new ArrayList<ItemToSchedule>()));

        // 5
        durations.clear();
        durations.put(lane5, 100);
        items.add(new ItemToSchedule(15, durations, new ArrayList<ItemToSchedule>()));

        // 5
        durations.clear();
        durations.put(lane6, 300);
        items.add(new ItemToSchedule(25, durations, new ArrayList<ItemToSchedule>()));

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertTrue(700 >= result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }

    @Test
    public void testScheduleSimpleWithConstraint8() {
        // schedules the same 4 test for 6 different lanes. Each of the 4 tests collides with a test on the other lane
        // and has to be scheduled separately.
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        final ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        final Lane lane0 = new Lane(0);
        final Lane lane1 = new Lane(1);
        final Lane lane2 = new Lane(2);
        final Lane lane3 = new Lane(3);
        final Lane lane4 = new Lane(4);
        final Lane lane5 = new Lane(5);
        final Lane lane6 = new Lane(6);

        // 1
        durations.clear();
        durations.put(lane0, 100);
        items.add(new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>()));

        // 1
        durations.clear();
        durations.put(lane1, 100);
        items.add(new ItemToSchedule(11, durations, new ArrayList<ItemToSchedule>()));

        // 1
        durations.clear();
        durations.put(lane2, 100);
        items.add(new ItemToSchedule(21, durations, new ArrayList<ItemToSchedule>()));

        // 1
        durations.clear();
        durations.put(lane3, 100);
        items.add(new ItemToSchedule(31, durations, new ArrayList<ItemToSchedule>()));

        // 1
        durations.clear();
        durations.put(lane4, 100);
        items.add(new ItemToSchedule(41, durations, new ArrayList<ItemToSchedule>()));

        // 1
        durations.clear();
        durations.put(lane5, 100);
        items.add(new ItemToSchedule(51, durations, new ArrayList<ItemToSchedule>()));

        // 1
        durations.clear();
        durations.put(lane6, 100);
        items.add(new ItemToSchedule(61, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(lane0, 100);
        items.add(new ItemToSchedule(2, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(lane1, 100);
        items.add(new ItemToSchedule(12, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(lane2, 100);
        items.add(new ItemToSchedule(22, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(lane3, 100);
        items.add(new ItemToSchedule(32, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(lane4, 100);
        items.add(new ItemToSchedule(42, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(lane5, 100);
        items.add(new ItemToSchedule(52, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(lane6, 100);
        items.add(new ItemToSchedule(62, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(lane0, 100);
        items.add(new ItemToSchedule(3, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(lane1, 100);
        items.add(new ItemToSchedule(13, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(lane2, 100);
        items.add(new ItemToSchedule(23, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(lane3, 100);
        items.add(new ItemToSchedule(33, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(lane4, 100);
        items.add(new ItemToSchedule(43, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(lane5, 100);
        items.add(new ItemToSchedule(53, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(lane6, 100);
        items.add(new ItemToSchedule(63, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane0, 100);
        items.add(new ItemToSchedule(4, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane1, 100);
        items.add(new ItemToSchedule(14, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane2, 100);
        items.add(new ItemToSchedule(24, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane3, 100);
        items.add(new ItemToSchedule(34, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane4, 100);
        items.add(new ItemToSchedule(44, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane5, 100);
        items.add(new ItemToSchedule(54, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane6, 100);
        items.add(new ItemToSchedule(64, durations, new ArrayList<ItemToSchedule>()));

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertTrue(800 >= result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }
    
    @Test
    public void testScheduleSimpleWithFixed() {
        // a lot of different items, most of them collide, there is some moving around required and fixed items as well
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        final ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        final Lane lane0 = new Lane(0);
        final Lane lane1 = new Lane(1);
        final Lane lane2 = new Lane(2);
        final Lane lane3 = new Lane(3);
        final Lane lane4 = new Lane(4);
        final Lane lane5 = new Lane(5);
        final Lane lane6 = new Lane(6);

        // 1
        durations.clear();
        durations.put(lane0, 100);
        durations.put(lane1, 200);
        durations.put(lane6, 100);
        ItemToSchedule unit1 = new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>());
        fixedItems.add(new ScheduledItem(unit1));

        // 1
        durations.clear();
        durations.put(lane3, 100);
        ItemToSchedule unit2 = new ItemToSchedule(11, durations, new ArrayList<ItemToSchedule>());
        items.add(unit2);

        // 1
        durations.clear();
        durations.put(lane4, 100);
        items.add(new ItemToSchedule(21, durations, new ArrayList<ItemToSchedule>()));

        // 1
        durations.clear();
        durations.put(lane5, 100);
        items.add(new ItemToSchedule(31, durations, new ArrayList<ItemToSchedule>()));

        // 3
        durations.clear();
        durations.put(lane0, 100);
        Collection<ItemToSchedule> required1 = new ArrayList<ItemToSchedule>();
        required1.add(unit1);
        items.add(new ItemToSchedule(3, durations, required1));

        // 3
        durations.clear();
        durations.put(lane3, 100);
        Collection<ItemToSchedule> required2 = new ArrayList<ItemToSchedule>();
        required2.add(unit2);
        items.add(new ItemToSchedule(13, durations, required2));

        // 3
        durations.clear();
        durations.put(lane2, 100);
        items.add(new ItemToSchedule(33, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(lane0, 100);
        items.add(new ItemToSchedule(2, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(lane3, 200);
        items.add(new ItemToSchedule(22, durations, new ArrayList<ItemToSchedule>()));

        // 2
        durations.clear();
        durations.put(lane2, 200);
        durations.put(lane6, 200);
        final ItemToSchedule item12 = new ItemToSchedule(12, durations, new ArrayList<ItemToSchedule>());
        fixedItems.add(new ScheduledItem(item12, 100));

        // 4
        durations.clear();
        durations.put(lane1, 200);
        items.add(new ItemToSchedule(14, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane4, 100);
        items.add(new ItemToSchedule(24, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane3, 100);
        items.add(new ItemToSchedule(44, durations, new ArrayList<ItemToSchedule>()));

        // 4
        durations.clear();
        durations.put(lane5, 100);
        items.add(new ItemToSchedule(34, durations, new ArrayList<ItemToSchedule>()));

        // 5
        durations.clear();
        durations.put(lane5, 100);
        items.add(new ItemToSchedule(15, durations, new ArrayList<ItemToSchedule>()));

        // 5
        durations.clear();
        durations.put(lane6, 300);
        items.add(new ItemToSchedule(25, durations, new ArrayList<ItemToSchedule>()));

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertTrue(700 >= result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }

    @Test
    public void testScheduleLocalOptimum1() {
        // A very simple local optimum that can be escaped by using the dependencies
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        final ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        final Lane lane0 = new Lane(0);
        final Lane lane1 = new Lane(1);

        // Test 1
        durations.clear();
        durations.put(lane0, 400);
        ItemToSchedule unit1 = new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>());
        items.add(unit1);

        // Test 11
        durations.clear();
        durations.put(lane1, 200);
        ItemToSchedule unit2 = new ItemToSchedule(11, durations, new ArrayList<ItemToSchedule>());

        // Test 2 (req Test 11)
        durations.clear();
        durations.put(lane1, 200);
        Collection<ItemToSchedule> required = new ArrayList<ItemToSchedule>();
        required.add(unit2);
        ItemToSchedule unit3 = new ItemToSchedule(2, durations, required);
        items.add(unit3);

        items.add(unit2);

        // Test 22 (req Test 11, Test 2)
        durations.clear();
        durations.put(lane1, 200);
        required = new ArrayList<ItemToSchedule>();
        required.add(unit2);
        required.add(unit3);
        items.add(new ItemToSchedule(22, durations, required));

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertEquals(600, result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }

    @Test
    public void testScheduleLocalOptimum2() {
        // A very simple local optimum that can be escaped by using the dependencies
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        final ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        final Lane lane0 = new Lane(0);
        final Lane lane1 = new Lane(1);

        // Test 1
        durations.clear();
        durations.put(lane0, 200);
        ItemToSchedule unit1 = new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>());
        items.add(unit1);

        // Test 2
        durations.clear();
        durations.put(lane1, 200);
        final ItemToSchedule unit2 = new ItemToSchedule(2, durations, new ArrayList<ItemToSchedule>());
        items.add(unit2);

        // Test 13 (req. Test 1)
        durations.clear();
        durations.put(lane0, 200);
        Collection<ItemToSchedule> required = new ArrayList<ItemToSchedule>();
        required.add(unit1);
        ItemToSchedule unit13 = new ItemToSchedule(13, durations, required);
        items.add(unit13);

        // Test 23 (req. Test 2)
        durations.clear();
        durations.put(lane1, 200);
        required = new ArrayList<ItemToSchedule>();
        required.add(unit2);
        ItemToSchedule unit23 = new ItemToSchedule(23, durations, required);
        items.add(unit23);

        // Test 4 (req. Test 13)
        durations.clear();
        durations.put(lane0, 200);
        required = new ArrayList<ItemToSchedule>();
        required.add(unit13);
        ItemToSchedule unit4 = new ItemToSchedule(4, durations, required);
        items.add(unit4);

        // Test 5 (req. Test 23)
        durations.clear();
        durations.put(lane1, 200);
        required = new ArrayList<ItemToSchedule>();
        required.add(unit23);
        ItemToSchedule unit5 = new ItemToSchedule(5, durations, required);
        items.add(unit5);

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertEquals(800, result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }

    @Test
    public void testScheduleLocalOptimum3() {
        // The fixed items create a local optimum that can be escaped by using the dependencies
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        final ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        final Lane lane0 = new Lane(0);
        final Lane lane1 = new Lane(1);

        // Test 1
        durations.clear();
        durations.put(lane0, 200);
        ItemToSchedule item1 = new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>());
        fixedItems.add(new ScheduledItem(item1, 0));

        // Test 21
        durations.clear();
        durations.put(lane0, 200);
        ItemToSchedule item2 = new ItemToSchedule(21, durations, new ArrayList<ItemToSchedule>());
        fixedItems.add(new ScheduledItem(item2, 200));

        // Test 11
        durations.clear();
        durations.put(lane1, 400);
        ItemToSchedule unit2 = new ItemToSchedule(11, durations, new ArrayList<ItemToSchedule>());
        items.add(unit2);

        // Test 2 (req Test 11)
        durations.clear();
        durations.put(lane1, 200);
        Collection<ItemToSchedule> required = new ArrayList<ItemToSchedule>();
        required.add(unit2);
        ItemToSchedule unit3 = new ItemToSchedule(2, durations, required);
        items.add(unit3);

        // Test 22 (req Test 11, Test 2)
        durations.clear();
        durations.put(lane1, 200);
        required = new ArrayList<ItemToSchedule>();
        required.add(unit2);
        required.add(unit3);
        items.add(new ItemToSchedule(22, durations, required));

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertEquals(1200, result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }

    private static List<ItemToSchedule> initializeItemsToForTest(int lanes, int itemsPerLane) {
        final ArrayList<ItemToSchedule> list = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        Map<Lane, List<ItemToSchedule>> testsForLane = new HashMap<Lane, List<ItemToSchedule>>();

        int counter = 0;
        Lane lane;

        for (int i = 0; i < lanes; i++) {
            testsForLane.put(new Lane(i), new ArrayList<ItemToSchedule>());
        }

        for (int i = 0; i < lanes * itemsPerLane; i++) {
            counter = i % lanes;
            lane = new Lane(counter);
            durations.clear();
            durations.put(lane, 100);
            List<ItemToSchedule> previousTests = testsForLane.get(lane);
            List<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>(previousTests);
            ItemToSchedule test = new ItemToSchedule(counter * 10 + i / lanes, durations, requiredItems);
            previousTests.add(test);
            list.add(test);
        }

        return list;
    }

    @Test
    public void testScheduleGeneratedLocalOptimum1() {
        // Creates a fixed number of tests for each lane, where each test depends on the previous ones. This inevitably
        // leads to some local optima (the more tests the more optima) that can be escaped by using the dependencies.
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        List<ItemToSchedule> items = initializeItemsToForTest(10, 3);

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertEquals(1200, result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }

    @Test
    public void testScheduleGeneratedLocalOptimum2() {
        // Creates a fixed number of tests for each lane, where each test depends on the previous ones. This inevitably
        // leads to some local optima (the more tests the more optima) that can be escaped by using the dependencies.
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        List<ItemToSchedule> items = initializeItemsToForTest(7, 7);

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertEquals(1300, result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }

    @Test
    public void testHarderLocalOptimum1() {
        // This local optimum cannot be solved by using the dependencies, but the items must be shifted.
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        final ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        final Lane lane0 = new Lane(0);
        final Lane lane1 = new Lane(1);

        // Test 1
        durations.clear();
        durations.put(lane0, 100);
        final ItemToSchedule unit1 = new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>());
        items.add(unit1);

        // Test 2
        durations.clear();
        durations.put(lane0, 100);
        final ItemToSchedule unit2 = new ItemToSchedule(2, durations, new ArrayList<ItemToSchedule>());
        items.add(unit2);

        // Test 11
        durations.clear();
        durations.put(lane1, 100);
        final ItemToSchedule unit11 = new ItemToSchedule(11, durations, new ArrayList<ItemToSchedule>());
        items.add(unit11);

        // Test 22
        durations.clear();
        durations.put(lane1, 100);
        final ItemToSchedule unit22 = new ItemToSchedule(22, durations, new ArrayList<ItemToSchedule>());
        items.add(unit22);

        // create a new special constraint

        ItemPairConstraint newConstraint = new ItemPairConstraint() {
            @Override
            public ConstraintPrediction predictDecision(ItemToSchedule movedItem, ItemToSchedule fixItem) {
                return new ConstraintPrediction(Prediction.UNKNOWN, Prediction.UNKNOWN, Prediction.UNKNOWN, 100);
            }

            @Override
            public boolean needsChecking(ItemToSchedule item1, ItemToSchedule item2) {
                return true;
            }

            @Override
            public ConstraintDecision check(ScheduledItem item1, ScheduledItem item2) {
                ScheduledItem itemA = null;
                ScheduledItem itemB = null;
                if (item1.getItemToSchedule() == unit1 && item2.getItemToSchedule() == unit2) {
                    itemA = item1;
                    itemB = item2;
                } else if (item2.getItemToSchedule() == unit1 && item1.getItemToSchedule() == unit2) {
                    itemA = item2;
                    itemB = item1;
                } else if (item1.getItemToSchedule() == unit11 && item2.getItemToSchedule() == unit22) {
                    itemA = item1;
                    itemB = item2;
                } else if (item2.getItemToSchedule() == unit11 && item1.getItemToSchedule() == unit22) {
                    itemA = item2;
                    itemB = item1;
                }

                if (itemA != null && itemB != null && (itemA.getStart() + itemA.getItemToSchedule().getMaxDuration()) != itemB.getStart()) {
                    return new ConstraintDecision(true, false, 100);
                }

                return new ConstraintDecision(true, true, 0);
            }
        };

        pairConstraints.add(newConstraint);
        manager = new ViolationsManager(singleConstraints, pairConstraints);
        scheduling = new HeuristicRepairScheduling(manager);

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertEquals(400, result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }

    @Test
    public void testHarderLocalOptimum2() {
        // This local optimum cannot be solved by using the dependencies or a right-shift, but the items must be shifted
        // to the left.
        ArrayList<ScheduledItem> fixedItems = new ArrayList<ScheduledItem>();
        final ArrayList<ItemToSchedule> items = new ArrayList<ItemToSchedule>();

        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        final Lane lane0 = new Lane(0);
        final Lane lane1 = new Lane(1);

        // Test 1
        durations.clear();
        durations.put(lane0, 100);
        final ItemToSchedule unit1 = new ItemToSchedule(1, durations, new ArrayList<ItemToSchedule>());
        items.add(unit1);

        // Test 2
        durations.clear();
        durations.put(lane0, 100);
        Collection<ItemToSchedule> req2 = new ArrayList<ItemToSchedule>();
        req2.add(unit1);
        final ItemToSchedule unit2 = new ItemToSchedule(2, durations, req2);
        items.add(unit2);

        // Test 3 (has to be scheduled before test 1 and test 4)
        durations.clear();
        durations.put(lane0, 100);
        final ItemToSchedule unit3 = new ItemToSchedule(3, durations, new ArrayList<ItemToSchedule>());
        items.add(unit3);

        // Test 4
        durations.clear();
        durations.put(lane1, 100);
        final ItemToSchedule unit4 = new ItemToSchedule(4, durations, new ArrayList<ItemToSchedule>());
        items.add(unit4);

        // Test 5
        durations.clear();
        durations.put(lane1, 100);
        final ItemToSchedule unit5 = new ItemToSchedule(5, durations, new ArrayList<ItemToSchedule>());
        items.add(unit5);

        // create a new special constraint

        ItemPairConstraint newConstraint = new ItemPairConstraint() {
            @Override
            public ConstraintPrediction predictDecision(ItemToSchedule movedItem, ItemToSchedule fixItem) {
                return new ConstraintPrediction(Prediction.UNKNOWN, Prediction.UNKNOWN, Prediction.UNKNOWN, 0);
            }

            @Override
            public boolean needsChecking(ItemToSchedule item1, ItemToSchedule item2) {
                return true;
            }

            @Override
            public ConstraintDecision check(ScheduledItem item1, ScheduledItem item2) {
                ScheduledItem itemA = null;
                ScheduledItem itemB = null;
                if (item1.getItemToSchedule() == unit1 && item2.getItemToSchedule() == unit3) {
                    itemA = item1;
                    itemB = item2;
                } else if (item2.getItemToSchedule() == unit1 && item1.getItemToSchedule() == unit3) {
                    itemA = item2;
                    itemB = item1;
                } else if (item1.getItemToSchedule() == unit4 && item2.getItemToSchedule() == unit3) {
                    itemA = item1;
                    itemB = item2;
                } else if (item2.getItemToSchedule() == unit4 && item1.getItemToSchedule() == unit3) {
                    itemA = item2;
                    itemB = item1;
                }

                if (itemA != null && itemB != null && (itemB.getStart() + itemB.getItemToSchedule().getMaxDuration()) > itemA.getStart()) {
                    return new ConstraintDecision(true, false, 100);
                }

                return new ConstraintDecision(true, true, 0);
            }
        };

        pairConstraints.add(newConstraint);
        manager = new ViolationsManager(singleConstraints, pairConstraints);
        scheduling = new HeuristicRepairScheduling(manager);

        SchedulePlan result = scheduling.schedule(items, fixedItems);

        assertEquals(items.size() + fixedItems.size(), result.getScheduledItems().size());
        assertEquals(300, result.getMakespan());
        assertTrue(allConstraintsSatisfied(result));
    }
}
