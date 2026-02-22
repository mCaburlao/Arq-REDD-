package jabs.analysis;

import jabs.metrics.SimulationMetrics;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Analyzes simulation metrics and generates comparative reports
 * Supports all 5 metrics across multiple protocols and scenarios
 */
public class MetricsAnalyzer {
    private String outputDirectory;
    private Map<String, SimulationMetrics> protocolMetrics;
    
    public MetricsAnalyzer(String outputDirectory) {
        this.outputDirectory = outputDirectory;
        this.protocolMetrics = new TreeMap<>();
    }
    
    /**
     * Load metrics from finalization log files
     * Parses finalization logs and calculates all 5 metrics
     */
    public void loadMetricsFromLogs(String protocolName, Path logDirectory) throws IOException {
        SimulationMetrics metrics = new SimulationMetrics();
        
        // Read all CSV files in the directory
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logDirectory, "*.csv")) {
            for (Path filePath : stream) {
                parseFinalizationLog(filePath, metrics);
            }
        }
        
        protocolMetrics.put(protocolName, metrics);
    }
    
    /**
     * Parse a single finalization log file
     * Expected format: timestamp, nodeid, blockheight, blockhash, blocksize, 
     *                  blockcreationtime, blockcreator, blockfinalizationtime, trafficuntilfinalization
     */
    private void parseFinalizationLog(Path logFile, SimulationMetrics metrics) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            String line;
            boolean skipHeader = true;
            
            while ((line = reader.readLine()) != null) {
                // Skip comments and header
                if (line.startsWith("#") || line.startsWith("Time") || skipHeader) {
                    skipHeader = false;
                    continue;
                }
                
                String[] parts = line.split(",");
                if (parts.length < 9) continue;
                
                try {
                    // Parse finalization time (column 8, in seconds)
                    double finalizationTime = Double.parseDouble(parts[7].trim());
                    metrics.recordBlockFinalizationTime(finalizationTime);
                    
                    // Parse traffic (column 9, in bytes)
                    long traffic = Long.parseLong(parts[8].trim());
                    metrics.recordBlockTraffic(traffic);
                    
                    // Record block as generated
                    metrics.recordBlockGenerated();
                    
                } catch (NumberFormatException e) {
                    // Skip malformed lines
                    continue;
                }
            }
        }
    }
    
    /**
     * Generate a comprehensive metrics comparison report
     */
    public void generateComparisonReport(String outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("========== CONSENSUS ALGORITHM METRICS COMPARISON ==========");
            writer.println("Date: " + new Date());
            writer.println();
            
            // Header
            writer.println(String.format("%-20s | Tb_Avg(s) | Tb_p95(s) | Cb_Avg(MB) | Bf(%%) | BFT(%%) | Pdv(%%)",
                "Protocol"));
            writer.println("-----".repeat(25));
            
            // Data rows sorted by protocol name
            for (Map.Entry<String, SimulationMetrics> entry : protocolMetrics.entrySet()) {
                String protocol = entry.getKey();
                SimulationMetrics metrics = entry.getValue();
                
                writer.println(String.format("%-20s | %9.3f | %8.3f | %10.6f | %5.2f | %6.2f | %6.2f",
                    protocol,
                    metrics.getAverageBlockFinalizationTime(),
                    metrics.getPercentileFinalizationTime(95),
                    metrics.getAverageTrafficPerBlock(),
                    metrics.getByzantineFaultTolerance(),
                    metrics.getDoubleSpendSuccessProbability()
                ));
            }
            
            writer.println();
            writer.println("========== DETAILED METRICS BY PROTOCOL ==========");
            writer.println();
            
            for (Map.Entry<String, SimulationMetrics> entry : protocolMetrics.entrySet()) {
                String protocol = entry.getKey();
                SimulationMetrics metrics = entry.getValue();
                writer.println(metrics.generateReport());
                writer.println();
            }
        }
    }
    
    /**
     * Generate CSV file with all metrics for statistical analysis
     */
    public void exportMetricsAsCSV(String csvFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {
            // CSV Header
            writer.println("Protocol,Tb_Avg_s,Tb_p50_s,Tb_p95_s,Tb_p99_s," +
                          "Cb_Avg_MB,Cb_p50_MB,Cb_p95_MB,Cb_p99_MB," +
                          "BFT_ByzantineTolerance_pct,ByzantineValidators,TotalValidators," +
                          "Pdv_DoubleSend_SuccessProbability_pct,SuccessfulAttacks,TotalAttacks");
            
            // Data rows
            for (Map.Entry<String, SimulationMetrics> entry : protocolMetrics.entrySet()) {
                String protocol = entry.getKey();
                SimulationMetrics metrics = entry.getValue();
                
                writer.println(String.format("%s,%.6f,%.6f,%.6f,%.6f," +
                                           "%.9f,%.9f,%.9f,%.9f," +
                                           "%.6f," +
                                           "%.6f,%d,%d," +
                                           "%.6f,%d,%d",
                    protocol,
                    metrics.getAverageBlockFinalizationTime(),
                    metrics.getPercentileFinalizationTime(50),
                    metrics.getPercentileFinalizationTime(95),
                    metrics.getPercentileFinalizationTime(99),
                    metrics.getAverageTrafficPerBlock(),
                    metrics.getPercentileTraffic(50),
                    metrics.getPercentileTraffic(95),
                    metrics.getPercentileTraffic(99),
                    metrics.getByzantineFaultTolerance(),
                    metrics.getByzantineValidators(),
                    metrics.getTotalValidators(),
                    metrics.getDoubleSpendSuccessProbability(),
                    metrics.getDoubleSpendSuccesses(),
                    metrics.getDoubleSpendAttempts()
                ));
            }
        }
    }
    
    /**
     * Get protocol ranking by specific metric
     */
    public List<String> rankProtocolsByMetric(String metricName) {
        List<Map.Entry<String, Double>> ranking = new ArrayList<>();
        
        for (Map.Entry<String, SimulationMetrics> entry : protocolMetrics.entrySet()) {
            String protocol = entry.getKey();
            SimulationMetrics metrics = entry.getValue();
            double value = 0;
            
            switch (metricName.toLowerCase()) {
                case "tb_avg":
                case "finalization_time":
                    value = metrics.getAverageBlockFinalizationTime();
                    break;
                case "cb_avg":
                case "traffic":
                    value = metrics.getAverageTrafficPerBlock();
                    break;
                case "bf":
                case "bft":
                case "byzantine_tolerance":
                    value = metrics.getByzantineFaultTolerance();
                    break;
                case "pdv":
                case "double_spend":
                    value = metrics.getDoubleSpendSuccessProbability();
                    break;
            }
            
            ranking.add(new AbstractMap.SimpleEntry<>(protocol, value));
        }
        
        // Sort by value (ascending for time/traffic/doublespend, descending for Byzantine tolerance)
        if (metricName.toLowerCase().contains("bft") || metricName.toLowerCase().contains("byzantine_tolerance")) {
            ranking.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // Higher is better
        } else {
            ranking.sort(Map.Entry.comparingByValue()); // Lower is better
        }
        
        List<String> result = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, Double> entry : ranking) {
            result.add(String.format("%d. %s: %.6f", rank++, entry.getKey(), entry.getValue()));
        }
        
        return result;
    }
    
    /**
     * Generate multi-criteria recommendation for REDD+ use case
     */
    public List<String> recommendForUseCase(Map<String, Double> weights) {
        Map<String, Double> scores = new TreeMap<>();
        
        for (Map.Entry<String, SimulationMetrics> entry : protocolMetrics.entrySet()) {
            String protocol = entry.getKey();
            SimulationMetrics metrics = entry.getValue();
            
            double score = 0;
            
            // Normalize and weight metrics (assuming scale 0-1000 for finalization time)
            if (weights.containsKey("finalization_time")) {
                // Lower is better: normalize with max=30000ms
                double normalized = 1.0 - Math.min(metrics.getAverageBlockFinalizationTime() * 1000 / 30000.0, 1.0);
                score += normalized * weights.get("finalization_time");
            }
            
            if (weights.containsKey("traffic")) {
                // Lower is better: normalize with max=10MB
                double normalized = 1.0 - Math.min(metrics.getAverageTrafficPerBlock() / 10.0, 1.0);
                score += normalized * weights.get("traffic");
            }
            
            if (weights.containsKey("byzantine_tolerance")) {
                // Higher is better: normalize with max=40%
                double normalized = Math.min(metrics.getByzantineFaultTolerance() / 40.0, 1.0);
                score += normalized * weights.get("byzantine_tolerance");
            }
            
            if (weights.containsKey("double_spend")) {
                // Lower is better: normalize with max=5%
                double normalized = 1.0 - Math.min(metrics.getDoubleSpendSuccessProbability() / 5.0, 1.0);
                score += normalized * weights.get("double_spend");
            }
            
            scores.put(protocol, score);
        }
        
        // Sort by score descending
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        
        List<String> result = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, Double> entry : sorted) {
            result.add(String.format("%d. %s: %.4f", rank++, entry.getKey(), entry.getValue()));
        }
        
        return result;
    }
}
