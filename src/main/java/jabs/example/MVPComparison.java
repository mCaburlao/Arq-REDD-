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
        // --validators=20,100,200 --byzantine=0,33 --duration=600 --sweep-step=5
        // --sweep-max=50
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
                // sweepArqREDD(arqMetrics, validators, sweepDuration, sweepStep, sweepMax,
                // runSeed, attack, outputRoot);

                // Baseline comparisons: MCO2, TreeCycle, Ambify (Parlia)
                SimulationMetrics mco2Metrics = runMCO2Scenario(validators, runSeed, duration,
                        outputRoot + "/mco2-" + scenarioLabel + ".csv");

                SimulationMetrics treeMetrics = runTreeCycleScenario(validators, runSeed, duration,
                        outputRoot + "/treecycle-" + scenarioLabel + ".csv");

                SimulationMetrics ambifyMetrics = runAmbifyScenario(validators, runSeed, duration,
                        outputRoot + "/ambify-" + scenarioLabel + ".csv");

                SimulationMetrics offsetMetrics = runOffsetBitcoinScenario(validators, runSeed, duration,
                        outputRoot + "/offset-bitcoin-" + scenarioLabel + ".csv");

                SimulationMetrics earthMetrics = runEarthDollarScenario(validators, runSeed, duration,
                        outputRoot + "/earth-dollar-" + scenarioLabel + ".csv");

                printMetricsReport("Arq-REDD+ (" + scenarioLabel + ")", arqMetrics);
                printMetricsReport("Ambify (Parlia) (" + scenarioLabel + ")", ambifyMetrics);
                printMetricsReport("TreeCycle (" + scenarioLabel + ")", treeMetrics);
                printMetricsReport("MCO2 (" + scenarioLabel + ")", mco2Metrics);
                printMetricsReport("Offset Bitcoin (" + scenarioLabel + ")", offsetMetrics);
                printMetricsReport("Earth Dollar (" + scenarioLabel + ")", earthMetrics);
            }
        }

        System.out.println("\nDone. Check per-run CSVs under " + outputRoot);
    }

    private static SimulationMetrics runArqREDDScenario(int totalValidators, double byzantinePercentage,
            double duration,
            long seed, ByzantineConfig.AttackType attack, String outputPath) {
        SimulationMetrics metrics = new SimulationMetrics();
        metrics.setTotalValidators(totalValidators);
        metrics.setByzantineValidators((int) Math.round(totalValidators * byzantinePercentage / 100.0));
        metrics.setConsensusType(SimulationMetrics.ConsensusType.VOTING);

        try {
            HybridNetworkScenario scenario = new HybridNetworkScenario(
                    "Arq-REDD+ Hybrid " + byzantinePercentage + "%",
                    seed,
                    totalValidators,
                    duration,
                    30.0,
                    new double[] { 20.0, 60.0, 20.0 },
                    byzantinePercentage,
                    attack);
            scenario.addMetricsLogger(outputPath);
            System.out.println(
                    "Running Arq-REDD+ HYBRID " + byzantinePercentage + "% (" + totalValidators + " nodes) ...");
            scenario.run();
            metrics = scenario.getMetrics();
            printScenarioFinilizedMetrics(metrics);
        } catch (Exception e) {
            System.out.println("⚠️  Arq-REDD+ simulation failed: " + e.getMessage());
            e.printStackTrace();
        }
        return metrics;
    }

    private static SimulationMetrics runMCO2Scenario(int numStakeholders, long seed, double duration,
            String outputPath) {
        SimulationMetrics metrics = new SimulationMetrics();
        metrics.setTotalValidators(numStakeholders);
        metrics.setConsensusType(SimulationMetrics.ConsensusType.POS);
        try {
            jabs.scenario.CasperEthereumNetworkScenario scenario = new jabs.scenario.CasperEthereumNetworkScenario(
                    "MCO2 - " + numStakeholders + " stakeholders",
                    (int) seed,
                    duration,
                    15.0,
                    14,
                    12,
                    numStakeholders);
            scenario.addMetricsLogger(outputPath, metrics);
            System.out.println("Running MCO2 scenario (" + numStakeholders + " nodes) ...");
            scenario.run();
            metrics = scenario.getMetrics();
            printScenarioFinilizedMetrics(metrics);
        } catch (Exception e) {
            System.out.println("⚠️  MCO2 simulation failed: " + e.getMessage());
            e.printStackTrace();
        }
        return metrics;
    }

    private static SimulationMetrics runTreeCycleScenario(int numStakeholders, long seed, double duration,
            String outputPath) {
        SimulationMetrics metrics = new SimulationMetrics();
        metrics.setTotalValidators(numStakeholders);
        metrics.setConsensusType(SimulationMetrics.ConsensusType.POS);
        try {
            jabs.scenario.CasperEthereumNetworkScenario scenario = new jabs.scenario.CasperEthereumNetworkScenario(
                    "TreeCycle - " + numStakeholders + " stakeholders",
                    (int) seed,
                    duration,
                    60.0,
                    14,
                    12,
                    numStakeholders);
            scenario.addMetricsLogger(outputPath, metrics);
            System.out.println("Running TreeCycle scenario (" + numStakeholders + " nodes) ...");
            scenario.run();
            metrics = scenario.getMetrics();
            printScenarioFinilizedMetrics(metrics);
        } catch (Exception e) {
            System.out.println("⚠️  TreeCycle simulation failed: " + e.getMessage());
            e.printStackTrace();
        }
        return metrics;
    }

    private static SimulationMetrics runAmbifyScenario(int numStakeholders, long seed, double duration,
            String outputPath) {
        SimulationMetrics metrics = new SimulationMetrics();
        metrics.setTotalValidators(numStakeholders);
        metrics.setConsensusType(SimulationMetrics.ConsensusType.POS);
        try {
            jabs.scenario.ParliaBSCNetworkScenario scenario = new jabs.scenario.ParliaBSCNetworkScenario(
                    "Ambify - " + numStakeholders + " stakeholders",
                    (int) seed,
                    duration,
                    3.0,
                    3,
                    21,
                    12,
                    numStakeholders);
            scenario.addMetricsLogger(outputPath, metrics);
            System.out.println("Running Ambify (Parlia) scenario (" + numStakeholders + " nodes) ...");
            scenario.run();
            metrics = scenario.getMetrics();
            printScenarioFinilizedMetrics(metrics);
        } catch (Exception e) {
            System.out.println("⚠️  Ambify simulation failed: " + e.getMessage());
            e.printStackTrace();
        }
        return metrics;
    }

    private static SimulationMetrics runOffsetBitcoinScenario(int numStakeholders, long seed, double duration,
            String outputPath) {
        SimulationMetrics metrics = new SimulationMetrics();
        metrics.setTotalValidators(numStakeholders);
        metrics.setConsensusType(SimulationMetrics.ConsensusType.POW);
        try {
            jabs.scenario.BitcoinGlobalNetworkScenario scenario = new jabs.scenario.BitcoinGlobalNetworkScenario(
                    "Offset Bitcoin - " + numStakeholders + " stakeholders",
                    seed,
                    (long) duration,
                    600.0,
                    numStakeholders);
            scenario.addMetricsLogger(outputPath, metrics);
            System.out.println("Running Offset Bitcoin scenario (" + numStakeholders + " nodes) ...");
            scenario.run();
            metrics = scenario.getMetrics();
            printScenarioFinilizedMetrics(metrics);
        } catch (Exception e) {
            System.out.println("⚠️  Offset Bitcoin simulation failed: " + e.getMessage());
            e.printStackTrace();
        }
        return metrics;
    }

    private static SimulationMetrics runEarthDollarScenario(int numStakeholders, long seed, double duration,
            String outputPath) {
        SimulationMetrics metrics = new SimulationMetrics();
        metrics.setTotalValidators(numStakeholders);
        metrics.setConsensusType(SimulationMetrics.ConsensusType.POS);
        try {
            jabs.scenario.CasperEthereumNetworkScenario scenario = new jabs.scenario.CasperEthereumNetworkScenario(
                    "Earth Dollar - " + numStakeholders + " stakeholders",
                    (int) seed,
                    duration,
                    450.0,
                    14,
                    12,
                    numStakeholders);
            scenario.addMetricsLogger(outputPath, metrics);
            System.out.println("Running Earth Dollar scenario (" + numStakeholders + " nodes) ...");
            scenario.run();
            metrics = scenario.getMetrics();
            printScenarioFinilizedMetrics(metrics);
        } catch (Exception e) {
            System.out.println("⚠️  Earth Dollar simulation failed: " + e.getMessage());
            e.printStackTrace();
        }
        return metrics;
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

    private static void printScenarioFinilizedMetrics(SimulationMetrics metrics) {
            System.out.println(String.format("  Blocks: %d generated, %d finalized, %d forked (Bf=%.3f%%)",
                    metrics.getTotalBlocksGenerated(), metrics.getBlockCount(), metrics.getForkedBlocks(),
                    metrics.getForkRate()));
            System.out.println(String.format("  Double-spend Pdv: %.3f%% (%d/%d attempts successful)",
                    metrics.getDoubleSpendSuccessProbability(),
                    metrics.getDoubleSpendSuccesses(),
                    metrics.getDoubleSpendAttempts()));
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
        System.out.println(
                padEnd(String.format("║   Average: %.3f seconds/block", metrics.getAverageBlockFinalizationTime()), 59)
                        + "║");
        System.out.println(
                padEnd(String.format("║   p95: %.3f seconds", metrics.getPercentileFinalizationTime(95)), 59) + "║");

        System.out.println(padEnd("║ Metric 2 - Cb (Network Traffic):", 59) + "║");
        System.out.println(
                padEnd(String.format("║   Average: %.6f MB/block", metrics.getAverageTrafficPerBlock()), 59) + "║");
        System.out.println(padEnd(String.format("║   p95: %.6f MB", metrics.getPercentileTraffic(95)), 59) + "║");

        System.out.println(padEnd("║ Metric 3 - Bf (Fork Rate):", 59) + "║");
        System.out.println(padEnd(String.format("║   Rate: %.3f%%", metrics.getForkRate()), 59) + "║");

        System.out.println(padEnd("║ Metric 4 - BFT (Byzantine Fault Tolerance):", 59) + "║");
        double attackThreshold = metrics.getByzantineFaultTolerance();
        System.out.println(padEnd(String.format("║   Attack threshold: %.2f%% (%d nodes)", attackThreshold, metrics.getBFTAttackThreshold()), 59) + "║");

        System.out.println(padEnd("║ Metric 5 - Pdv (Double-spending):", 59) + "║");
        System.out.println(
                padEnd(String.format("║   Probability: %.3f%%", metrics.getDoubleSpendSuccessProbability()), 59) + "║");

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
