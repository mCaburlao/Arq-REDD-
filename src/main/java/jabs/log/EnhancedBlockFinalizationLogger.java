package jabs.log;

import jabs.ledgerdata.Block;
import jabs.ledgerdata.Tx;
import jabs.ledgerdata.DoubleSpendTracker;
import jabs.network.node.nodes.Node;
import jabs.simulator.event.AbstractLogEvent;
import jabs.simulator.event.Event;
import jabs.simulator.event.BlockFinalizationEvent;
import jabs.metrics.SimulationMetrics;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;

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
    
    /**
     * Creates an enhanced CSV logger with metrics tracking
     * @param writer this is output CSV of the logger
     * @param metrics the metrics collector
     */
    public EnhancedBlockFinalizationLogger(Writer writer, SimulationMetrics metrics) {
        super(writer);
        this.metrics = metrics;
        this.doubleSpendTracker = new DoubleSpendTracker();
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
            
            // Collect Metric 1: Block finalization time (Tb)
            double finalizationTime = this.scenario.getSimulator().getSimulationTime() - block.getCreationTime();
            metrics.recordBlockFinalizationTime(finalizationTime);
            
            // Collect Metric 2: Network traffic for finalization (Cb)
            long traffic = finalizationEvent.getTrafficUntilFinalization();
            metrics.recordBlockTraffic(traffic);
            
            // Collect Metric 3: Fork rate (Bf) - tracked separately when blocks are discarded
            metrics.recordBlockGenerated();
            
            // Collect Metric 5: Double-spending (Pdv)
            // Track all transactions in this finalized block
            if (block instanceof Block) {
                // Try to get transactions from block (implementation specific)
                // This is a placeholder - actual implementation depends on Block interface
                trackTransactionsInBlock(block);
            }
            
            return true;
        }
        return false;
    }
    
    /**
     * Track transactions in a finalized block for double-spend detection
     * @param block The finalized block
     */
    private void trackTransactionsInBlock(Block block) {
        // This method should be called to track double-spending
        // Actual transaction extraction depends on Block implementation
        // Placeholder implementation for now
    }
    
    /**
     * Record a detected fork (block not in canonical chain)
     * Call this when a block is reorged or orphaned
     * 
     * @param blockHeight The height of the forked block
     */
    public void recordForkedBlock(int blockHeight) {
        metrics.recordForkedBlock(blockHeight);
    }
    
    /**
     * Get the double-spend tracker for external usage
     */
    public DoubleSpendTracker getDoubleSpendTracker() {
        return doubleSpendTracker;
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
}
