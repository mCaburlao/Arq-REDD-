package jabs.simulator.event;

import jabs.ledgerdata.Block;
import jabs.network.node.nodes.Node;

/**
 * Event fired when a block becomes orphaned/forked (removed from canonical chain).
 * This is used to track fork rate metric (Bf) for consensus algorithm analysis.
 */
public class BlockForkedEvent extends AbstractLogEvent {
    private final Node node;
    private final Block block;
    private final String reason;  // e.g., "reorg", "chain-switch", "uncle"

    public BlockForkedEvent(double time, Node node, Block block, String reason) {
        super(time);
        this.node = node;
        this.block = block;
        this.reason = reason;
    }

    public Node getNode() {
        return node;
    }

    public Block getBlock() {
        return block;
    }

    public String getReason() {
        return reason;
    }
}
