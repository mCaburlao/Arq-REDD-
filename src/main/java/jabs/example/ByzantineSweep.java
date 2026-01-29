package jabs.example;

import jabs.config.ByzantineConfig;
import jabs.metrics.SimulationMetrics;
import jabs.scenario.HybridNetworkScenario;
import jabs.scenario.CasperEthereumNetworkScenario;
import jabs.scenario.BitcoinGlobalNetworkScenario;

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
 * protocol,n,f,trial,seed,avgPdv,forkRate,avgFinalizationTime,secure
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
        String out = cli.getOrDefault("out", "output/byzantine-sweep.csv");

        Path outPath = Paths.get(out);
        Path outDir = outPath.getParent() == null ? Paths.get(".") : outPath.getParent();
        Files.createDirectories(outDir);

        try (BufferedWriter bw = Files.newBufferedWriter(outPath)) {
            bw.write(
                    "protocol,n,f,trials,avgPdv,forkRate,avgFinalizationTime,avgBlockCount,avgEmpiricalBFT_pct,secureCount\n");

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
                double sumFork = 0.0;
                double sumFinalTime = 0.0;
                double sumBlocks = 0.0;
                double sumEmpiricalBFT = 0.0;
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
                                        "sweep/HybridSweep-" + n + "n-" + f + "b",
                                        seed,
                                        n,
                                        duration,
                                        byzPct,
                                        attack);
                                String tmp = outDir.resolve("tmp-" + type + "-" + n + "n-" + f + "-" + t + ".csv")
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
                                        "sweep/CasperSweep-" + n + "n-" + f + "b",
                                        seed,
                                        duration,
                                        15.0,
                                        14,
                                        numMiners,
                                        numStakeholders,
                                        byzPct,
                                        attack);
                                String tmp = outDir.resolve("tmp-" + type + "-" + n + "n-" + f + "-" + t + ".csv")
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
                                        "sweep/BitcoinSweep-" + n + "n-" + f + "b",
                                        seed,
                                        (long) duration,
                                        600.0,
                                        n,  // numMiners (represents hashpower distribution)
                                        0,  // numNodes (non-mining nodes)
                                        confirmationDepth,
                                        byzPct,  // Byzantine percentage of hashpower
                                        attack); // Attack type
                                String tmp = outDir.resolve("tmp-" + type + "-" + n + "n-" + f + "-" + t + ".csv")
                                        .toString();
                                Files.createDirectories(Paths.get(tmp).getParent());
                                scenario.addMetricsLogger(tmp, metrics);
                                scenario.run();
                                break;
                            }
                            default:
                                throw new IllegalArgumentException("Unsupported protocol type: " + type);
                        }

                        double maxFork = Double.parseDouble(cli.getOrDefault("max-fork-pct", "1.0"));
                        double maxPdv = Double.parseDouble(cli.getOrDefault("max-pdv-pct", "0.5"));
                        boolean secure = metrics.evaluateSecurity(maxFork, maxPdv);
                        if (secure) {
                            anySecureInThisF = true;
                            secureCount++;
                        }

                        trialsExecuted++;
                        sumPdv += metrics.getDoubleSpendSuccessProbability();
                        sumFork += metrics.getForkRate();
                        sumFinalTime += metrics.getAverageBlockFinalizationTime();
                        sumBlocks += metrics.getBlockCount();
                        sumEmpiricalBFT += metrics.getEmpiricalByzantineFaultTolerance();

                    } catch (Exception e) {
                        System.err.println("Run failed for f=" + f + ", trial=" + t + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                if (trialsExecuted == 0) {
                    System.out.println(String.format(Locale.ROOT,
                            "No successful trials executed for f=%d (n=%d). Skipping.", f, n));
                    continue;
                }

                // compute averages across executed trials
                double avgPdv = sumPdv / trialsExecuted;
                double avgFork = sumFork / trialsExecuted;
                double avgFinalTime = sumFinalTime / trialsExecuted;
                double avgBlockCount = sumBlocks / trialsExecuted;
                double avgEmpiricalBFT = sumEmpiricalBFT / trialsExecuted; // stored as ratio in metrics

                // Decide security using aggregated averages
                double maxFork = Double.parseDouble(cli.getOrDefault("max-fork-pct", "1.0"));
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
                    default:
                        consensusType = SimulationMetrics.ConsensusType.VOTING;
                        break;
                }

                boolean avgSecure = SimulationMetrics.evaluateSecurityFromAverages(
                        consensusType,
                        maxFork,
                        maxPdv,
                        avgFork,
                        avgPdv,
                        avgEmpiricalBFT,
                        n,
                        f);

                // Use averaged decision as indicator whether this f is considered secure
                anySecureInThisF = avgSecure;

                // write aggregated row: empiricalBFT reported as percentage
                bw.write(String.format(Locale.ROOT,
                        "%s,%d,%d,%d,%.6f,%.6f,%.6f,%.0f,%.6f,%d\n",
                        type, n, f, trialsExecuted,
                        avgPdv,
                        avgFork,
                        avgFinalTime,
                        avgBlockCount,
                        avgEmpiricalBFT * 100.0,
                        secureCount));
                bw.flush();

                // If averaged result reports insecure for this f, stop the sweep early
                if (!anySecureInThisF) {
                    System.out.println(String.format(Locale.ROOT,
                            "Averaged decision: no secure result for f=%d (n=%d). Stopping further f values to save time.",
                            f, n));
                    break;
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
