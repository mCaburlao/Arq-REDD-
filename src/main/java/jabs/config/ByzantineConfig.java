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
        DOUBLE_SIGN
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
    
    /**
     * Check if Byzantine threshold (f < n/3) is exceeded
     * For voting-based consensus, safety requires: Byzantine < n/3
     */
    public boolean isThresholdExceeded() {
        return byzantinePercentage > (100.0 / 3.0); // 33.33%
    }
    
    /**
     * Get safety margin: percentage points below n/3
     * Example: if Byzantine = 30%, margin = 3.33%
     */
    public double getSafetyMargin() {
        double threshold = 100.0 / 3.0;
        return Math.max(0, threshold - byzantinePercentage);
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
            isThresholdExceeded() ? "EXCEEDED" : "SAFE", getSafetyMargin());
    }
}
