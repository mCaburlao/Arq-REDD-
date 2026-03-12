package jabs.example;

import jabs.config.ByzantineConfig;
import jabs.ledgerdata.DoubleSpendTracker;
import jabs.metrics.SimulationMetrics;
import jabs.scenario.AbstractScenario;
import jabs.scenario.ArqReddVotingScenario;
import jabs.scenario.HybridNetworkScenario;
import jabs.scenario.PBFTLANScenario;
import jabs.log.BlockFinalizationLogger;
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
        System.out.println("║   MVP: Arq-REDD+ vs others - Multiple Trials Harness   ║");
        System.out.println("║   Date: 2026-03-05                                     ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");

        // CLI args:
        // --validators=200 --protocols=EARTH,MCO2,TREE --trials=10 --duration=600
        // --durations=EARTH:600,MCO2:300,TREE:450
        // --seed=12345 --output=output/mvp-validation --csv-out=output/mvp-results.csv
        java.util.Map<String, String> cli = parseArgs(args);
        java.util.List<Integer> validatorsList = parseIntList(cli.getOrDefault("validators", "200"));
        java.util.List<String> protocolList = parseStringList(cli.getOrDefault("protocols", "EARTH"));
        int trials = Integer.parseInt(cli.getOrDefault("trials", "5"));
        double defaultDuration = Double.parseDouble(cli.getOrDefault("duration", "600"));
        long baseSeed = Long.parseLong(cli.getOrDefault("seed", "12345"));
        String outputRoot = cli.getOrDefault("output", "output/mvp-validation");
        String csvOut = cli.getOrDefault("csv-out", outputRoot + "/mvp-results.csv");
        boolean enableTransactions = Boolean.parseBoolean(cli.getOrDefault("enable-tx", "true"));
        double txInterval = Double.parseDouble(cli.getOrDefault("tx-interval", "5.0"));
        
        // Parse per-protocol durations: EARTH:600,MCO2:300,TREE:450
        java.util.Map<String, Double> protocolDurations = parseProtocolDurations(
            cli.getOrDefault("durations", ""), protocolList, defaultDuration);
        
        Files.createDirectories(Paths.get(outputRoot));

        System.out.println("Args: validators=" + validatorsList +
                ", protocols=" + protocolList +
                ", trials=" + trials +
                ", default duration=" + defaultDuration + "s, seed=" + baseSeed +
                ", out=" + outputRoot +
                ", csv-out=" + csvOut);
        System.out.println("Protocol-specific durations: " + protocolDurations);

        // Initialize CSV file with header
        initializeCSV(csvOut);

        for (int validators : validatorsList) {
            System.out.println("\n╔═══════════════════════════════════════╗");
            System.out.println("║ Testing with " + validators + " validators ║");
            System.out.println("╚═══════════════════════════════════════╝");

            for (String protocol : protocolList) {
                System.out.println("\n  ■ Protocol: " + protocol);
                
                // Create accumulator for this protocol/validators combination
                MetricsAccumulator acc = new MetricsAccumulator();
                double protocolDuration = protocolDurations.get(protocol);
                
                // Run all trials for this protocol/validators combination
                for (int trial = 0; trial < trials; trial++) {
                    long runSeed = baseSeed + validators * 1000L + trial;
                    System.out.println("    Trial " + (trial + 1) + "/" + trials + " (seed=" + runSeed + ")");

                    try {
                        SimulationMetrics metrics = runProtocol(protocol, validators, runSeed, protocolDuration, 
                            outputRoot + "/" + protocol.toLowerCase() + "-" + validators + "v-t" + trial + ".csv",
                            enableTransactions, txInterval);
                        
                        // Accumulate metrics
                        acc.addTrial(
                            metrics.getAverageBlockFinalizationTime(),
                            metrics.getAverageTrafficPerBlock(),
                            metrics.getAverageTransactionConfirmationLatency()
                        );
                        
                        System.out.println("      ✓ completed");
                    } catch (Exception e) {
                        System.out.println("      ✗ failed: " + e.getMessage());
                    }
                }
                
                // After all trials for this protocol/validators, write to CSV
                System.out.println("    → Writing results for " + protocol + " (n=" + validators + ") to CSV");
                writeSingleResultToCSV(csvOut, protocol, validators, trials, acc);
            }
        }

        // Write CSV results
        System.out.println("\n╔═══════════════════════════════════╗");
        System.out.println("║    All simulations completed!      ║");
        System.out.println("╚═══════════════════════════════════╝\n");
        
        System.out.println("✓ Results written to: " + csvOut);
    }

    /**
     * Parse protocol-specific durations from format: EARTH:600,MCO2:300,TREE:450
     * Initialize all protocols with default duration, then override with specified values
     */
    private static java.util.Map<String, Double> parseProtocolDurations(String durationStr, 
            java.util.List<String> protocolList, double defaultDuration) {
        java.util.Map<String, Double> map = new java.util.HashMap<>();
        
        // Initialize all protocols with default duration
        for (String protocol : protocolList) {
            map.put(protocol, defaultDuration);
        }
        
        // Override with specified durations
        if (durationStr != null && !durationStr.isEmpty()) {
            for (String pair : durationStr.split(",")) {
                String[] parts = pair.trim().split(":");
                if (parts.length == 2) {
                    try {
                        String protocol = parts[0].trim().toUpperCase();
                        double duration = Double.parseDouble(parts[1].trim());
                        map.put(protocol, duration);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid duration format: " + pair);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Initialize CSV file with header
     */
    private static void initializeCSV(String csvPath) throws IOException {
        try (java.io.BufferedWriter bw = Files.newBufferedWriter(java.nio.file.Paths.get(csvPath))) {
            bw.write("project;n;trials;Tb_avg;Tb_std;Cb_avg;Cb_std;Tt_avg;Tt_std\n");
        }
    }

    /**
     * Write a single result line to CSV (append mode)
     */
    private static void writeSingleResultToCSV(String csvPath, String protocol, int n, int trials,
            MetricsAccumulator acc) throws IOException {
        try (java.io.BufferedWriter bw = Files.newBufferedWriter(
                java.nio.file.Paths.get(csvPath),
                java.nio.file.StandardOpenOption.APPEND)) {
            
            bw.write(String.format(java.util.Locale.ROOT,
                    "%s;%d;%d;%.6f;%.6f;%.6f;%.6f;%.6f;%.6f\n",
                    protocol, n, trials,
                    acc.getAvgTb(), acc.getStdTb(),
                    acc.getAvgCb(), acc.getStdCb(),
                    acc.getAvgTt(), acc.getStdTt()
            ));
        }
    }

    /**
     * Run a single protocol simulation
     */
    private static SimulationMetrics runProtocol(String protocol, int validators, long seed, 
            double duration, String outputPath, boolean enableTx, double txInterval) throws Exception {
        
        switch (protocol.toUpperCase()) {
            case "EARTH":
                return runEarthDollarScenario(validators, seed, duration, outputPath, enableTx, txInterval);
            case "MCO2":
                return runMCO2Scenario(validators, seed, duration, outputPath);
            case "TREE":
                return runTreeCycleScenario(validators, seed, duration, outputPath);
            case "AMBIFY":
                return runAmbifyScenario(validators, seed, duration, outputPath);
            case "OFFSET":
                return runOffsetBitcoinScenario(validators, seed, duration, outputPath, enableTx, txInterval);
            case "ARQ":
                return runArqREDDScenario(validators, duration, seed, outputPath);
            default:
                throw new IllegalArgumentException("Unknown protocol: " + protocol);
        }
    }

    /**
     * Helper class to accumulate metrics across trials
     */
    private static class MetricsAccumulator {
        private double sumTb = 0.0, sumTbSq = 0.0;
        private double sumCb = 0.0, sumCbSq = 0.0;
        private double sumTt = 0.0, sumTtSq = 0.0;
        private int count = 0;

        void addTrial(double tb, double cb, double tt) {
            sumTb += tb;
            sumTbSq += tb * tb;
            sumCb += cb;
            sumCbSq += cb * cb;
            sumTt += tt;
            sumTtSq += tt * tt;
            count++;
        }

        double getAvgTb() { return count > 0 ? sumTb / count : 0.0; }
        double getAvgCb() { return count > 0 ? sumCb / count : 0.0; }
        double getAvgTt() { return count > 0 ? sumTt / count : 0.0; }

        double getStdTb() {
            if (count <= 1) return 0.0;
            double var = (sumTbSq - (sumTb * sumTb) / count) / (count - 1);
            return Math.sqrt(Math.max(0, var));
        }

        double getStdCb() {
            if (count <= 1) return 0.0;
            double var = (sumCbSq - (sumCb * sumCb) / count) / (count - 1);
            return Math.sqrt(Math.max(0, var));
        }

        double getStdTt() {
            if (count <= 1) return 0.0;
            double var = (sumTtSq - (sumTt * sumTt) / count) / (count - 1);
            return Math.sqrt(Math.max(0, var));
        }
    }

    private static SimulationMetrics runArqREDDScenario(int totalValidators, double duration,
            long seed, String outputPath) {
        SimulationMetrics metrics = new SimulationMetrics();
        metrics.setTotalValidators(totalValidators);
        metrics.setByzantineValidators(0);
        metrics.setConsensusType(SimulationMetrics.ConsensusType.VOTING);

        try {
            HybridNetworkScenario scenario = new HybridNetworkScenario(
                    "Arq-REDD+ Hybrid (Honest Nodes)",
                    seed,
                    totalValidators,
                    duration,
                    30.0,
                    new double[] { 20.0, 60.0, 20.0 },
                    0.0,
                    ByzantineConfig.AttackType.NONE);
            scenario.addMetricsLogger(outputPath);
            System.out.println(
                    "Running Arq-REDD+ HYBRID (" + totalValidators + " honest nodes) ...");
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
                    (int) (numStakeholders * 0.3),
                    (int) (numStakeholders * 0.7)); // 70% staking nodes
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
                    (int) (numStakeholders * 0.3),
                    (int) (numStakeholders * 0.7));
            scenario.addMetricsLogger(outputPath, metrics);
            scenario.AddNewLogger(new BlockFinalizationLogger(Paths.get(outputPath.replace(".csv", "-basic-log.csv"))));
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
                    (int) (numStakeholders * 0.3),
                    (int) (numStakeholders * 0.7));
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
            String outputPath, boolean enableTx, double txInterval) {
        SimulationMetrics metrics = new SimulationMetrics();
        metrics.setTotalValidators(numStakeholders);
        metrics.setConsensusType(SimulationMetrics.ConsensusType.POW);
        try {
            jabs.scenario.BitcoinGlobalNetworkScenario scenario = new jabs.scenario.BitcoinGlobalNetworkScenario(
                    "Offset Bitcoin - " + numStakeholders + " stakeholders",
                    seed,
                    (long) duration,
                    600.0,
                    numStakeholders,
                    0,
                    14,
                    enableTx,          // enable transaction generation
                    txInterval);       // average interval between txs
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
            String outputPath, boolean enableTx, double txInterval) {
        SimulationMetrics metrics = new SimulationMetrics();
        metrics.setTotalValidators(numStakeholders);
        metrics.setConsensusType(SimulationMetrics.ConsensusType.POS);
        try {
            jabs.scenario.BitcoinGlobalNetworkScenario scenario = new jabs.scenario.BitcoinGlobalNetworkScenario(
                    "Earth Dollar - " + numStakeholders + " stakeholders",
                    seed,
                    (long) duration,
                    450.0,
                    numStakeholders,
                    0,
                    6,
                    enableTx,
                    txInterval);
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
        System.out.println(String.format("  Blocks: %d generated, %d finalized",
                metrics.getTotalBlocksGenerated(), metrics.getBlockCount()));
        System.out.println(String.format("  Double-spend Pdv: %.3f%% (%d/%d attempts successful)",
                metrics.getDoubleSpendSuccessProbability(),
                metrics.getDoubleSpendSuccesses(),
                metrics.getDoubleSpendAttempts()));
    }

    /**
     * Print formatted metrics report and save to file
     */
    private static void printMetricsReport(String protocolName, SimulationMetrics metrics, String reportFile) {
        StringBuilder sb = new StringBuilder();
        
        String line1 = "╔" + "═".repeat(58) + "╗";
        String line2 = "║ " + protocolName.toUpperCase() + " METRICS REPORT" +
                " ".repeat(Math.max(0, 43 - protocolName.length())) + "║";
        String line3 = "╠" + "═".repeat(58) + "╣";
        String lineEnd = "╚" + "═".repeat(58) + "╝";
        
        sb.append(line1).append("\n");
        sb.append(line2).append("\n");
        sb.append(line3).append("\n");
        
        System.out.println(line1);
        System.out.println(line2);
        System.out.println(line3);

        if (metrics.getBlockCount() == 0) {
            String noBlocksMsg = padEnd("║ No blocks generated in simulation", 59) + "║";
            sb.append(noBlocksMsg).append("\n");
            sb.append(lineEnd).append("\n");
            System.out.println(noBlocksMsg);
            System.out.println(lineEnd);
            saveReportToFile(reportFile, sb.toString());
            return;
        }

        String[] lines = new String[15];
        lines[0] = padEnd("║ Metric 1 - Tb (Block Finalization Time):", 59) + "║";
        lines[1] = padEnd(String.format("║   Average: %.3f seconds/block", metrics.getAverageBlockFinalizationTime()), 59) + "║";
        lines[2] = padEnd(String.format("║   StdDev: %.3f seconds", metrics.getStandardDeviationFinalizationTime()), 59) + "║";
        lines[3] = padEnd(String.format("║   p95: %.3f seconds", metrics.getPercentileFinalizationTime(95)), 59) + "║";
        
        lines[4] = padEnd("║ Metric 2 - Cb (Network Traffic):", 59) + "║";
        lines[5] = padEnd(String.format("║   Average: %.6f MB/block", metrics.getAverageTrafficPerBlock()), 59) + "║";
        lines[6] = padEnd(String.format("║   StdDev: %.6f MB", metrics.getStandardDeviationTraffic()), 59) + "║";
        lines[7] = padEnd(String.format("║   p95: %.6f MB", metrics.getPercentileTraffic(95)), 59) + "║";
        
        lines[8] = padEnd("║ Metric 3 - Tt (Transaction Confirmation Latency):", 59) + "║";
        lines[9] = padEnd(String.format("║   Average: %.3f seconds/tx", metrics.getAverageTransactionConfirmationLatency()), 59) + "║";
        lines[10] = padEnd(String.format("║   StdDev: %.3f seconds", metrics.getStandardDeviationConfirmationLatency()), 59) + "║";
        lines[11] = padEnd(String.format("║   p95: %.3f seconds", metrics.getPercentileConfirmationLatency(95)), 59) + "║";
        
        lines[12] = padEnd("║ Metric 4 - BFT (Byzantine Fault Tolerance):", 59) + "║";
        double attackThreshold = metrics.getByzantineFaultTolerance();
        lines[13] = padEnd(String.format("║   Attack threshold: %.2f%% (%d nodes)", attackThreshold,
                metrics.getBFTAttackThreshold()), 59) + "║";
        
        lines[14] = padEnd("║ Metric 5 - Pdv (Double-spending):", 59) + "║";
        
        for (String l : lines) {
            System.out.println(l);
            sb.append(l).append("\n");
        }
        
        String pdvLine = padEnd(String.format("║   Probability: %.3f%%", metrics.getDoubleSpendSuccessProbability()), 59) + "║";
        System.out.println(pdvLine);
        sb.append(pdvLine).append("\n");
        
        System.out.println(lineEnd);
        sb.append(lineEnd).append("\n");
        
        saveReportToFile(reportFile, sb.toString());
    }
    
    /**
     * Save metrics report to file (append mode)
     */
    private static void saveReportToFile(String filePath, String content) {
        try {
            Files.write(Paths.get(filePath), content.getBytes(), 
                java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("  ⚠️  Failed to save report to " + filePath + ": " + e.getMessage());
        }
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

    private static java.util.List<String> parseStringList(String csv) {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String s : csv.split(",")) {
            list.add(s.trim().toUpperCase());
        }
        return list;
    }
}
