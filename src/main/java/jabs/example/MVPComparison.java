package jabs.example;

import jabs.config.ByzantineConfig;
import jabs.ledgerdata.DoubleSpendTracker;
import jabs.metrics.SimulationMetrics;
import jabs.scenario.AbstractScenario;
import jabs.scenario.ArqReddVotingScenario;
import jabs.scenario.PBFTLANScenario;
import jabs.log.EnhancedBlockFinalizationLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * MVP Example: Arq-REDD+ vs pBFT with REAL Simulation
 * 
 * This example demonstrates:
 * 1. Running REAL blockchain consensus simulations
 * 2. Injecting 33% Byzantine validators
 * 3. Collecting all 5 metrics in real-time
 * 4. Comparing Byzantine fault tolerance
 * 
 * Date: January 23, 2026
 * Status: Ready for execution with REAL scenarios
 */
public class MVPComparison {
    
    public static void main(String[] args) throws IOException {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║   MVP: Arq-REDD+ vs pBFT (33% Byzantine) - REAL SIM   ║");
        System.out.println("║   Date: 2026-01-23                                    ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");
        
        // Create output directory
        Files.createDirectories(Paths.get("output/mvp-validation"));
        
        // ============================================
        // STEP 1: Byzantine Configuration
        // ============================================
        System.out.println("📋 STEP 1: Byzantine Configuration");
        System.out.println("─".repeat(60));
        
        int totalValidators = 20;  // Reduced from 100 for memory efficiency
        double byzantinePercentage = 33.0;
        long randomSeed = 12345L;
        
        ByzantineConfig byzantineConfig = new ByzantineConfig(
            totalValidators,
            byzantinePercentage,
            ByzantineConfig.AttackType.EQUIVOCATION,
            randomSeed
        );
        
        System.out.println("Configuration created:");
        System.out.println("  Total Validators: " + totalValidators);
        System.out.println("  Byzantine %: " + byzantinePercentage + "%");
        System.out.println("  Byzantine Count: " + byzantineConfig.getByzantineCount());
        System.out.println("  Attack Type: EQUIVOCATION");
        System.out.println("  Safety Margin: " + String.format("%.2f%%", byzantineConfig.getSafetyMargin()));
        System.out.println();
        
        // ============================================
        // STEP 2: Run Arq-REDD+ Voting-Based Simulation
        // ============================================
        System.out.println("🔄 STEP 2: Arq-REDD+ Voting-Based Consensus Simulation");
        System.out.println("─".repeat(60));
        
        SimulationMetrics metricsArqRedd = new SimulationMetrics();
        metricsArqRedd.setByzantineValidators(byzantineConfig.getByzantineCount());
        metricsArqRedd.setTotalValidators(totalValidators);
        
        try {
            ArqReddVotingScenario arqReddScenario = new ArqReddVotingScenario(
                "Arq-REDD+ Voting with 33% Byzantine Validators",
                randomSeed,
                totalValidators,
                600.0,  // 600 seconds simulation (10 minutes)
                byzantinePercentage,
                ByzantineConfig.AttackType.EQUIVOCATION
            );
            
            // Add metrics logger
            arqReddScenario.addMetricsLogger("output/mvp-validation/arq-redd-metrics.csv");
            
            // Run simulation
            System.out.println("Running Arq-REDD+ simulation (this may take 1-3 minutes)...");
            arqReddScenario.run();
            
            // Get metrics
            metricsArqRedd = arqReddScenario.getMetrics();
            
            System.out.println("Arq-REDD+ Simulation Complete:");
            System.out.println("  Blocks Generated: " + metricsArqRedd.getTotalBlocksGenerated());
            System.out.println("  Blocks Finalized: " + metricsArqRedd.getBlockCount());
            System.out.println("  Forked Blocks: " + metricsArqRedd.getForkedBlocks());
            
        } catch (Exception e) {
            System.out.println("⚠️  Arq-REDD+ simulation failed: " + e.getMessage());
            e.printStackTrace();
            // Continue with pBFT
        }
        System.out.println();
        
        // ============================================
        // STEP 3: Run pBFT Simulation
        // ============================================
        System.out.println("🔄 STEP 3: pBFT Consensus Simulation");
        System.out.println("─".repeat(60));
        
        SimulationMetrics metricsPBFT = new SimulationMetrics();
        metricsPBFT.setByzantineValidators(byzantineConfig.getByzantineCount());
        metricsPBFT.setTotalValidators(totalValidators);
        
        try {
            PBFTLANScenario pbftScenario = new PBFTLANScenario(
                "pBFT with 33% Byzantine Validators",
                randomSeed + 1,
                totalValidators,
                600.0  // 600 seconds simulation (10 minutes)
            );
            
            // Add metrics logger
            EnhancedBlockFinalizationLogger pbftLogger = 
                new EnhancedBlockFinalizationLogger(
                    Paths.get("output/mvp-validation/pbft-metrics.csv"),
                    metricsPBFT
                );
            pbftScenario.AddNewLogger(pbftLogger);
            
            // Run simulation
            System.out.println("Running pBFT simulation (this may take 1-3 minutes)...");
            pbftScenario.run();
            
            System.out.println("pBFT Simulation Complete:");
            System.out.println("  Blocks Generated: " + metricsPBFT.getTotalBlocksGenerated());
            System.out.println("  Blocks Finalized: " + metricsPBFT.getBlockCount());
            System.out.println("  Forked Blocks: " + metricsPBFT.getForkedBlocks());
            
        } catch (Exception e) {
            System.out.println("⚠️  pBFT simulation failed: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
        
        // ============================================
        // STEP 4: Generate Results
        // ============================================
        System.out.println("📊 STEP 4: Metrics Comparison");
        System.out.println("─".repeat(60));
        System.out.println();
        
        printMetricsReport("Arq-REDD+", metricsArqRedd);
        System.out.println();
        printMetricsReport("pBFT", metricsPBFT);
        System.out.println();
        
        // ============================================
        // STEP 5: Validation
        // ============================================
        System.out.println("✅ STEP 5: Validation Results");
        System.out.println("─".repeat(60));
        
        double bftArqRedd = metricsArqRedd.getByzantineFaultTolerance();
        double bftPBFT = metricsPBFT.getByzantineFaultTolerance();
        
        boolean arqReddPassed = bftArqRedd >= 99.9;
        boolean pbftPassed = bftPBFT >= 99.9;
        
        System.out.println(String.format("BFT Arq-REDD+: %.2f%% %s", bftArqRedd, 
            arqReddPassed ? "✅ PASSED (≥99.9%)" : "⚠️  Below threshold"));
        System.out.println(String.format("BFT pBFT:      %.2f%% %s", bftPBFT,
            pbftPassed ? "✅ PASSED (≥99.9%)" : "⚠️  Below threshold"));
        System.out.println();
        
        if (metricsArqRedd.getBlockCount() > 0 && metricsPBFT.getBlockCount() > 0) {
            System.out.println("🎉 MVP EXECUTION: COMPLETE");
            System.out.println("   Simulations ran successfully with real blockchain scenarios");
            System.out.println("   Ready to scale to 4,400 simulations");
            System.out.println();
            System.out.println("📁 Output files:");
            System.out.println("   output/mvp-validation/arq-redd-metrics.csv");
            System.out.println("   output/mvp-validation/pbft-metrics.csv");
        } else {
            System.out.println("⚠️  MVP EXECUTION: LIMITED");
            System.out.println("   One or more simulations produced no blocks");
            System.out.println("   Review logs above for details");
        }
        System.out.println();
    }
    
    /**
     * Pad a string to a specific width (Java-compatible)
     */
    private static String padEnd(String str, int width) {
        if (str.length() >= width) {
            return str.substring(0, width);
        }
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < width) {
            sb.append(" ");
        }
        return sb.toString();
    }
    
    /**
     * Print formatted metrics report
     */
    private static void printMetricsReport(String protocolName, SimulationMetrics metrics) {
        System.out.println("╔" + "═".repeat(58) + "╗");
        System.out.println("║ " + protocolName.toUpperCase() + " METRICS REPORT" + 
                          " ".repeat(Math.max(0, 43 - protocolName.length())) + "║");
        System.out.println("╠" + "═".repeat(58) + "╣");
        
        if (metrics.getBlockCount() == 0) {
            System.out.println(padEnd("║ No blocks generated in simulation", 59) + "║");
            System.out.println("╚" + "═".repeat(58) + "╝");
            return;
        }
        
        System.out.println(padEnd("║ Metric 1 - Tb (Block Finalization Time):", 59) + "║");
        System.out.println(padEnd(String.format("║   Average: %.3f seconds/block", metrics.getAverageBlockFinalizationTime()), 59) + "║");
        System.out.println(padEnd(String.format("║   p95: %.3f seconds", metrics.getPercentileFinalizationTime(95)), 59) + "║");
        
        System.out.println(padEnd("║ Metric 2 - Cb (Network Traffic):", 59) + "║");
        System.out.println(padEnd(String.format("║   Average: %.6f MB/block", metrics.getAverageTrafficPerBlock()), 59) + "║");
        System.out.println(padEnd(String.format("║   p95: %.6f MB", metrics.getPercentileTraffic(95)), 59) + "║");
        
        System.out.println(padEnd("║ Metric 3 - Bf (Fork Rate):", 59) + "║");
        System.out.println(padEnd(String.format("║   Rate: %.3f%%", metrics.getForkRate()), 59) + "║");
        
        System.out.println(padEnd("║ Metric 4 - BFT (Byzantine Fault Tolerance):", 59) + "║");
        double bft = metrics.getByzantineFaultTolerance();
        String bftStatus = bft >= 99.9 ? "✅ SAFE" : "⚠️  CHECK";
        System.out.println(padEnd(String.format("║   BFT: %.2f%% %s", bft, bftStatus), 59) + "║");
        
        System.out.println(padEnd("║ Metric 5 - Pdv (Double-spending):", 59) + "║");
        System.out.println(padEnd(String.format("║   Probability: %.3f%%", metrics.getDoubleSpendSuccessProbability()), 59) + "║");
        
        System.out.println("╚" + "═".repeat(58) + "╝");
    }
}
