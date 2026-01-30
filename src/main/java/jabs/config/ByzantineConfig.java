package jabs.config;

import java.util.*;

/**
 * Configuration for Byzantine fault injection in consensus simulations.
 * 
 * Enables testing of protocol resilience to malicious validators.
 * Arq-REDD+ voting-based consensus is designed to tolerate up to f < n/3 Byzantine nodes.
 */
public class ByzantineConfig {
    
    private Set<Integer> byzantineValidatorIds;
    private double byzantinePercentage;
    private int totalValidators;
    private String attackType;
    private long randomSeed;
    
    /**
     * Attack types that Byzantine validators can execute:
     * - WITHHOLD: Refuse to vote on proposals
     * - EQUIVOCATION: Vote for conflicting blocks
     * - MINORITY_FORK: Try to create alternative chain with minority
     * - SILENT: Don't participate (network partitioned)
     * - DOUBLE_SIGN: Sign multiple conflicting blocks
     */
    public enum AttackType {
        WITHHOLD,
        EQUIVOCATION,
        MINORITY_FORK,
        SILENT,
        DOUBLE_SIGN,
        NONE
    }
    
    public ByzantineConfig(int totalValidators, double byzantinePercentage, 
                          AttackType attackType, long randomSeed) {
        this.totalValidators = totalValidators;
        this.byzantinePercentage = byzantinePercentage;
        this.attackType = attackType.name();
        this.randomSeed = randomSeed;
        this.byzantineValidatorIds = new HashSet<>();
        
        // Select random validators to be Byzantine
        selectByzantineValidators();
    }
    
    /**
     * Alternative constructor with String attack type
     */
    public ByzantineConfig(int totalValidators, double byzantinePercentage, 
                          String attackType, long randomSeed) {
        this.totalValidators = totalValidators;
        this.byzantinePercentage = byzantinePercentage;
        this.attackType = attackType;
        this.randomSeed = randomSeed;
        this.byzantineValidatorIds = new HashSet<>();
        
        selectByzantineValidators();
    }

    /**
     * Construct a ByzantineConfig with an explicit set of global node IDs.
     * @param totalNodes total number of nodes in the network (for reporting)
     * @param byzantineIds set of global node IDs which are Byzantine
     * @param attackType attack type string
     * @param randomSeed random seed used (kept for reproducibility)
     */
    public ByzantineConfig(int totalNodes, Set<Integer> byzantineIds,
                           String attackType, long randomSeed) {
        this.totalValidators = totalNodes;
        this.byzantineValidatorIds = new HashSet<>(byzantineIds == null ? Collections.emptySet() : byzantineIds);
        this.byzantinePercentage = (this.byzantineValidatorIds.size() * 100.0) / (totalNodes == 0 ? 1 : totalNodes);
        this.attackType = attackType;
        this.randomSeed = randomSeed;
    }
    
    /**
     * Randomly select Byzantine validators based on percentage
     */
    private void selectByzantineValidators() {
        int byzantineCount = (int) Math.ceil(totalValidators * (byzantinePercentage / 100.0));
        Random random = new Random(randomSeed);
        
        while (byzantineValidatorIds.size() < byzantineCount) {
            int validatorId = random.nextInt(totalValidators);
            byzantineValidatorIds.add(validatorId);
        }
    }
    
    /**
     * Check if a validator ID is Byzantine
     */
    public boolean isByzantine(int validatorId) {
        return byzantineValidatorIds.contains(validatorId);
    }
    
    // ===== GETTERS =====
    
    public Set<Integer> getByzantineValidatorIds() {
        return Collections.unmodifiableSet(byzantineValidatorIds);
    }
    
    public int getByzantineCount() {
        return byzantineValidatorIds.size();
    }
    
    public double getByzantinePercentage() {
        return byzantinePercentage;
    }
    
    public int getTotalValidators() {
        return totalValidators;
    }
    
    public String getAttackType() {
        return attackType;
    }
    
    public long getRandomSeed() {
        return randomSeed;
    }
    
    @Override
    public String toString() {
        return String.format("ByzantineConfig{validators=%d, byzantine=%.1f%% (%d nodes), " +
                           "attack=%s, seed=%d, threshold=%s, safetyMargin=%.2f%%}",
            totalValidators, byzantinePercentage, getByzantineCount(), attackType, randomSeed,
            "N/A", 0.0);
    }
}
