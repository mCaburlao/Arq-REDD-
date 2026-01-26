package jabs.log;

import jabs.ledgerdata.Block;
import jabs.ledgerdata.Tx;
import jabs.ledgerdata.DoubleSpendTracker;
import jabs.network.node.nodes.Node;
import jabs.simulator.event.AbstractLogEvent;
import jabs.simulator.event.Event;
import jabs.simulator.event.BlockFinalizationEvent;
import jabs.simulator.event.BlockForkedEvent;
import jabs.simulator.event.BlockProposalEvent;
import jabs.scenario.ForkTracker;
import jabs.metrics.SimulationMetrics;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Enhanced block finalization logger that tracks all 5 metrics:
 * 1. Tb  - Average block finalization time (s/block)
 * 2. Cb  - Average network traffic for finalization (MB/block)
 * 3. Bf  - Fork rate (percentage)
 * 4. BFT - Byzantine Fault Tolerance (percentage)
 * 5. Pdv - Double-spending success probability (percentage)
 */
public class EnhancedBlockFinalizationLogger extends AbstractCSVLogger {
    private final SimulationMetrics metrics;
    private final DoubleSpendTracker doubleSpendTracker;
    
    // Track finalized blocks by height for double-spend detection
    private final Map<Integer, Set<Object>> blockTransactions; // height -> set of tx ids
    
    // Track all proposed blocks for fork detection
    private final Map<Integer, Set<Object>> blocksByHeight;   // height -> set of block ids
    
    // Track previously finalized blocks for reorg detection
    private final Set<Object> finalizedBlockIds;
    // Optional external fork tracker for scenario-level tracking
    private ForkTracker forkTracker;
    
    /**
     * Creates an enhanced CSV logger with metrics tracking
     * @param writer this is output CSV of the logger
     * @param metrics the metrics collector
     */
    public EnhancedBlockFinalizationLogger(Writer writer, SimulationMetrics metrics) {
        super(writer);
        this.metrics = metrics;
        this.doubleSpendTracker = new DoubleSpendTracker();
        this.blockTransactions = new HashMap<>();
        this.blocksByHeight = new HashMap<>();
        this.finalizedBlockIds = new HashSet<>();
    }

    /**
     * Creates an enhanced CSV logger with metrics tracking
     * @param path this is output path of CSV file
     * @param metrics the metrics collector
     */
    public EnhancedBlockFinalizationLogger(Path path, SimulationMetrics metrics) throws IOException {
        super(path);
        this.metrics = metrics;
        this.doubleSpendTracker = new DoubleSpendTracker();
        this.blockTransactions = new HashMap<>();
        this.blocksByHeight = new HashMap<>();
        this.finalizedBlockIds = new HashSet<>();
    }

    /**
     * Optionally attach a ForkTracker to mirror fork registration to scenario-level tracker
     */
    public void setForkTracker(ForkTracker forkTracker) {
        this.forkTracker = forkTracker;
    }

    @Override
    protected String csvStartingComment() {
        return String.format("Simulation name: %s      Number of nodes: %d      Network type: %s", 
            scenario.getName(),
            this.scenario.getNetwork().getAllNodes().size(), 
            this.scenario.getNetwork().getClass().getSimpleName());
    }

    @Override
    protected boolean csvOutputConditionBeforeEvent(Event event) {
        return false;
    }

    @Override
    protected boolean csvOutputConditionAfterEvent(Event event) {
        if (event instanceof BlockFinalizationEvent) {
            BlockFinalizationEvent finalizationEvent = (BlockFinalizationEvent) event;
            Block block = finalizationEvent.getBlock();
            Object blockId = block.hashCode();
            
            // Track this block by height for fork detection
            Set<Object> blocksAtHeight = blocksByHeight.computeIfAbsent(block.getHeight(), k -> new HashSet<>());
            boolean isNewBlockAtHeight = blocksAtHeight.add(blockId);
            
            // If this is the 2nd+ block at this height, register all but first as forked
            // Only register once per height when fork is detected
            if (isNewBlockAtHeight && blocksAtHeight.size() > 1) {
                // Count all blocks except the first one as forked
                // (size-1 additional forked blocks when 2nd+ blocks appear)
                metrics.recordForkedBlock(block.getHeight());
            }
            
            // Mark as finalized
            finalizedBlockIds.add(blockId);
            
            // Collect Metric 1: Block finalization time (Tb)
            double finalizationTime = this.scenario.getSimulator().getSimulationTime() - block.getCreationTime();
            metrics.recordBlockFinalizationTime(finalizationTime);
            
            // Collect Metric 2: Network traffic for finalization (Cb)
            long traffic = finalizationEvent.getTrafficUntilFinalization();
            metrics.recordBlockTraffic(traffic);
            
            // Collect Metric 3: Fork rate (Bf) - tracked separately when blocks are discarded
            metrics.recordBlockGenerated();
            
            // Collect Metric 5: Double-spending (Pdv)
            // Extract and track transactions from this finalized block
            Set<Object> txIds = extractTransactionsFromBlock(block);
            if (!txIds.isEmpty()) {
                blockTransactions.put(block.getHeight(), txIds);
                for (Object txId : txIds) {
                    boolean isDoubleSpend = doubleSpendTracker.recordTransaction(
                        txId,
                        block.getHeight(),
                        block.hashCode() + ""
                    );
                    if (isDoubleSpend) {
                        metrics.recordDoubleSpendAttempt();
                        // Mark as confirmed successful since both blocks finalized
                        doubleSpendTracker.confirmDoubleSpendSuccess(txId);
                        metrics.recordDoubleSpendSuccess();
                    }
                }
            }
            
            return true;
        } else if (event instanceof BlockProposalEvent) {
            BlockProposalEvent proposal = (BlockProposalEvent) event;
            Block block = proposal.getBlock();
            // Track proposed block for fork detection
            recordProposedBlock(block);
            if (this.forkTracker != null) {
                try {
                    this.forkTracker.registerBlockProposal(block, proposal.getNode());
                } catch (Exception ignored) {}
            }
            return false;
        } else if (event instanceof BlockForkedEvent) {
            // Metric 3: Track fork when block is orphaned
            BlockForkedEvent forkedEvent = (BlockForkedEvent) event;
            Block block = forkedEvent.getBlock();
            metrics.recordForkedBlock(block.getHeight());
            blockTransactions.remove(block.getHeight()); // Remove from tracking
            return true;
        }
        return false;
    }
    
    /**
     * Extract transaction IDs from a block using reflection
     * Attempts to find and invoke getTransactions() or equivalent method
     * Falls back to reflection on private fields if needed
     * 
     * @param block The block to extract transactions from
     * @return Set of transaction IDs (using hash as ID)
     */
    private Set<Object> extractTransactionsFromBlock(Block block) {
        Set<Object> txIds = new HashSet<>();
        
        try {
            // Try common methods first
            try {
                // Try getTransactions() method
                java.lang.reflect.Method method = block.getClass().getMethod("getTransactions");
                Object result = method.invoke(block);
                if (result instanceof Collection) {
                    for (Object tx : (Collection<?>) result) {
                        if (tx != null) {
                            txIds.add(getTxId(tx));
                        }
                    }
                }
            } catch (NoSuchMethodException e) {
                // Try alternative: iterate private fields looking for Tx collections
                for (Field field : block.getClass().getDeclaredFields()) {
                    if (Collection.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object fieldValue = field.get(block);
                        if (fieldValue instanceof Collection) {
                            for (Object item : (Collection<?>) fieldValue) {
                                // Check if item looks like a transaction
                                if (item != null && item.getClass().getSimpleName().contains("Tx")) {
                                    txIds.add(getTxId(item));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail - simulation doesn't halt on reflection errors
            // Log at debug level if logger configured
        }
        
        return txIds;
    }
    
    /**
     * Extract a unique ID from a transaction object
     * Uses hash code or attempts to call getId()/getHash()
     */
    private Object getTxId(Object tx) {
        try {
            // Try getHash() method first
            java.lang.reflect.Method hashMethod = tx.getClass().getMethod("getHash");
            return hashMethod.invoke(tx);
        } catch (Exception e1) {
            try {
                // Try hashCode() as fallback
                return tx.hashCode();
            } catch (Exception e2) {
                return tx.toString(); // Last resort
            }
        }
    }
    
    /**
     * Track transactions in a finalized block for double-spend detection
     * Call this when a block is reorged or orphaned
     * 
     * @param blockHeight The height of the forked block
     */
    public void recordForkedBlock(int blockHeight) {
        metrics.recordForkedBlock(blockHeight);
        blockTransactions.remove(blockHeight);
    }
    
    /**
     * Get the double-spend tracker for external usage
     */
    public DoubleSpendTracker getDoubleSpendTracker() {
        return doubleSpendTracker;
    }
    
    /**
     * Get tracked transactions by block height
     */
    public Map<Integer, Set<Object>> getBlockTransactions() {
        return blockTransactions;
    }

    @Override
    protected boolean csvOutputConditionFinalPerNode() {
        return false;
    }

    @Override
    protected String[] csvHeaderOutput() {
        return new String[]{
            "Time", 
            "NodeID", 
            "BlockHeight", 
            "BlockHashCode", 
            "BlockSize", 
            "BlockCreationTime", 
            "BlockCreator", 
            "BlockFinalizationTime",
            "TrafficUntilFinalization",
            "Tb_AvgFinalizationTime_s",
            "Cb_AvgTraffic_MB",
            "Bf_ForkRate_pct",
            "BFT_ByzantineTolerance_pct",
            "Pdv_DoubleSend_SuccessProbability_pct"
        };
    }

    @Override
    protected String[] csvEventOutput(Event event) {
        BlockFinalizationEvent finalizationEvent = (BlockFinalizationEvent) event;
        Node node = finalizationEvent.getNode();
        Block block = finalizationEvent.getBlock();

        return new String[]{
            Double.toString(this.scenario.getSimulator().getSimulationTime()),
            Integer.toString(node.nodeID),
            Integer.toString(block.getHeight()),
            Integer.toString(block.hashCode()),
            Integer.toString(block.getSize()),
            Double.toString(block.getCreationTime()),
            Integer.toString(block.getCreator().nodeID),
            Double.toString(this.scenario.getSimulator().getSimulationTime() - block.getCreationTime()),
            Long.toString(finalizationEvent.getTrafficUntilFinalization()),
            // Metric 1: Tb - Average block finalization time
            Double.toString(metrics.getAverageBlockFinalizationTime()),
            // Metric 2: Cb - Average traffic per block in MB
            Double.toString(metrics.getAverageTrafficPerBlock()),
            // Metric 3: Bf - Fork rate as percentage
            Double.toString(metrics.getForkRate()),
            // Metric 4: BFT - Byzantine fault tolerance percentage
            Double.toString(metrics.getByzantineFaultTolerance()),
            // Metric 5: Pdv - Double-spending success probability
            Double.toString(metrics.getDoubleSpendSuccessProbability())
        };
    }
    
    /**
     * Register a newly proposed block for fork detection
     * Call this when a block is created/proposed but not yet finalized
     * Enables detection of multiple blocks at same height (potential fork)
     * 
     * @param block The proposed block
     */
    public void recordProposedBlock(Block block) {
        if (block != null) {
            Object blockId = block.hashCode();
            Set<Object> blocksAtHeight = blocksByHeight.computeIfAbsent(block.getHeight(), k -> new HashSet<>());
            blocksAtHeight.add(blockId);
        }
    }}