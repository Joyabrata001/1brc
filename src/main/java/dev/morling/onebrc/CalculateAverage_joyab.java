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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.TreeMap;

// Time taken: ~1 - 2 mins
// Allocated classes drops from approx 75 to 2
// GC pressure reduces

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

    private static class StationKey {
        byte[] bytes;
        int offset;
        int len;
        int hash;

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof StationKey other)) return false;
            if (this.len != other.len) return false;

            for (int i = 0; i < len; i++) {
                if (this.bytes[offset + i] != other.bytes[other.offset + i]) return false;
            }
            return true;
        }

        public StationKey(byte[] bytes, int offset, int len, int hash) {
            this.bytes = bytes;
            this.offset = offset;
            this.len = len;
            this.hash = hash;
        }
    }

    private static class MeasurementAggregator {
        private long min = Long.MAX_VALUE;
        private long max = Long.MIN_VALUE;
        private long sum;
        private long count;
    }

    public static void main(String[] args) throws IOException {
        long startTime = System.nanoTime();

        HashMap<StationKey, MeasurementAggregator> mpp = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            byte[] b = new byte[BUFFERSIZE];
            int carryOver = 0;
            int bytesRead;

            StationKey lookupKey = new StationKey(b, -1, -1, -1);
            lookupKey.bytes = b;

            while ((bytesRead = fis.read(b, carryOver, BUFFERSIZE - carryOver)) != -1) {
                int totBytesRead = bytesRead + carryOver;

                // Find idx of last complete record (last NEWLINE)
                int lastNewLine = totBytesRead - 1;
                while (lastNewLine >= 0 && b[lastNewLine] != NEWLINE) --lastNewLine;

                int i = 0;

                while (i <= lastNewLine) {
                    // Parse station
                    int hash = 0;
                    int stationStartIdx = i;
                    while (b[i] != SEMICOLON) {
                        hash = 31 * hash + b[i];
                        ++i;
                    }

                    lookupKey.offset = stationStartIdx;
                    lookupKey.len = i - stationStartIdx;
                    lookupKey.hash = hash;

                    MeasurementAggregator agg = mpp.get(lookupKey);

                    if (agg == null) {
                        byte[] stationCopy = new byte[i - stationStartIdx];
                        System.arraycopy(b, stationStartIdx, stationCopy, 0, i - stationStartIdx);
                        StationKey storedKey = new StationKey(stationCopy, 0, i - stationStartIdx, hash);

                        agg = new MeasurementAggregator();
                        mpp.put(storedKey, agg);
                    }

                    ++i;    // Skip SEMICOLON

                    // Parse temperature
                    int temp = 0;
                    if (b[i] == MINUS) {
                        if (b[i + 2] == PERIOD) {
                            // -x.x
                            temp = -((b[i + 1] - ZERO) * 10 + (b[i + 3] - ZERO));

                            i += 5;
                        } else {
                            // -xx.x
                            temp = -((b[i + 1] - ZERO) * 100 + (b[i + 2] - ZERO) * 10 + (b[i + 4] - ZERO));

                            i += 6;
                        }
                    } else {
                        if (b[i + 1] == PERIOD) {
                            // x.x
                            temp = (b[i] - ZERO) * 10 + (b[i + 2] - ZERO);

                            i += 4;
                        } else {
                            // xx.x
                            temp = (b[i] - ZERO) * 100 + (b[i + 1] - ZERO) * 10 + (b[i + 3] - ZERO);

                            i += 5;
                        }
                    }

                    // Aggregate
                    if (temp < agg.min) agg.min = temp;
                    if (temp > agg.max) agg.max = temp;
                    agg.sum += temp;
                    ++agg.count;
                }

                // Recalculate carryOver and make copy of leftover
                carryOver = totBytesRead - (lastNewLine + 1);
                if (carryOver > 0) System.arraycopy(b, lastNewLine + 1, b, 0, carryOver);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        TreeMap<String, ResultRow> measurements = new TreeMap<>();
        mpp.forEach((key, agg) -> {
            double mean = (double) agg.sum / agg.count;
            String station = new String(key.bytes, key.offset, key.len, StandardCharsets.UTF_8);
            measurements.put(station, new ResultRow(agg.min, mean, agg.max));
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
