package jabs.scenario;

import jabs.config.ByzantineConfig;
import jabs.consensus.config.PBFTConsensusConfig;
import jabs.ledgerdata.BlockFactory;
import jabs.ledgerdata.pbft.PBFTPrePrepareVote;
import jabs.network.message.VoteMessage;
import jabs.network.networks.pbft.PBFTLocalLANNetwork;
import jabs.network.node.nodes.Node;
import jabs.network.node.nodes.pbft.PBFTNode;
import jabs.network.node.nodes.PeerBlockchainNode;
import jabs.log.EnhancedBlockFinalizationLogger;
import jabs.scenario.ForkTracker;
import jabs.metrics.SimulationMetrics;

import java.io.IOException;
import java.nio.file.Paths;

import static jabs.network.node.nodes.pbft.PBFTNode.PBFT_GENESIS_BLOCK;

/**
 * Scenario for Arq-REDD+ Voting-Based Consensus with Byzantine Validators
 * 
 * This scenario creates a network of voting-based consensus nodes (using pBFT
 * as base)
 * and optionally injects Byzantine validators for fault tolerance testing.
 * 
 * Used for MVP validation of 3 new metrics:
 * 1. Tb - Block Finalization Time
 * 2. Cb - Network Traffic
 * 3. Bf - Fork Rate
 * 4. BFT - Byzantine Fault Tolerance (NEW)
 * 5. Pdv - Double-spending Probability (NEW)
 */
public class ArqReddVotingScenario extends AbstractScenario {
    protected int numNodes;
    protected double simulationStopTime;
    protected ByzantineConfig byzantineConfig;
    protected SimulationMetrics metrics;

    /**
     * Create Arq-REDD+ voting scenario WITHOUT Byzantine validators
     * 
     * @param name               Scenario name
     * @param seed               Random seed
     * @param numNodes           Number of consensus nodes
     * @param simulationStopTime Stop condition (seconds)
     */
    public ArqReddVotingScenario(String name, long seed, int numNodes, double simulationStopTime) {
        super(name, seed);
        this.numNodes = numNodes;
        this.simulationStopTime = simulationStopTime;
        this.byzantineConfig = null;
        this.metrics = new SimulationMetrics();
    }

    /**
     * Create Arq-REDD+ voting scenario WITH Byzantine validators
     * 
     * @param name                Scenario name
     * @param seed                Random seed
     * @param numNodes            Number of consensus nodes
     * @param simulationStopTime  Stop condition (seconds)
     * @param byzantinePercentage Percentage of Byzantine nodes (0-100)
     * @param attackType          Type of Byzantine attack
     */
    public ArqReddVotingScenario(String name, long seed, int numNodes, double simulationStopTime,
            double byzantinePercentage, ByzantineConfig.AttackType attackType) {
        super(name, seed);
        this.numNodes = numNodes;
        this.simulationStopTime = simulationStopTime;

        // Create Byzantine configuration
        this.byzantineConfig = new ByzantineConfig(numNodes, byzantinePercentage, attackType, seed);

        // Initialize metrics with Byzantine info
        this.metrics = new SimulationMetrics();
        this.metrics.setByzantineValidators(byzantineConfig.getByzantineCount());
        this.metrics.setTotalValidators(numNodes);
    }

    @Override
    public void createNetwork() {
        // Create pBFT-based network (voting consensus substrate)
        network = new PBFTLocalLANNetwork(randomnessEngine);
        network.populateNetwork(this.simulator, this.numNodes, new PBFTConsensusConfig());

        // If Byzantine config exists, mark nodes as Byzantine
        if (byzantineConfig != null) {
            for (int i = 0; i < network.getAllNodes().size(); i++) {
                Node node = (Node) network.getAllNodes().get(i);
                if (byzantineConfig.isByzantine(i)) {
                    // Attach Byzantine configuration to node's consensus algorithm when possible
                    if (node instanceof PeerBlockchainNode) {
                        try {
                            PeerBlockchainNode<?, ?> pbn = (PeerBlockchainNode<?, ?>) node;
                            pbn.getConsensusAlgorithm().setByzantineConfig(byzantineConfig);
                        } catch (Exception ignored) {}
                    }

                    // Apply immediate network-level effects for certain attack types
                    String attack = byzantineConfig.getAttackType();
                    if ("SILENT".equalsIgnoreCase(attack)) {
                        // Silent attackers: take node offline (no packet delivery)
                        try { node.crash(); } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    @Override
    protected void insertInitialEvents() {
        // Start continuous consensus rounds via the network with stop time
        ((PBFTLocalLANNetwork) network).startConsensusRound(simulator, this.simulationStopTime);
    }

    @Override
    public boolean simulationStopCondition() {
        return (simulator.getSimulationTime() > this.simulationStopTime);
    }

    /**
     * Add metrics logger to scenario
     */
    public void addMetricsLogger(String outputPath) throws IOException {
        EnhancedBlockFinalizationLogger logger = new EnhancedBlockFinalizationLogger(
                Paths.get(outputPath),
                this.metrics);
        // Create and attach a ForkTracker so the scenario-level tracker is used
        ForkTracker forkTracker = new ForkTracker(this.simulator, this.network, logger);
        logger.setForkTracker(forkTracker);
        metrics.setForkTracker(forkTracker);
        this.AddNewLogger(logger);
    }

    /**
     * Add metrics logger to scenario
     */
    public void addMetricsLogger(String outputPath, SimulationMetrics metrics) throws IOException {
        EnhancedBlockFinalizationLogger logger = new EnhancedBlockFinalizationLogger(
                Paths.get(outputPath),
                metrics);
        // Create and attach a ForkTracker so the scenario-level tracker is used
        ForkTracker forkTracker = new ForkTracker(this.simulator, this.network, logger);
        logger.setForkTracker(forkTracker);
        metrics.setForkTracker(forkTracker);
        this.metrics = metrics;
        this.AddNewLogger(logger);
    }

    /**
     * Get collected metrics
     */
    public SimulationMetrics getMetrics() {
        return this.metrics;
    }

    /**
     * Get Byzantine configuration
     */
    public ByzantineConfig getByzantineConfig() {
        return this.byzantineConfig;
    }
}
