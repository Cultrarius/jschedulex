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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mockito.Mockito;

public class ItemToScheduleTest {

    @Test(expected = IllegalArgumentException.class)
    public void testItemToScheduleWrongArgument1() {
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = null;
        new ItemToSchedule(0, durations, requiredItems);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testItemToScheduleWrongArgument2() {
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        new ItemToSchedule(0, durations, requiredItems);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testItemToScheduleWrongArgument3() {
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        durations.put(new Lane(0), 0);
        new ItemToSchedule(0, durations, requiredItems);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testItemToScheduleWrongArgument4() {
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        durations.put(new Lane(0), -1);
        new ItemToSchedule(0, durations, requiredItems);
    }

    @Test
    public void testGetDuration() {
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        Lane lane = new Lane(0);
        durations.put(lane, 13);
        ItemToSchedule item = new ItemToSchedule(0, durations, requiredItems);
        assertEquals(13, item.getDuration(lane));
    }
    
    @Test(expected = NullPointerException.class)
    public void testGetDurationWrongLane() {
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        Lane lane = new Lane(0);
        durations.put(lane, 13);
        ItemToSchedule item = new ItemToSchedule(0, durations, requiredItems);
        item.getDuration(new Lane(1));
    }
    
    @Test
    public void testGetDurations() {
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        Lane lane = new Lane(0);
        Lane lane2 = new Lane(1);
        durations.put(lane, 13);
        durations.put(lane2, 1);
        ItemToSchedule item = new ItemToSchedule(0, durations, requiredItems);
        assertEquals(1, item.getDuration(lane2));
        assertEquals(13, item.getDuration(lane));
    }

    @Test
    public void testGetMaxDuration() {
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        Lane lane = new Lane(0);
        durations.put(lane, 13);
        ItemToSchedule item = new ItemToSchedule(0, durations, requiredItems);
        assertEquals(13, item.getMaxDuration());
    }
    
    @Test
    public void testGetMaxDuration2() {
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        Lane lane = new Lane(0);
        Lane lane2 = new Lane(1);
        durations.put(lane, 13);
        durations.put(lane2, 1);
        ItemToSchedule item = new ItemToSchedule(0, durations, requiredItems);
        assertEquals(13, item.getMaxDuration());
    }

    @Test
    public void testGetDurationSummary() {
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        Lane lane = new Lane(0);
        durations.put(lane, 13);
        ItemToSchedule item = new ItemToSchedule(0, durations, requiredItems);
        assertEquals(13, item.getDurationSummary());
    }
    
    @Test
    public void testGetDurationSummary2() {
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        Lane lane = new Lane(0);
        Lane lane2 = new Lane(1);
        durations.put(lane, 13);
        durations.put(lane2, 1);
        ItemToSchedule item = new ItemToSchedule(0, durations, requiredItems);
        assertEquals(14, item.getDurationSummary());
    }

    @Test
    public void testGetAffectedLanes() {
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        Lane lane = new Lane(0);
        durations.put(lane, 13);
        ItemToSchedule item = new ItemToSchedule(0, durations, requiredItems);
        Collection<Lane> lanes = item.getAffectedLanes();
        assertEquals(1, lanes.size());
        assertTrue(lanes.contains(lane));
    }
    
    @Test
    public void testGetAffectedLanes2() {
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        Lane lane = new Lane(0);
        Lane lane2 = new Lane(1);
        durations.put(lane, 13);
        durations.put(lane2, 1);
        ItemToSchedule item = new ItemToSchedule(0, durations, requiredItems);
        Collection<Lane> lanes = item.getAffectedLanes();
        assertEquals(2, lanes.size());
        assertTrue(lanes.contains(lane));
        assertTrue(lanes.contains(lane2));
    }

    @Test
    public void testGetRequiredItems1() {
        
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        Lane lane = new Lane(0);
        durations.put(lane, 1);
        ItemToSchedule item = new ItemToSchedule(0, durations, requiredItems);
        List<ItemToSchedule> items = item.getRequiredItems();
        assertEquals(0, items.size());
    }
    
    @Test
    public void testGetRequiredItems2() {
        ItemToSchedule req1 = Mockito.mock(ItemToSchedule.class);
        
        Collection<ItemToSchedule> requiredItems = new ArrayList<ItemToSchedule>();
        requiredItems.add(req1);
        Map<Lane, Integer> durations = new HashMap<Lane, Integer>();
        Lane lane = new Lane(0);
        durations.put(lane, 1);
        ItemToSchedule item = new ItemToSchedule(0, durations, requiredItems);
        List<ItemToSchedule> items = item.getRequiredItems();
        assertEquals(1, items.size());
        assertTrue(items.contains(req1));
    }

}
