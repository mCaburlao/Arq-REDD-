package jabs.scenario;

import jabs.log.EnhancedBlockFinalizationLogger;
import jabs.simulator.Simulator;
import jabs.simulator.event.BlockForkedEvent;
import jabs.network.networks.Network;
import jabs.ledgerdata.Block;
import jabs.network.node.nodes.Node;

import java.util.*;

/**
 * Utility to track and simulate block forks/reorgs for realistic Bf (fork rate) measurement.
 * 
 * In a real blockchain:
 * - Temporary forks occur when miners/validators create competing blocks at same height
 * - These are eventually resolved by the consensus protocol
 * - The fork rate Bf is the ratio of orphaned blocks to total blocks
 * 
 * This utility simulates forks based on network topology (multiple nodes proposing concurrently)
 * and generates BlockForkedEvent to be logged.
 */
public class ForkTracker {
    private final Simulator simulator;
    private final Network network;
    private final EnhancedBlockFinalizationLogger logger;
    
    // Track blocks by height to detect forks
    private final Map<Integer, List<Block>> blocksByHeight;
    private final Set<Block> orphanedBlocks;
    
    public ForkTracker(Simulator simulator, Network network, EnhancedBlockFinalizationLogger logger) {
        this.simulator = simulator;
        this.network = network;
        this.logger = logger;
        this.blocksByHeight = new HashMap<>();
        this.orphanedBlocks = new HashSet<>();
    }
    
    /**
     * Register a block as proposed/generated.
     * Detects if this creates a fork at this height.
     * 
     * @param block The block being proposed
     * @param creator The node that created this block
     * @return true if this creates a fork (multiple blocks at same height)
     */
    public boolean registerBlockProposal(Block block, Node creator) {
        int height = block.getHeight();
        blocksByHeight.computeIfAbsent(height, k -> new ArrayList<>()).add(block);
        
        List<Block> blocksAtHeight = blocksByHeight.get(height);
        if (blocksAtHeight.size() > 1) {
            // Fork detected: multiple blocks at same height
            // All but one will eventually be orphaned
            // Emit BlockForkedEvent for all but the newest block to inform loggers
            for (Block other : new ArrayList<>(blocksAtHeight)) {
                if (!other.equals(block)) {
                    if (!orphanedBlocks.contains(other)) {
                        orphanedBlocks.add(other);
                        try {
                            simulator.putEvent(new jabs.simulator.event.BlockForkedEvent(
                                    simulator.getSimulationTime(), creator, other, "proposal-fork"
                            ), 0);
                        } catch (Exception ignored) {}
                    }
                }
            }
            return true;
        }
        return false;
    }
    
    /**
     * Mark a block as orphaned when a reorg happens.
     * 
     * @param block The orphaned block
     * @param reason Why this block was orphaned (e.g., "reorg", "uncle")
     */
    public void recordOrphanedBlock(Block block, String reason) {
        if (block != null && !orphanedBlocks.contains(block)) {
            orphanedBlocks.add(block);
        }
    }
    
    /**
     * Get the empirical fork rate from tracked data
     * @return percentage of orphaned blocks
     */
    public double getEmpiricalForkRate() {
        int totalBlocks = blocksByHeight.values().stream()
            .mapToInt(List::size)
            .sum();
        if (totalBlocks == 0) return 0;
        return (orphanedBlocks.size() / (double) totalBlocks) * 100.0;
    }
    
    public int getOrphanedBlockCount() {
        return orphanedBlocks.size();
    }
    
    public int getTotalBlocksProposed() {
        return blocksByHeight.values().stream()
            .mapToInt(List::size)
            .sum();
    }
}
