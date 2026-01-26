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
        System.out.println("║   MVP: Arq-REDD+ vs pBFT - Parametrized Harness        ║");
        System.out.println("║   Date: 2026-01-23                                     ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");

        // CLI args (defaults keep smoke-run fast; use flags to scale):
        // --validators=20,100,200 --byzantine=0,33 --duration=600 --sweep-step=5 --sweep-max=50
        // --seed=12345 --attack=EQUIVOCATION --output=output/mvp-validation
        java.util.Map<String, String> cli = parseArgs(args);
        java.util.List<Integer> validatorsList = parseIntList(cli.getOrDefault("validators", "20"));
        java.util.List<Double> byzList = parseDoubleList(cli.getOrDefault("byzantine", "33"));
        double duration = Double.parseDouble(cli.getOrDefault("duration", "600"));
        double sweepDuration = Double.parseDouble(cli.getOrDefault("sweep-duration", "60"));
        int sweepStep = Integer.parseInt(cli.getOrDefault("sweep-step", "5"));
        int sweepMax = Integer.parseInt(cli.getOrDefault("sweep-max", "50"));
        long baseSeed = Long.parseLong(cli.getOrDefault("seed", "12345"));
        String attackStr = cli.getOrDefault("attack", "EQUIVOCATION");
        ByzantineConfig.AttackType attack = ByzantineConfig.AttackType.valueOf(attackStr.toUpperCase());
        String outputRoot = cli.getOrDefault("output", "output/mvp-validation");
        Files.createDirectories(Paths.get(outputRoot));

        System.out.println("Args: validators=" + validatorsList + ", byzantine=%" + byzList +
            ", duration=" + duration + "s, sweep=" + sweepStep + ".." + sweepMax + " step, seed=" + baseSeed +
            ", attack=" + attack + ", out=" + outputRoot);

        for (int validators : validatorsList) {
            for (double byzPct : byzList) {
                long runSeed = baseSeed + validators * 1000L + Math.round(byzPct * 10);
                String scenarioLabel = validators + "v-" + (int) byzPct + "pct";
                System.out.println("\n=== RUN " + scenarioLabel + " ===");

                SimulationMetrics arqMetrics = runArqREDDScenario(validators, byzPct, duration, runSeed, attack,
                    outputRoot + "/arq-redd-" + scenarioLabel + ".csv");
                //sweepArqREDD(arqMetrics, validators, sweepDuration, sweepStep, sweepMax, runSeed, attack, outputRoot);

                printMetricsReport("Arq-REDD+ (" + scenarioLabel + ")", arqMetrics);
            }
        }

        System.out.println("\nDone. Check per-run CSVs under " + outputRoot);
    }

    private static SimulationMetrics runArqREDDScenario(int totalValidators, double byzantinePercentage, double duration,
                                                       long seed, ByzantineConfig.AttackType attack, String outputPath) {
        SimulationMetrics metrics = new SimulationMetrics();
        metrics.setTotalValidators(totalValidators);
        metrics.setByzantineValidators((int) Math.round(totalValidators * byzantinePercentage / 100.0));

        try {
            HybridNetworkScenario scenario = new HybridNetworkScenario(
                "Arq-REDD+ Hybrid " + byzantinePercentage + "%",
                seed,
                totalValidators,
                duration,
                30.0,
                new double[]{20.0, 60.0, 20.0},
                byzantinePercentage,
                attack
            );
            scenario.addMetricsLogger(outputPath);
            System.out.println("Running Arq-REDD+ HYBRID " + byzantinePercentage + "% (" + totalValidators + " nodes) ...");
            scenario.run();
            metrics = scenario.getMetrics();
            System.out.println(String.format("  Blocks: %d generated, %d finalized, %d forked (Bf=%.3f%%)",
                metrics.getTotalBlocksGenerated(), metrics.getBlockCount(), metrics.getForkedBlocks(),
                metrics.getForkRate()));
            System.out.println(String.format("  Double-spend Pdv: %.3f%% (%d/%d attempts successful)",
                metrics.getDoubleSpendSuccessProbability(),
                metrics.getDoubleSpendSuccesses(),
                metrics.getDoubleSpendAttempts()));
        } catch (Exception e) {
            System.out.println("⚠️  Arq-REDD+ simulation failed: " + e.getMessage());
            e.printStackTrace();
        }
        return metrics;
    }

    private static void sweepArqREDD(SimulationMetrics aggregate, int totalValidators, double duration, int step, int max,
                                    long seed, ByzantineConfig.AttackType attack, String outputRoot) {
        System.out.println("🔬 Sweep Arq-REDD+ 0–" + max + "% (step " + step + ")...");
        for (int pct = 0; pct <= max; pct += step) {
            int byzCount = (int) Math.round(totalValidators * pct / 100.0);
            SimulationMetrics m = runArqREDDScenario(totalValidators, pct, duration, seed + pct + 100, attack,
                outputRoot + "/arq-redd-sweep-" + pct + ".csv");
            m.setTotalValidators(totalValidators);
            boolean secure = m.evaluateSecurity(10.0, 0.5);
            aggregate.recordByzantineTestResult(byzCount, secure);
            System.out.println(String.format("  Sweep %2d%% -> byz=%d: %s (fork=%.3f%%, pdv=%.3f%%)",
                pct, byzCount, secure ? "SECURE" : "INSECURE", m.getForkRate(), m.getDoubleSpendSuccessProbability()));
            if (!secure) {
                System.out.println("  Insecure threshold reached at " + pct + "% - stopping sweep.");
                break;
            }
        }
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

    private static java.util.Map<String, String> parseArgs(String[] args) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                String[] parts = arg.substring(2).split("=", 2);
                if (parts.length == 2) {
                    map.put(parts[0], parts[1]);
                }
            }
        }
        return map;
    }

    private static java.util.List<Integer> parseIntList(String csv) {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        for (String s : csv.split(",")) {
            list.add(Integer.parseInt(s.trim()));
        }
        return list;
    }

    private static java.util.List<Double> parseDoubleList(String csv) {
        java.util.List<Double> list = new java.util.ArrayList<>();
        for (String s : csv.split(",")) {
            list.add(Double.parseDouble(s.trim()));
        }
        return list;
    }
}
