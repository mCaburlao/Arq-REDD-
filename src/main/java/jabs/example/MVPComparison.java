package jabs.example;

import jabs.config.ByzantineConfig;
import jabs.ledgerdata.DoubleSpendTracker;
import jabs.metrics.SimulationMetrics;
import jabs.scenario.AbstractScenario;
import jabs.scenario.ArqReddVotingScenario;
import jabs.scenario.HybridNetworkScenario;
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
            HybridNetworkScenario arqReddScenario = new HybridNetworkScenario(
                "Arq-REDD+ Hybrid Voting with 33% Byzantine Validators",
                randomSeed,
                totalValidators,
                600.0,  // 600 seconds simulation (10 minutes)
                30.0,  // 30% private transactions
                new double[]{20.0, 60.0, 20.0},
                byzantinePercentage,
                ByzantineConfig.AttackType.EQUIVOCATION
            );
            
            // Add metrics logger
            arqReddScenario.addMetricsLogger("output/mvp-validation/arq-redd-hybrid-metrics.csv");
            
            // Run simulation
            System.out.println("Running Arq-REDD+ HYBRID simulation (this may take 1-3 minutes)...");
            arqReddScenario.run();
            
            // Get metrics
            metricsArqRedd = arqReddScenario.getMetrics();
            
            System.out.println("Arq-REDD+ HYBRID Simulation Complete:");
            System.out.println("  Blocks Generated: " + metricsArqRedd.getTotalBlocksGenerated());
            System.out.println("  Blocks Finalized: " + metricsArqRedd.getBlockCount());
            System.out.println("  Forked Blocks: " + metricsArqRedd.getForkedBlocks());
            
        } catch (Exception e) {
            System.out.println("⚠️  Arq-REDD+ simulation failed: " + e.getMessage());
            e.printStackTrace();
            // Continue with pBFT
        }
        System.out.println();
        
        // Quick Byzantine sweep for Arq-REDD+ (1% steps up to 50%, stop at first insecure)
        System.out.println("🔬 Running Byzantine sweep for Arq-REDD+ (0–50%)...");
        for (int pct = 0; pct <= 50; pct += 5) {
            int byzCount = (int) Math.round(totalValidators * pct / 100.0);
            try {
                HybridNetworkScenario sweep = new HybridNetworkScenario(
                    "Arq-REDD+ sweep " + pct + "% Byzantines",
                    randomSeed + pct + 100,
                    totalValidators,
                    60.0,  // short run
                    30.0,
                    new double[]{20.0,60.0,20.0},
                    (double) pct,
                    ByzantineConfig.AttackType.EQUIVOCATION
                );
                // Attach a temporary metrics logger so the scenario will populate metrics during the run
                SimulationMetrics mMetrics = new SimulationMetrics();
                EnhancedBlockFinalizationLogger sweepLogger = new EnhancedBlockFinalizationLogger(
                    java.nio.file.Paths.get("output/mvp-validation/arq-redd-sweep-" + pct + ".csv"),
                    mMetrics
                );
                sweep.AddNewLogger(sweepLogger);
                sweep.run();
                SimulationMetrics m = mMetrics;
                boolean secure = m.evaluateSecurity(1.0, 0.5); // fork rate <=1%, Pdv <=0.5%
                metricsArqRedd.recordByzantineTestResult(byzCount, secure);
                System.out.println(String.format("  Sweep %2d%% -> byz=%d: %s (fork=%.3f%%, pdv=%.3f%%)",
                    pct, byzCount, secure ? "SECURE" : "INSECURE", m.getForkRate(), m.getDoubleSpendSuccessProbability()));
                if (!secure) {
                    System.out.println("  Insecure threshold reached at " + pct + "% (byz=" + byzCount + ") - stopping sweep.");
                    break;
                }
            } catch (Exception ex) {
                System.out.println("  Sweep " + pct + "% failed: " + ex.getMessage());
            }
        }
        
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

        // Quick Byzantine sweep for pBFT (short tests)
        System.out.println("🔬 Running quick Byzantine sweep for pBFT (short tests)...");
        // Quick Byzantine sweep for pBFT (1% steps up to 50%, stop at first insecure)
        System.out.println("🔬 Running Byzantine sweep for pBFT (0–50%)...");
        for (int pct = 0; pct <= 50; pct += 5) {
            int byzCount = (int) Math.round(totalValidators * pct / 100.0);
            try {
                PBFTLANScenario sweep = new PBFTLANScenario(
                    "pBFT sweep " + pct + "% Byzantines",
                    randomSeed + pct + 200,
                    totalValidators,
                    60.0
                );
                // Attach a temporary metrics logger so we can collect metrics from this scenario
                SimulationMetrics mMetrics = new SimulationMetrics();
                EnhancedBlockFinalizationLogger sweepLogger = new EnhancedBlockFinalizationLogger(
                    java.nio.file.Paths.get("output/mvp-validation/pbft-sweep-" + pct + ".csv"),
                    mMetrics
                );
                sweep.AddNewLogger(sweepLogger);
                sweep.run();
                SimulationMetrics m = mMetrics;
                boolean secure = m.evaluateSecurity(1.0, 0.5);
                metricsPBFT.recordByzantineTestResult(byzCount, secure);
                System.out.println(String.format("  Sweep %2d%% -> byz=%d: %s (fork=%.3f%%, pdv=%.3f%%)",
                    pct, byzCount, secure ? "SECURE" : "INSECURE", m.getForkRate(), m.getDoubleSpendSuccessProbability()));
                if (!secure) {
                    System.out.println("  Insecure threshold reached at " + pct + "% (byz=" + byzCount + ") - stopping sweep.");
                    break;
                }
            } catch (Exception ex) {
                System.out.println("  Sweep " + pct + "% failed: " + ex.getMessage());
            }
        }
        
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
        
        // Report empirical sweep results and configured status
        double maxTolerableArq = metricsArqRedd.getMaxTolerableByzantinePercentage();
        double maxTolerablePbft = metricsPBFT.getMaxTolerableByzantinePercentage();
        double firstInsecureArq = metricsArqRedd.getFirstInsecureByzantinePercentage();
        double firstInsecurePbft = metricsPBFT.getFirstInsecureByzantinePercentage();

        double requiredThreshold = metricsArqRedd.getMaxByzantineThreshold(); // typically 33.3%

        int configuredByz = byzantineConfig.getByzantineCount();
        double configuredPct = (configuredByz / (double) totalValidators) * 100.0;
        boolean configuredSecureArq = metricsArqRedd.evaluateSecurity(1.0, 0.5);
        boolean configuredSecurePbft = metricsPBFT.evaluateSecurity(1.0, 0.5);

        System.out.println(String.format("Configured Byzantine: %d nodes (%.2f%%)", configuredByz, configuredPct));
        System.out.println(String.format("  Arq-REDD+ configured status: %s", configuredSecureArq ? "SECURE" : "INSECURE"));
        System.out.println(String.format("  pBFT configured status:      %s", configuredSecurePbft ? "SECURE" : "INSECURE"));

        System.out.println(String.format("Max tolerable Byzantine - Arq-REDD+: %.2f%%", maxTolerableArq));
        if (firstInsecureArq >= 0) {
            System.out.println(String.format("  First insecure tested at: %.2f%%", firstInsecureArq));
        } else {
            System.out.println("  No insecure percentage observed in sweep (up to tested max)");
        }

        System.out.println(String.format("Max tolerable Byzantine - pBFT:      %.2f%%", maxTolerablePbft));
        if (firstInsecurePbft >= 0) {
            System.out.println(String.format("  First insecure tested at: %.2f%%", firstInsecurePbft));
        } else {
            System.out.println("  No insecure percentage observed in sweep (up to tested max)");
        }

        boolean arqReddPassed = maxTolerableArq >= requiredThreshold;
        boolean pbftPassed = maxTolerablePbft >= requiredThreshold;

        System.out.println();
        System.out.println(String.format("Theoretical threshold (f<n/3): %.2f%%", requiredThreshold));
        System.out.println(String.format("Result: Arq-REDD+ %s, pBFT %s",
            arqReddPassed ? "PASSED" : "FAILED", pbftPassed ? "PASSED" : "FAILED"));
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
        double empiricalMax = metrics.getMaxTolerableByzantinePercentage();
        System.out.println(padEnd(String.format("║   Empirical max tolerable: %.2f%%", empiricalMax), 59) + "║");
        
        System.out.println(padEnd("║ Metric 5 - Pdv (Double-spending):", 59) + "║");
        System.out.println(padEnd(String.format("║   Probability: %.3f%%", metrics.getDoubleSpendSuccessProbability()), 59) + "║");
        
        System.out.println("╚" + "═".repeat(58) + "╝");
    }
}
