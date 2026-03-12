package jabs.example;

import jabs.config.ByzantineConfig;
import jabs.metrics.SimulationMetrics;
import jabs.scenario.HybridNetworkScenario;
import jabs.scenario.CasperEthereumNetworkScenario;
import jabs.scenario.BitcoinGlobalNetworkScenario;
import jabs.scenario.ParliaBSCNetworkScenario;
import jabs.scenario.ParliaBSCNetworkScenario;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Utility to empirically sweep Byzantine validator counts and record security
 * results.
 * Generates an aggregated CSV with columns:
 * protocol,n,f,trial,seed,avgPdv,avgFinalizationTime,secure
 */
public class ByzantineSweep {

    public static void main(String[] args) throws IOException {
        java.util.Map<String, String> cli = parseArgs(args);
        int n = Integer.parseInt(cli.getOrDefault("validators", "100"));
        int maxByz = Integer.parseInt(cli.getOrDefault("max-byz", String.valueOf(n / 2)));
        int trials = Integer.parseInt(cli.getOrDefault("trials", "30"));
        double duration = Double.parseDouble(cli.getOrDefault("duration", "600"));
        String type = cli.getOrDefault("type", "VOTING").toUpperCase(Locale.ROOT);
        String attackStr = cli.getOrDefault("attack", "EQUIVOCATION");
        ByzantineConfig.AttackType attack = ByzantineConfig.AttackType.valueOf(attackStr.toUpperCase());
        // transaction generation options (used by POW scenarios)
        boolean enableTransactions = Boolean.parseBoolean(cli.getOrDefault("enable-tx", "true"));
        double txInterval = Double.parseDouble(cli.getOrDefault("tx-interval", "5.0"));
        String out = cli.getOrDefault("out", "output/byzantine-sweep.csv");

        Path outPath = Paths.get(out);
        Path outDir = outPath.getParent() == null ? Paths.get(".") : outPath.getParent();
        Files.createDirectories(outDir);

        try (BufferedWriter bw = Files.newBufferedWriter(outPath)) {
            bw.write(
                    "protocol,n,f,trials,avgPdv,stdPdv,avgFinalizationTime,stdFinalizationTime,avgConfirmationLatency,stdConfirmationLatency,avgBlockCount,stdBlockCount,avgTraffic,stdTraffic,avgEmpiricalBFT_pct,stdEmpiricalBFT_pct,secureCount\n");

            int step = Math.max(1, n / 100); // step corresponds to ~1% of N
            java.util.List<Integer> fValues = new java.util.ArrayList<>();
            fValues.add(0); // include the 0-byzantine case
            for (int v = step; v <= maxByz; v += step) {
                fValues.add(v);
            }

            for (int f : fValues) {
                boolean anySecureInThisF = false;
                int trialsExecuted = 0;
                double sumPdv = 0.0;
                double sumFinalTime = 0.0;
                double sumConfirmationLatency = 0.0;
                double sumBlocks = 0.0;
                double sumTraffic = 0.0;
                double sumEmpiricalBFT = 0.0;
                double sumPdvSq = 0.0;
                double sumFinalTimeSq = 0.0;
                double sumConfirmationLatencySq = 0.0;
                double sumBlocksSq = 0.0;
                double sumTrafficSq = 0.0;
                double sumEmpiricalBFTSq = 0.0;
                int secureCount = 0;

                for (int t = 0; t < trials; t++) {
                    long seed = Long.parseLong(cli.getOrDefault("seed", "12345")) + n * 1000L + t;

                    SimulationMetrics metrics = new SimulationMetrics();
                    metrics.setTotalValidators(n);
                    metrics.setByzantineValidators(f);

                    try {
                        switch (type) {
                            case "VOTING": {
                                double byzPct = (n == 0) ? 0.0 : (100.0 * f / n);
                                HybridNetworkScenario scenario = new HybridNetworkScenario(
                                        "sweep/arqREDD/HybridSweep-" + n + "n-" + f + "b",
                                        seed,
                                        n,
                                        duration,
                                        byzPct,
                                        attack);
                                String tmp = outDir.resolve("tmp/" + type + "/" + n + "n-" + f + "-" + t + ".csv")
                                        .toString();
                                Files.createDirectories(Paths.get(tmp).getParent());
                                scenario.addMetricsLogger(tmp, metrics);
                                scenario.run();
                                break;
                            }
                            case "POS": {
                                int numMiners = (int) Math.max(1, Math.round(n * 0.3));
                                int numStakeholders = Math.max(1, n);
                                double byzPct = (n == 0) ? 0.0 : (100.0 * f / n);
                                CasperEthereumNetworkScenario scenario = new CasperEthereumNetworkScenario(
                                        "sweep/Ethereum/CasperSweep-" + n + "n-" + f + "b",
                                        seed,
                                        duration,
                                        15.0,
                                        14,
                                        numMiners,
                                        numStakeholders,
                                        byzPct,
                                        attack);
                                String tmp = outDir.resolve("tmp/" + type + "/" + n + "n-" + f + "-" + t + ".csv")
                                        .toString();
                                Files.createDirectories(Paths.get(tmp).getParent());
                                scenario.addMetricsLogger(tmp, metrics);
                                scenario.run();
                                break;
                            }
                            case "POW": {
                                int confirmationDepth = 6;
                                double byzPct = (n == 0) ? 0.0 : (100.0 * f / n);
                                BitcoinGlobalNetworkScenario scenario = new BitcoinGlobalNetworkScenario(
                                        "sweep/Bitcoin/BitcoinSweep-" + n + "n-" + f + "b",
                                        seed,
                                        (long) duration,
                                        600.0,
                                        n,  // numMiners (represents hashpower distribution)
                                        0,  // numNodes (non-mining nodes)
                                        confirmationDepth,
                                        enableTransactions, // whether to generate txs
                                        txInterval,
                                        byzPct,  // Byzantine percentage of hashpower
                                        attack);
                                String tmp = outDir.resolve("tmp/" + type + "/" + n + "n-" + f + "-" + t + ".csv")
                                        .toString();
                                Files.createDirectories(Paths.get(tmp).getParent());
                                scenario.addMetricsLogger(tmp, metrics);
                                scenario.run();
                                break;
                            }
                            case "POSA": {
                                double byzPct = (n == 0) ? 0.0 : (100.0 * f / n);
                                int numMiners = (int) Math.max(1, Math.round(n * 0.3));
                                int turnLength = Integer.parseInt(cli.getOrDefault("turn-length", "3"));
                                int epochLength = Integer.parseInt(cli.getOrDefault("epoch-length", "21"));

                                ParliaBSCNetworkScenario scenario = new ParliaBSCNetworkScenario(
                                    "sweep/Parlia/ParliaSweep-" + n + "n-" + f + "b",
                                    seed,
                                    duration,
                                    3.0,
                                    turnLength,
                                    epochLength,
                                    numMiners,
                                    n);

                                ByzantineConfig byzConfig = new ByzantineConfig(
                                    n,
                                    byzPct,
                                    attack,
                                    seed);
                                scenario.setByzantineConfig(byzConfig);

                                String tmp = outDir.resolve("tmp/" + type + "/" + n + "n-" + f + "-" + t + ".csv")
                                    .toString();
                                Files.createDirectories(Paths.get(tmp).getParent());
                                scenario.addMetricsLogger(tmp, metrics);
                                scenario.run();
                                break;
                                }
                            default:
                                throw new IllegalArgumentException("Unsupported protocol type: " + type);
                        }

                        double maxPdv = Double.parseDouble(cli.getOrDefault("max-pdv-pct", "0.5"));
                        boolean secure = metrics.evaluateSecurity(maxPdv);
                        if (secure) {
                            anySecureInThisF = true;
                            secureCount++;
                        }

                        trialsExecuted++;
                        double pdvVal = metrics.getDoubleSpendSuccessProbability();
                        double finalTimeVal = metrics.getAverageBlockFinalizationTime();
                        double confirmLatencyVal = metrics.getAverageTransactionConfirmationLatency();
                        double blocksVal = metrics.getBlockCount();
                        double trafficVal = metrics.getAverageTrafficPerBlock();
                        double empiricalBFTVal = metrics.getEmpiricalByzantineFaultTolerance();
                        
                        sumPdv += pdvVal;
                        sumFinalTime += finalTimeVal;
                        sumConfirmationLatency += confirmLatencyVal;
                        sumBlocks += blocksVal;
                        sumTraffic += trafficVal;
                        sumEmpiricalBFT += empiricalBFTVal;
                        
                        sumPdvSq += pdvVal * pdvVal;
                        sumFinalTimeSq += finalTimeVal * finalTimeVal;
                        sumConfirmationLatencySq += confirmLatencyVal * confirmLatencyVal;
                        sumBlocksSq += blocksVal * blocksVal;
                        sumTrafficSq += trafficVal * trafficVal;
                        sumEmpiricalBFTSq += empiricalBFTVal * empiricalBFTVal;

                    } catch (Exception e) {
                        System.err.println("Run failed for f=" + f + ", trial=" + t + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                if (trialsExecuted == 0) {
                    System.out.println(String.format(Locale.ROOT,
                            "No successful trials executed for f=%d (n=%d). Skipping.", f, n));
                    // continue;
                }

                // compute averages across executed trials
                double avgPdv = sumPdv / trialsExecuted;
                double avgFinalTime = sumFinalTime / trialsExecuted;
                double avgConfirmationLatency = sumConfirmationLatency / trialsExecuted;
                double avgBlockCount = sumBlocks / trialsExecuted;
                double avgTraffic = sumTraffic / trialsExecuted;
                double avgEmpiricalBFT = sumEmpiricalBFT / trialsExecuted; // stored as ratio in metrics
                
                // compute standard deviations (sample std dev formula)
                int denom = Math.max(1, trialsExecuted - 1);
                double stdPdv = Math.sqrt(Math.max(0, (sumPdvSq - (sumPdv * sumPdv) / trialsExecuted) / denom));
                double stdFinalTime = Math.sqrt(Math.max(0, (sumFinalTimeSq - (sumFinalTime * sumFinalTime) / trialsExecuted) / denom));
                double stdConfirmationLatency = Math.sqrt(Math.max(0, (sumConfirmationLatencySq - (sumConfirmationLatency * sumConfirmationLatency) / trialsExecuted) / denom));
                double stdBlockCount = Math.sqrt(Math.max(0, (sumBlocksSq - (sumBlocks * sumBlocks) / trialsExecuted) / denom));
                double stdTraffic = Math.sqrt(Math.max(0, (sumTrafficSq - (sumTraffic * sumTraffic) / trialsExecuted) / denom));
                double stdEmpiricalBFT = Math.sqrt(Math.max(0, (sumEmpiricalBFTSq - (sumEmpiricalBFT * sumEmpiricalBFT) / trialsExecuted) / denom));

                // Decide security using aggregated averages
                double maxPdv = Double.parseDouble(cli.getOrDefault("max-pdv-pct", "0.5"));
                // Map protocol type to ConsensusType
                SimulationMetrics.ConsensusType consensusType;
                switch (type) {
                    case "VOTING":
                        consensusType = SimulationMetrics.ConsensusType.VOTING;
                        break;
                    case "POS":
                        consensusType = SimulationMetrics.ConsensusType.POS;
                        break;
                    case "POW":
                        consensusType = SimulationMetrics.ConsensusType.POW;
                        break;
                    case "POSA":
                        consensusType = SimulationMetrics.ConsensusType.POS;
                        break;
                    default:
                        consensusType = SimulationMetrics.ConsensusType.VOTING;
                        break;
                }

                boolean avgSecure = SimulationMetrics.evaluateSecurityFromAverages(
                        consensusType,
                        maxPdv,
                        avgPdv,
                        avgEmpiricalBFT,
                        n,
                        f);

                // Use averaged decision as indicator whether this f is considered secure
                anySecureInThisF = avgSecure;

                // write aggregated row: empiricalBFT reported as percentage
                bw.write(String.format(Locale.ROOT,
                        "%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.0f,%.0f,%.6f,%.6f,%.6f,%.6f,%d\n",
                        type, n, f, trialsExecuted,
                        avgPdv, stdPdv,
                        avgFinalTime, stdFinalTime,
                        avgConfirmationLatency, stdConfirmationLatency,
                        avgBlockCount, stdBlockCount,
                        avgTraffic, stdTraffic,
                        avgEmpiricalBFT * 100.0, stdEmpiricalBFT * 100.0,
                        secureCount));
                bw.flush();

                // If averaged result reports insecure for this f, stop the sweep early
                if (!anySecureInThisF) {
                    System.out.println(String.format(Locale.ROOT,
                            "Averaged decision: no secure result for f=%d (n=%d). Stopping further f values to save time.",
                            f, n));
                    // break;
                }
            }
        }

        System.out.println("Sweep complete. Results in " + out);
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
}
