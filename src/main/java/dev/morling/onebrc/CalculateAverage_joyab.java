/*
 *  Copyright 2023 The original authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package dev.morling.onebrc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

public class CalculateAverage_joyab {

    private static final String FILE = "./measurements.txt";
    private static final Path path = Paths.get(FILE);

    public static final int MINUS = 45;
    public static final int PERIOD = 46;
    public static final int ZERO = 48;
    public static final int SEMICOLON = 59;

    private static int parseTemperature(String str) {
        boolean isNeg = str.charAt(0) == MINUS;
        int temp = 0, n = str.length();
        for (int i = isNeg ? 1 : 0; i < n; i++) {
            char ch = str.charAt(i);

            if (ch != PERIOD)
                temp = temp * 10 + ch - ZERO;
        }
        return isNeg ? -temp : temp;
    }

    private record Measurement(String station, int value) {
        public static Measurement of(String l) {
            int indexOfSemicolon = l.indexOf(SEMICOLON);
            return new Measurement(l.substring(0, indexOfSemicolon), parseTemperature(l.substring(indexOfSemicolon + 1)));
        }
    }

    private record ResultRow(long min, double mean, long max) {
        public String toString() {
            return (min / 10.0) + "/" + (Math.round(mean) / 10.0) + "/" + (max / 10.0);
        }
    }

    private static class MeasurementAggregator {
        private long min = Integer.MAX_VALUE;
        private long max = Integer.MIN_VALUE;
        private long sum;
        private long count;
    }

    public static void main(String[] args) throws IOException {
        Collector<Measurement, MeasurementAggregator, ResultRow> collector = Collector.of(
                MeasurementAggregator::new,
                (a, m) -> {
                    a.min = Math.min(a.min, m.value);
                    a.max = Math.max(a.max, m.value);
                    a.sum += m.value;
                    a.count++;
                },
                (agg1, agg2) -> {
                    var res = new MeasurementAggregator();
                    res.min = Math.min(agg1.min, agg2.min);
                    res.max = Math.max(agg1.max, agg2.max);
                    res.sum = agg1.sum + agg2.sum;
                    res.count = agg1.count + agg2.count;

                    return res;
                },
                agg -> {
                    return new ResultRow(agg.min, (double) agg.sum / agg.count, agg.max);
                });

        HashMap<String, ResultRow> measurements;

        long startTime = System.nanoTime();

        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            measurements = lines
                    .map(Measurement::of)
                    // creates a Map: key = station, value = result of collector
                    .collect(groupingBy(Measurement::station, HashMap::new, collector));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        long endTime = System.nanoTime();

        TreeMap<String, ResultRow> measurements1 = new TreeMap<>(measurements);
        System.out.println(measurements1);

        long durationNs = (endTime - startTime);
        long totalSeconds = durationNs / 1_000_000_000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = (durationNs / 1_000_000) % 1000;

        System.out.printf("Logic only: %d min %d sec %d ms%n", minutes, seconds, millis);
    }
}
