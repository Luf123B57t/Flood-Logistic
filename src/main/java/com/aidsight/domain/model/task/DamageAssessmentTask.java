package com.aidsight.domain.model.task;

import com.aidsight.domain.model.analysis.ChartData;
import com.aidsight.domain.model.core.Task;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.LocalDate;
import java.util.Map;

public class DamageAssessmentTask extends Task {

    public static class Result implements Task.Result<Result> {
        public static class DamageCount {
            public enum DamageType {
                DEATHS,
                INJURIES,
                MISSING,
                DAMAGED_HOUSES,
                FLOODED_HOUSES,
                RICE,
                CROPS,
                FRUIT_TREES,
                AQUACULTURE_CAGES,
                CATTLE_DEATHS,
                POULTRY_DEATHS,
                ELECTRIC_POLES
            }

            static final String[] DAMAGE_TYPE_NAMES = {
                "Deaths",
                "Injuries",
                "Missing Persons",
                "Damaged Houses",
                "Flooded Houses",
                "Rice",
                "Crops",
                "Fruit Trees",
                "Aquaculture Cages",
                "Cattle Deaths",
                "Poultry Deaths",
                "Electric Poles"
            };

            int[] counts = new int[DamageType.values().length];

            void increment(DamageType type, int amount) {
                counts[type.ordinal()] += amount;
            }

            void merge(DamageCount other) {
                for (int i = 0; i < counts.length; i++) {
                    this.counts[i] += other.counts[i];
                }
            }
        }
        Map<LocalDate, DamageCount> damageCounts = new HashMap<>();

        public void increment(LocalDate date, DamageCount.DamageType type, int amount) {
            damageCounts.computeIfAbsent(date, _ -> new DamageCount()).increment(type, amount);
        }

        private ChartData createChartDataFromDamageTypes(String title, String yAxisLabel, DamageCount.DamageType... types) {
            // Sort dates in non-decreasing order
            List<LocalDate> sortedDates = new ArrayList<>(damageCounts.keySet());
            sortedDates.sort(LocalDate::compareTo);

            // Prepare series infrastructure for each damage type - use LinkedHashMap to preserve order
            // Calculate cumulative sums: each day's value is the sum of all previous days (including current day)
            Map<String, List<Integer>> seriesData = new LinkedHashMap<>();
            for (DamageCount.DamageType type : types) {
                List<Integer> values = new ArrayList<>();
                int cumulativeSum = 0;
                for (LocalDate date : sortedDates) {
                    DamageCount damageCount = damageCounts.get(date);
                    if (damageCount != null) {
                        cumulativeSum += damageCount.counts[type.ordinal()];
                    }
                    values.add(cumulativeSum);
                }
                seriesData.put(DamageCount.DAMAGE_TYPE_NAMES[type.ordinal()], values);
            }


            // Create and return ChartData using Builder pattern
            return new ChartData.Builder(title)
                .xAxisLabel("Date")
                .yAxisLabel(yAxisLabel)
                .xAxisData(sortedDates)  // Directly use LocalDate list
                .addSeries(seriesData)    // Directly add all series infrastructure at once
                .chartType(ChartData.ChartType.LINE)     // Set chart type
                .build();
        }


        /**
         * Merges another Result into this one by combining damage counts for each date.
         *
         * @param other the other result to merge from
         */
        @Override
        public void merge(Result other) {
            for (Map.Entry<LocalDate, DamageCount> entry : other.damageCounts.entrySet()) {
                damageCounts.merge(entry.getKey(), entry.getValue(), (dc1, dc2) -> {
                    dc1.merge(dc2);
                    return dc1;
                });
            }
        }

        /**
         * Returns a text report of the analysis results.
         *
         * @return formatted text report
         */
        @Override
        public String getReport() {
            return "Task Two Analysis Report";
        }

        /**
         * Returns a list of ChartData objects for visualizing the damage assessment results.
         * <p>
         * Generates four charts:
         * 1. Deaths, Injuries, Missing Persons Over Time (LINE chart)
         * 2. Houses Damage Over Time (BAR chart)
         * 3. Crops Damage Over Time (LINE chart)
         * 4. Livestock Deaths Over Time (BAR chart)
         * </p>
         *
         * @return list of configured chart infrastructure objects
         */
        @Override
        public List<ChartData> getChartData() {
            List<ChartData> charts = new ArrayList<>();

            charts.add(createChartDataFromDamageTypes(
                "Chart 1",
                "Count",
                DamageCount.DamageType.DEATHS,
                DamageCount.DamageType.INJURIES,
                DamageCount.DamageType.MISSING
            ));

            charts.add(createChartDataFromDamageTypes(
                "Chart 2",
                "Count",
                DamageCount.DamageType.DAMAGED_HOUSES,
                DamageCount.DamageType.FLOODED_HOUSES
            ));

            charts.add(createChartDataFromDamageTypes(
                "Crops Damage Over Time",
                "Area (hectares)",
                DamageCount.DamageType.RICE,
                DamageCount.DamageType.CROPS,
                DamageCount.DamageType.FRUIT_TREES
            ));

            charts.add(createChartDataFromDamageTypes(
                "Aquaculture Damage Over Time",
                "Count",
                DamageCount.DamageType.AQUACULTURE_CAGES
            ));

            charts.add(createChartDataFromDamageTypes(
                "Livestock Deaths Over Time",
                "Count",
                DamageCount.DamageType.CATTLE_DEATHS,
                DamageCount.DamageType.POULTRY_DEATHS
            ));

            charts.add(createChartDataFromDamageTypes(
                "Electric Poles Damaged Over Time",
                "Count",
                DamageCount.DamageType.ELECTRIC_POLES
            ));

            return charts;
        }
    }
}
