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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.TreeMap;

// New issues: Integer boxing
// Why Allocated Classes Score Worsened
// Allocation when using String as HashMap key: Single, predictable, making it favourable for JIT inlining
// Allocation when using CustomHash integer as HashMap key: Conditional, inside HashMap lookup, making harder to scalar-replace
//

public class CalculateAverage_joyab {

    private static final String FILE = "./measurements.txt";
    private static final Path path = Paths.get(FILE);

    public static final int MINUS = 45;
    public static final int PERIOD = 46;
    public static final int ZERO = 48;
    public static final int SEMICOLON = 59;
    public static final int BUFFERSIZE = 1 << 21;
    public static final int NEWLINE = 10;

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
        long startTime = System.nanoTime();

        HashMap<Integer, MeasurementAggregator> mpp1 = new HashMap<>();
        HashMap<Integer, String> mpp2 = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            byte[] b = new byte[BUFFERSIZE];
            int carryOver = 0;
            int bytesRead;

            while ((bytesRead = fis.read(b, carryOver, BUFFERSIZE - carryOver)) != -1) {
                int totBytesRead = bytesRead + carryOver;

                // Find idx of last complete record (last NEWLINE)
                int lastNewLine = totBytesRead - 1;
                while (lastNewLine >= 0 && b[lastNewLine] != NEWLINE) --lastNewLine;

                int i = 0;

                while (i <= lastNewLine) {
                    // Parse station
                    int station = 0;
                    int stationStartIdx = i;
                    while (b[i] != SEMICOLON) {
                        station = 31 * station + b[i];
                        ++i;
                    }

                    if (mpp2.get(station) == null)
                        mpp2.put(station, new String(b, stationStartIdx, i - stationStartIdx));

                    ++i;    // Skip SEMICOLON

                    // Parse temperature
                    boolean isNeg = (b[i] == MINUS);
                    i += isNeg ? 1 : 0;

                    int temp = 0;
                    while (b[i] != PERIOD) {
                        temp = temp * 10 + b[i] - ZERO;
                        ++i;
                    }

                    ++i;    // Skip PERIOD

                    temp = temp * 10 + b[i] - ZERO;

                    if (isNeg) temp = -temp;

                    // Aggregate
                    MeasurementAggregator agg = mpp1.get(station);

                    if (agg == null) {
                        agg = new MeasurementAggregator();
                        mpp1.put(station, agg);
                    }

                    agg.min = Math.min(agg.min, temp);
                    agg.max = Math.max(agg.max, temp);
                    agg.sum += temp;
                    ++agg.count;

                    // Move to newline
                    while (b[i] != NEWLINE) ++i;

                    ++i;    // Skip newline
                }

                // Recalculate carryOver and make copy of leftover
                carryOver = totBytesRead - (lastNewLine + 1);
                if (carryOver > 0) System.arraycopy(b, lastNewLine + 1, b, 0, carryOver);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        TreeMap<String, ResultRow> measurements = new TreeMap<>();
        mpp1.forEach((station, agg) -> {
            double mean = (double) agg.sum / agg.count;
            measurements.put(mpp2.get(station), new ResultRow(agg.min, mean, agg.max));
        });

        long endTime = System.nanoTime();

        System.out.println(measurements);

        long durationNs = (endTime - startTime);
        long totalSeconds = durationNs / 1_000_000_000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = (durationNs / 1_000_000) % 1000;

        System.out.printf("Logic only: %d min %d sec %d ms%n", minutes, seconds, millis);
    }
}
