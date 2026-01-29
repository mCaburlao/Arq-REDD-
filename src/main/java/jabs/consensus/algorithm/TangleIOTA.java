package jabs.consensus.algorithm;

import jabs.consensus.blockchain.LocalBlockDAG;
import jabs.consensus.config.TangleIOTAConsensusConfig;
import jabs.ledgerdata.tangle.TangleBlock;
import jabs.ledgerdata.tangle.TangleTx;

import java.util.HashMap;
import java.util.HashSet;
import jabs.simulator.event.BlockFinalizationEvent;

/**
 *
 */
public class TangleIOTA extends AbstractDAGBasedConsensus<TangleBlock, TangleTx> {
    protected final HashMap<TangleBlock, Double> blockAccWeights = new HashMap<>();

    /**
     * Creates a Tangle Consensus Algorithm
     *
     * @param localBlockDAG local block tree in the node's memory
     */
    public TangleIOTA(LocalBlockDAG<TangleBlock> localBlockDAG, TangleIOTAConsensusConfig tangleIOTAConsensusConfig) {
        super(localBlockDAG);
        this.newIncomingBlock(localBlockDAG.getGenesisBlock());
    }

    /** If a new block that is connected to the genesis block is received
     * this function will be called. Thus, any input block is both totally
     * new and 100% connected to genesis block with all ancestors known to
     * the node. This function will update the block acc accumulative
     * weights in the consensus state of the node.
     *
     * @param block the newly received block
     */
    @Override
    public void newIncomingBlock(TangleBlock block) {
        blockAccWeights.put(block, block.getWeight());
        HashSet<TangleBlock> ancestors = localBlockDAG.getAllAncestors(block);
        for (TangleBlock ancestor:ancestors) {
            blockAccWeights.put(ancestor, blockAccWeights.get(ancestor) + block.getWeight());
        }
    }

    /**
     * Stub: confirm a block (emit finalization event and record acceptance).
     * Protocol-specific confirmation logic should call this when a block
     * reaches the required confidence/weight threshold.
     */
    public void confirmBlock(TangleBlock block) {
        if (block == null) return;
        if (this.peerDLTNode != null && this.peerDLTNode.getSimulator() != null) {
            long traffic = 0L;
            this.peerDLTNode.getSimulator().putEvent(
                    new BlockFinalizationEvent(
                            this.peerDLTNode.getSimulator().getSimulationTime(),
                            this.peerDLTNode,
                            block,
                            traffic
                    ),
                    0
            );
        }
        // record for BFT metrics
        recordBlockAcceptance(block);
    }

    @Override
    protected int getBlockProposer(TangleBlock block) {
        if (block == null || block.getCreator() == null) return 0;
        return block.getCreator().nodeID;
    }
}
