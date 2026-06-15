package com.aidsight.domain.model.task;

import com.aidsight.domain.model.analysis.ChartData;
import com.aidsight.domain.model.core.Task;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SentimentTrackerTask extends Task {

    public static class Result implements Task.Result<Result> {
        private Map<LocalDate, List<Float>> sentiment = new LinkedHashMap<>();

        public void addSentiment(LocalDate date, float value) {
            sentiment.computeIfAbsent(date, _ -> new ArrayList<>()).add(value);
        }

        @Override
        public void merge(Result other) {
            sentiment.putAll(other.sentiment);
        }

        @Override
        public String getReport() {
            return "Sentiment over time: " + sentiment.toString();
        }

        @Override
        public List<ChartData> getChartData() {
            List<LocalDate> dates = new ArrayList<>();
            List<Double> averageSentiments = new ArrayList<>();

            // Sort entries by date
            sentiment.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        dates.add(entry.getKey());

                        // Calculate average sentiment for this date
                        List<Float> values = entry.getValue();
                        double average = values.stream()
                                .mapToDouble(Float::doubleValue)
                                .average()
                                .orElse(0.0);

                        averageSentiments.add(average);
                    });

            ChartData chart = new ChartData.Builder("Sentiment Over Time")
                    .xAxisLabel("Date")
                    .yAxisLabel("Average Sentiment")
                    .xAxisData(dates)
                    .addSeries("Sentiment", averageSentiments)
                    .build();

            return List.of(chart);
        }
    }
}
