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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import cern.acctesting.service.schedule.ItemToSchedule;
import cern.acctesting.service.schedule.ScheduledItem;
import cern.acctesting.service.schedule.constraint.ConstraintPrediction;
import cern.acctesting.service.schedule.constraint.ConstraintPrediction.Prediction;
import cern.acctesting.service.schedule.constraint.ItemPairConstraint;
import cern.acctesting.service.schedule.impl.ViolationsManager.ConstraintPartner;

public class Predictor {
    private final Map<ItemToSchedule, Set<ConstraintPartner>> constraintMap;
    private final Map<ItemToSchedule, PredictionData> predictionMap;
    private final BlockStore blockStore;
    private final ForkJoinPool executor;
    
    private SchedulePlan plan;

    public Predictor(SchedulePlan plan, Map<ItemToSchedule, Set<ConstraintPartner>> constraintMap) {
        this.plan = plan;
        this.constraintMap = constraintMap;
        predictionMap = new HashMap<ItemToSchedule, PredictionData>(constraintMap.size());
        blockStore = new BlockStore();
        executor = new ForkJoinPool();

        initializePredictionMap();
    }

    private void initializePredictionMap() {
        for (Entry<ItemToSchedule, Set<ConstraintPartner>> entry : constraintMap.entrySet()) {
            Map<ItemToSchedule, PredictionBlocks> predictionBlocks = new HashMap<ItemToSchedule, PredictionBlocks>(entry.getValue().size());
            PredictionBlocks blocks;
            for (ConstraintPartner partner : entry.getValue()) {
                blocks = null;
                for (ItemPairConstraint constraint : partner.getConstraints()) {
                    ConstraintPrediction decision = constraint.predictDecision(entry.getKey(), partner.getPartnerItem());
                    PredictionBlocks newBlocks = createBlocksFromDecision(entry.getKey(), partner.getPartnerItem(), decision);
                    if (blocks == null) {
                        blocks = newBlocks;
                    } else {
                        blocks = blocks.aggregate(newBlocks, Method.MERGE_MAX);
                    }
                }
                predictionBlocks.put(partner.getPartnerItem(), blocks);
            }
            predictionMap.put(entry.getKey(), new PredictionData(predictionBlocks));
        }
    }

    public void itemWasMoved(ItemToSchedule movedItem) {
        for (ConstraintPartner partner : constraintMap.get(movedItem)) {
            final ItemToSchedule partnerItem = partner.getPartnerItem();
            predictionMap.get(partnerItem).flaggedDirty.add(movedItem);
        }
    }

    private PredictionBlocks createBlocksFromDecision(ItemToSchedule movedItem, ItemToSchedule fixItem, ConstraintPrediction decision) {
        PredictionBlocks blocks = null;

        // the block conflicts when before
        if (Prediction.CONFLICT.equals(decision.getConflictsWhenBefore())) {
            blocks = getConflictBeforeBlock(movedItem, decision);
        }
        // it is possible (but unknown) that the block conflicts when before
        else if (Prediction.UNKNOWN.equals(decision.getConflictsWhenBefore())) {
            blocks = getUnknownBeforeBlock(decision);
        }

        // the block conflicts when starting together (and maybe when overlapping)
        if (Prediction.CONFLICT.equals(decision.getConflictsWhenTogether())) {
            PredictionBlocks newBlocks = getConflictDuringBlock(movedItem, fixItem, decision);
            if (blocks == null) {
                blocks = newBlocks;
            } else {
                blocks = blocks.aggregate(newBlocks, Method.MERGE_MAX);
            }
        }
        // it is possible (but unknown) that the block conflicts when starting together (and maybe when overlapping)
        else if (Prediction.UNKNOWN.equals(decision.getConflictsWhenTogether())) {
            PredictionBlocks newBlocks = getUnknownDuringBlock(movedItem, fixItem, decision);
            if (blocks == null) {
                blocks = newBlocks;
            } else {
                blocks = blocks.aggregate(newBlocks, Method.MERGE_MAX);
            }
        }

        // the block conflicts when after
        if (Prediction.CONFLICT.equals(decision.getConflictsWhenAfter())) {
            PredictionBlocks newBlocks = getConflictAfterBlock(movedItem, fixItem, decision);
            if (blocks == null) {
                blocks = newBlocks;
            } else {
                blocks = blocks.aggregate(newBlocks, Method.MERGE_MAX);
            }
        }
        // it is possible (but unknown) that the block conflicts when after
        else if (Prediction.UNKNOWN.equals(decision.getConflictsWhenAfter())) {
            PredictionBlocks newBlocks = getUnknownAfterBlock(movedItem, fixItem, decision);
            if (blocks == null) {
                blocks = newBlocks;
            } else {
                blocks = blocks.aggregate(newBlocks, Method.MERGE_MAX);
            }
        }

        if (blocks == null) {
            return new PredictionBlocks(blockStore.getBeforeBlock(0, 0, -1), Collections.<MiddleBlock> emptyList(),
                    blockStore.getAfterBlock(0, 0, 0));
        } else {
            return blocks;
        }
    }

    private PredictionBlocks getUnknownAfterBlock(ItemToSchedule movedItem, ItemToSchedule fixItem, ConstraintPrediction decision) {
        AfterBlock afterBlock = blockStore.getAfterBlock(0, decision.getPredictedConflictValue(),
                fixItem.getMaxDuration() - (movedItem.getMaxDuration() - 1));
        return new PredictionBlocks(blockStore.getBeforeBlock(0, 0, fixItem.getMaxDuration() - movedItem.getMaxDuration()),
                Collections.<MiddleBlock> emptyList(), afterBlock);
    }

    private PredictionBlocks getConflictAfterBlock(ItemToSchedule movedItem, ItemToSchedule fixItem, ConstraintPrediction decision) {
        List<MiddleBlock> middleBlocks = new ArrayList<MiddleBlock>(1);
        if (movedItem.getMaxDuration() > 1) {
            middleBlocks.add(blockStore.getMiddleBlock(0, decision.getPredictedConflictValue(),
                    fixItem.getMaxDuration() - (movedItem.getMaxDuration() - 1), fixItem.getMaxDuration() - 1));
        }
        AfterBlock afterBlock = blockStore.getAfterBlock(decision.getPredictedConflictValue(), 0, fixItem.getMaxDuration());
        return new PredictionBlocks(blockStore.getBeforeBlock(0, 0, fixItem.getMaxDuration() - movedItem.getMaxDuration()), middleBlocks,
                afterBlock);
    }

    private PredictionBlocks getUnknownDuringBlock(ItemToSchedule movedItem, ItemToSchedule fixItem, ConstraintPrediction decision) {
        BeforeBlock beforeBlock = blockStore.getBeforeBlock(0, 0, -movedItem.getMaxDuration());
        List<MiddleBlock> middleBlocks = new ArrayList<MiddleBlock>(1);
        middleBlocks.add(blockStore.getMiddleBlock(0, decision.getPredictedConflictValue(), -(movedItem.getMaxDuration() - 1),
                fixItem.getMaxDuration() - 1));
        return new PredictionBlocks(beforeBlock, middleBlocks, blockStore.getAfterBlock(0, 0, fixItem.getMaxDuration()));
    }

    private PredictionBlocks getConflictDuringBlock(ItemToSchedule movedItem, ItemToSchedule fixItem, ConstraintPrediction decision) {
        BeforeBlock beforeBlock = blockStore.getBeforeBlock(0, 0, -movedItem.getMaxDuration());
        List<MiddleBlock> middleBlocks = new ArrayList<MiddleBlock>(3);
        middleBlocks.add(blockStore.getMiddleBlock(0, decision.getPredictedConflictValue(), -(movedItem.getMaxDuration() - 1), -1));
        middleBlocks.add(blockStore.getMiddleBlock(decision.getPredictedConflictValue(), 0, 0, 0));
        if (fixItem.getMaxDuration() > 1) {
            middleBlocks.add(blockStore.getMiddleBlock(0, decision.getPredictedConflictValue(), 1, fixItem.getMaxDuration() - 1));
        }
        return new PredictionBlocks(beforeBlock, middleBlocks, blockStore.getAfterBlock(0, 0, fixItem.getMaxDuration()));
    }

    private PredictionBlocks getUnknownBeforeBlock(ConstraintPrediction decision) {
        return new PredictionBlocks(blockStore.getBeforeBlock(0, decision.getPredictedConflictValue(), -1),
                Collections.<MiddleBlock> emptyList(), blockStore.getAfterBlock(0, 0, 0));
    }

    private PredictionBlocks getConflictBeforeBlock(ItemToSchedule movedItem, ConstraintPrediction decision) {
        BeforeBlock beforeBlock = blockStore.getBeforeBlock(decision.getPredictedConflictValue(), 0, -movedItem.getMaxDuration());
        List<MiddleBlock> middleBlocks = new ArrayList<MiddleBlock>(1);
        middleBlocks.add(blockStore.getMiddleBlock(0, decision.getPredictedConflictValue(), -(movedItem.getMaxDuration() - 1), -1));
        return new PredictionBlocks(beforeBlock, middleBlocks, blockStore.getAfterBlock(0, 0, 0));
    }

    public ConflictPrediction predictConflicts(ScheduledItem item) {
        PredictionData data = predictionMap.get(item.getItemToSchedule());
        Block predictedBlock = data.getBlockForTime(item.getStart());
        return new ConflictPrediction(predictedBlock.getValues());
    }

    public class ConflictPrediction {
        private final Block.PredictionValues values;

        public ConflictPrediction(Block.PredictionValues values) {
            this.values = values;
        }

        public int getDefinedHardConflictValue() {
            return values.conflictValue;
        }

        public int getPossibleHardConflictValue() {
            return values.unknownValue;
        }
    }

    private class PredictionData {
        private Set<ItemToSchedule> flaggedDirty;
        private Map<ItemToSchedule, PredictionBlocks> predictionBlocks;
        private PredictionBlocks aggregated;

        public PredictionData(Map<ItemToSchedule, PredictionBlocks> predictionBlocks) {
            this.predictionBlocks = predictionBlocks;
            flaggedDirty = new HashSet<ItemToSchedule>();
            aggregated = null;
        }

        public Block getBlockForTime(int start) {
            if (aggregated == null) {
                createAggregationBlock();
            } else if (!flaggedDirty.isEmpty()) {
                if (flaggedDirty.size() < (predictionBlocks.size() / 2)) {
                    updateAggregationBlock();
                } else {
                    createAggregationBlock();
                }
            }
            return aggregated.getBlockForTime(start);
        }

        private void updateAggregationBlock() {
            // subtract the old dirty flagged blocks
            List<PredictionBlocks> blocksToAggregate = new ArrayList<PredictionBlocks>(flaggedDirty.size());
            for (ItemToSchedule item : flaggedDirty) {
                blocksToAggregate.add(predictionBlocks.get(item));
            }
            aggregated = aggregated.aggregate(blocksToAggregate, Method.SUBTRACT);

            // update them and then add them to the aggregate again
            blocksToAggregate.clear();
            for (ItemToSchedule item : flaggedDirty) {
                int itemStart = plan.getScheduledItem(item).getStart();
                PredictionBlocks itemBlocks = predictionBlocks.get(item);
                itemBlocks.setStartPosition(itemStart);
                blocksToAggregate.add(itemBlocks);
            }
            aggregated = aggregated.aggregate(blocksToAggregate, Method.ADD);
            flaggedDirty.clear();
        }

        private void createAggregationBlock() {
            flaggedDirty.clear();
            aggregated = new PredictionBlocks(blockStore.getBeforeBlock(0, 0, -1), Collections.<MiddleBlock> emptyList(),
                    blockStore.getAfterBlock(0, 0, 0), 0);
            List<PredictionBlocks> blocksToAggregate = new ArrayList<PredictionBlocks>();
            for (Entry<ItemToSchedule, PredictionBlocks> entry : predictionBlocks.entrySet()) {
                int itemStart = plan.getScheduledItem(entry.getKey()).getStart();
                PredictionBlocks itemBlocks = entry.getValue();
                itemBlocks.setStartPosition(itemStart);
                blocksToAggregate.add(itemBlocks);
            }
            aggregated = aggregated.aggregate(blocksToAggregate, Method.ADD);
        }
    }

    private class PredictionBlocks {
        private int startPosition;
        private final BeforeBlock beforeBlock;
        private final List<MiddleBlock> middleBlocks;
        private final AfterBlock afterBlock;

        public PredictionBlocks(BeforeBlock beforeBlock, List<MiddleBlock> middleBlocks, AfterBlock afterBlock) {
            this(beforeBlock, middleBlocks, afterBlock, 0);
        }

        public PredictionBlocks(BeforeBlock beforeBlock, List<MiddleBlock> middleBlocks, AfterBlock afterBlock, int startPosition) {
            this.beforeBlock = beforeBlock;
            this.middleBlocks = new ArrayList<MiddleBlock>(middleBlocks);
            this.afterBlock = afterBlock;

            setStartPosition(startPosition);
        }

        public PredictionBlocks aggregate(PredictionBlocks blockToAggregate, Method method) {
            return aggregate(Collections.singletonList(blockToAggregate), method);
        }

        public PredictionBlocks aggregate(List<PredictionBlocks> blocksToAggregate, Method method) {

            // create a set of the times used
            SortedSet<Integer> startTimes = new TreeSet<Integer>();
            SortedSet<Integer> endTimes = new TreeSet<Integer>();

            addTimes(startTimes, endTimes);
            for (PredictionBlocks blocks : blocksToAggregate) {
                blocks.addTimes(startTimes, endTimes);
            }

            Iterator<Integer> itS = startTimes.iterator();
            Iterator<Integer> itE = endTimes.iterator();
            List<Block> oldBlocks = new ArrayList<Block>(blocksToAggregate.size() + 1);

            // create the new before block
            int time = itE.next();
            for (PredictionBlocks blocks : blocksToAggregate) {
                oldBlocks.add(blocks.beforeBlock);
            }
            BeforeBlock newBeforeBlock = blockStore.getBeforeBlock(getNewConflictValue(beforeBlock, oldBlocks, method),
                    getNewUnknownValue(beforeBlock, oldBlocks, method), time);

            List<MiddleBlock> newMiddleBlocks = new ArrayList<MiddleBlock>(startTimes.size());
            AfterBlock newAfterBlock;
            List<MiddleBlockTask> tasks = new ArrayList<MiddleBlockTask>();
            while (true) {
                time = itS.next();
                if (!itS.hasNext()) {
                    if (itE.hasNext()) {
                        throw new IllegalStateException("There is just the start time for the last block, but still more end times left.");
                    }
                    // create the after block
                    oldBlocks.clear();
                    for (PredictionBlocks blocks : blocksToAggregate) {
                        oldBlocks.add(blocks.afterBlock);
                    }
                    newAfterBlock = blockStore.getAfterBlock(getNewConflictValue(afterBlock, oldBlocks, method),
                            getNewUnknownValue(afterBlock, oldBlocks, method), time);
                    break;
                } else {
                    // create another middle block
                    oldBlocks.clear();
                    MiddleBlockTask task = new MiddleBlockTask(this, blocksToAggregate, method, time, itE.next());
                    executor.submit(task);
                    tasks.add(task);
                }
            }

            // TODO: merge adjacent equal blocks with equal value ?
            
            for (MiddleBlockTask middleBlockTask : tasks) {
                try {
                    newMiddleBlocks.add(middleBlockTask.get());
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            return new PredictionBlocks(newBeforeBlock, newMiddleBlocks, newAfterBlock, startPosition);
        }

        private class MiddleBlockTask extends ForkJoinTask<MiddleBlock> {
            private static final long serialVersionUID = 1L;
            MiddleBlock result;
            private final PredictionBlocks reference;
            private final List<PredictionBlocks> blocksToAggregate;
            private final Method method;
            private final int time;
            private final int endTime;

            public MiddleBlockTask(PredictionBlocks reference, List<PredictionBlocks> blocksToAggregate, Method method, int time,
                    int endTime) {
                this.reference = reference;
                this.blocksToAggregate = blocksToAggregate;
                this.method = method;
                this.time = time;
                this.endTime = endTime;
            }

            @Override
            public MiddleBlock getRawResult() {
                return result;
            }

            @Override
            protected void setRawResult(MiddleBlock value) {
                result = value;
            }

            @Override
            protected boolean exec() {
                List<Block> oldBlocks = new ArrayList<Predictor.Block>();
                for (PredictionBlocks blocks : blocksToAggregate) {
                    oldBlocks.add(blocks.getBlockForTime(time));
                }
                Block referenceBlock = reference.getBlockForTime(time);

                result = blockStore.getMiddleBlock(getNewConflictValue(referenceBlock, oldBlocks, method),
                        getNewUnknownValue(referenceBlock, oldBlocks, method), time, endTime);
                return true;
            }
        }

        private int getNewUnknownValue(Block reference, List<Block> oldBlocks, Method method) {
            int newValue = reference.getValues().unknownValue;
            if (Method.ADD.equals(method)) {
                for (Block block : oldBlocks) {
                    newValue += block.getValues().unknownValue;
                }
            } else if (Method.MERGE_MAX.equals(method)) {
                for (Block block : oldBlocks) {
                    if (newValue < block.getValues().unknownValue) {
                        newValue = block.getValues().unknownValue;
                    }
                }
            } else if (Method.SUBTRACT.equals(method)) {
                for (Block block : oldBlocks) {
                    newValue -= block.getValues().unknownValue;
                }
            }
            return newValue;
        }

        private int getNewConflictValue(Block referenceBlock, List<Block> oldBlocks, Method method) {
            int newValue = referenceBlock.getValues().conflictValue;
            if (Method.ADD.equals(method)) {
                for (Block block : oldBlocks) {
                    newValue += block.getValues().conflictValue;
                }
            } else if (Method.MERGE_MAX.equals(method)) {
                for (Block block : oldBlocks) {
                    if (newValue < block.getValues().conflictValue) {
                        newValue = block.getValues().conflictValue;
                    }
                }
            } else if (Method.SUBTRACT.equals(method)) {
                for (Block block : oldBlocks) {
                    newValue -= block.getValues().conflictValue;
                }
            }
            return newValue;
        }

        public Block getBlockForTime(int time) {
            time -= startPosition;
            if (beforeBlock.containsPoint(time)) {
                return beforeBlock;
            } else if (afterBlock.containsPoint(time)) {
                return afterBlock;
            } else {
                // binary search for the middle block
                int low = 0;
                int high = middleBlocks.size() - 1;

                while (low <= high) {
                    int mid = (low + high) >>> 1;
                    MiddleBlock midVal = middleBlocks.get(mid);
                    if (time < (midVal.start)) {
                        high = mid - 1;
                    } else if (time > (midVal.end)) {
                        low = mid + 1;
                    } else {
                        return midVal;
                    }
                }
            }
            throw new IllegalStateException("This code segment should never be reached. For every time t a Block should be found!");
        }

        private void addTimes(SortedSet<Integer> startTimes, SortedSet<Integer> endTimes) {
            endTimes.add(beforeBlock.end + startPosition);
            startTimes.add(afterBlock.start + startPosition);
            for (MiddleBlock middleBlock : middleBlocks) {
                startTimes.add(middleBlock.start + startPosition);
                endTimes.add(middleBlock.end + startPosition);
            }
        }

        public void setStartPosition(int startPosition) {
            this.startPosition = startPosition;
        }
    }

    private enum Method {
        ADD, MERGE_MAX, SUBTRACT
    }

    private abstract class Block {
        private final PredictionValues values;

        public Block(int conflictValue, int unknownValue) {
            this.values = new PredictionValues(conflictValue, unknownValue);
        }

        public abstract boolean containsPoint(int point);

        public PredictionValues getValues() {
            return values;
        }

        public class PredictionValues {
            private final int conflictValue;
            private final int unknownValue;

            public PredictionValues(int conflictValue, int unknownValue) {
                if (conflictValue < 0 || unknownValue < 0) {
                    throw new IllegalArgumentException();
                }
                this.conflictValue = conflictValue;
                this.unknownValue = unknownValue;
            }
        }
    }

    private class BeforeBlock extends Block {
        private final int end;

        public BeforeBlock(int conflictValue, int unknownValue, int end) {
            super(conflictValue, unknownValue);
            this.end = end;
        }

        @Override
        public boolean containsPoint(int point) {
            return point <= end;
        }
    }

    private class AfterBlock extends Block {
        private final int start;

        public AfterBlock(int conflictValue, int unknownValue, int start) {
            super(conflictValue, unknownValue);
            this.start = start;
        }

        @Override
        public boolean containsPoint(int point) {
            return point >= start;
        }
    }

    private class MiddleBlock extends Block {
        private final int start;
        private final int end;

        public MiddleBlock(int conflictValue, int unknownValue, int start, int end) {
            super(conflictValue, unknownValue);
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean containsPoint(int point) {
            return point <= end && point >= start;
        }
    }

    private class BlockStore {

        private final Map<Integer, List<MiddleBlock>> middleBlockStore;
        private final Map<Integer, List<BeforeBlock>> beforeBlockStore;
        private final Map<Integer, List<AfterBlock>> afterBlockStore;

        public BlockStore() {
            middleBlockStore = new ConcurrentHashMap<Integer, List<MiddleBlock>>(50000);
            beforeBlockStore = new ConcurrentHashMap<Integer, List<BeforeBlock>>(25000);
            afterBlockStore = new ConcurrentHashMap<Integer, List<AfterBlock>>(25000);
        }

        public MiddleBlock getMiddleBlock(int conflictValue, int unknownValue, int start, int end) {
            Integer key = ((conflictValue * 2 + unknownValue) * 2 + start) * 2 + end;
            List<MiddleBlock> blockList = middleBlockStore.get(key);
            if (blockList == null) {
                blockList = new CopyOnWriteArrayList<Predictor.MiddleBlock>();
                middleBlockStore.put(key, blockList);
            }
            for (MiddleBlock middleBlock : blockList) {
                final Block.PredictionValues values = middleBlock.getValues();
                if (middleBlock.start == start && middleBlock.end == end && values.conflictValue == conflictValue
                        && values.unknownValue == unknownValue) {
                    return middleBlock;
                }
            }
            MiddleBlock newBlock = new MiddleBlock(conflictValue, unknownValue, start, end);
            blockList.add(newBlock);
            return newBlock;
        }

        public BeforeBlock getBeforeBlock(int conflictValue, int unknownValue, int end) {
            Integer key = (conflictValue * 2 + unknownValue) * 2 + end;
            List<BeforeBlock> blockList = beforeBlockStore.get(key);
            if (blockList == null) {
                blockList = new CopyOnWriteArrayList<Predictor.BeforeBlock>();
                beforeBlockStore.put(key, blockList);
            }
            for (BeforeBlock beforeBlock : blockList) {
                final Block.PredictionValues values = beforeBlock.getValues();
                if (beforeBlock.end == end && values.conflictValue == conflictValue && values.unknownValue == unknownValue) {
                    return beforeBlock;
                }
            }
            BeforeBlock newBlock = new BeforeBlock(conflictValue, unknownValue, end);
            blockList.add(newBlock);
            return newBlock;
        }

        public AfterBlock getAfterBlock(int conflictValue, int unknownValue, int start) {
            Integer key = (conflictValue * 2 + unknownValue) * 2 + start;
            List<AfterBlock> blockList = afterBlockStore.get(key);
            if (blockList == null) {
                blockList = new CopyOnWriteArrayList<Predictor.AfterBlock>();
                afterBlockStore.put(key, blockList);
            }
            for (AfterBlock afterBlock : blockList) {
                final Block.PredictionValues values = afterBlock.getValues();
                if (afterBlock.start == start && values.conflictValue == conflictValue && values.unknownValue == unknownValue) {
                    return afterBlock;
                }
            }
            AfterBlock newBlock = new AfterBlock(conflictValue, unknownValue, start);
            blockList.add(newBlock);
            return newBlock;
        }
    }

    public void planHasBeenUpdated(SchedulePlan oldPlan, SchedulePlan newPlan) {
        //TODO: improve the update
        plan = newPlan;
        for (ScheduledItem item : newPlan.getScheduledItems()) {
            itemWasMoved(item.getItemToSchedule());
        }
    }
}
