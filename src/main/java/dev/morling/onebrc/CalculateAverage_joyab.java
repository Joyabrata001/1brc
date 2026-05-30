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
import java.util.TreeMap;

public class CalculateAverage_joyab {

    private static final String FILE = "./measurements.txt";
    private static final Path path = Paths.get(FILE);

    private static final int MINUS = 45;
    private static final int PERIOD = 46;
    private static final int ZERO = 48;
    private static final int SEMICOLON = 59;
    private static final int BUFFERSIZE = 1 << 21;
    private static final int NEWLINE = 10;

    private static final int TABLE_SIZE = 1 << 10;
    private static final int MASK = TABLE_SIZE - 1;

    private record ResultRow(long min, double mean, long max) {
        public String toString() {
            return (min / 10.0) + "/" + (Math.round(mean) / 10.0) + "/" + (max / 10.0);
        }
    }

    private static final class Station {
        byte[] name;
        int len;
        int hash;

        long min;
        long max;
        long sum;
        long count;
    }

    private static final class CustomHashMap {
        static Station[] table = new Station[TABLE_SIZE];

        // For stats
        static long collisionCount = 0;
        static long lookupCount = 0;
        static long maxProbeLength = 0;
        static long totalProbeLength = 0;

        public static boolean stationEquals(byte[] stored, byte[] curr, int offset, int len) {
            if (stored.length != len) return false;

            for (int i = 0; i < len; i++) {
                if (stored[i] != curr[offset + i]) return false;
            }

            return true;
        }

        public static Station findOrCreate(byte[] buffer, int offset, int len, int hash) {
            ++lookupCount;

            int idx = hash & MASK;
            long probeLength = 0;

            while (true) {
                Station s = table[idx];

                if (s == null) {
                    byte[] name = new byte[len];
                    System.arraycopy(buffer, offset, name, 0, len);

                    s = new Station();

                    s.name = name;
                    s.len = len;
                    s.hash = hash;

                    s.min = Long.MAX_VALUE;
                    s.max = Long.MIN_VALUE;

                    table[idx] = s;

                    totalProbeLength += probeLength;
                    if (probeLength > maxProbeLength) maxProbeLength = probeLength;

                    return s;
                }

                if (s.hash == hash && stationEquals(s.name, buffer, offset, len)) {
                    totalProbeLength += probeLength;
                    if (probeLength > maxProbeLength) maxProbeLength = probeLength;

                    return s;
                }

                idx = (idx + 1) & MASK;

                ++probeLength;
                ++collisionCount;
            }
        }

        public static int usedBuckets() {
            int used = 0;

            for (Station station : table) {
                if (station != null) used++;
            }

            return used;
        }
    }

    public static void main(String[] args) throws IOException {
        long startTime = System.nanoTime();

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
                    int hash = 0;
                    int stationStartIdx = i;
                    while (b[i] != SEMICOLON) {
                        hash = 31 * hash + b[i];
                        ++i;
                    }

                    Station station = CustomHashMap.findOrCreate(b, stationStartIdx, i - stationStartIdx, hash);

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
                    if (temp < station.min) station.min = temp;
                    if (temp > station.max) station.max = temp;
                    station.sum += temp;
                    ++station.count;
                }

                // Recalculate carryOver and make copy of leftover
                carryOver = totBytesRead - (lastNewLine + 1);
                if (carryOver > 0) System.arraycopy(b, lastNewLine + 1, b, 0, carryOver);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        TreeMap<String, ResultRow> measurements = new TreeMap<>();
        for (Station station : CustomHashMap.table) {
            if (station == null) continue;

            double mean = (double) station.sum / station.count;
            measurements.put(new String(station.name, StandardCharsets.UTF_8), new ResultRow(station.min, mean, station.max));
        }

        long endTime = System.nanoTime();

        System.out.println(measurements);

        long durationNs = (endTime - startTime);
        long totalSeconds = durationNs / 1_000_000_000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = (durationNs / 1_000_000) % 1000;

        System.out.printf("Logic only: %d min %d sec %d ms%n", minutes, seconds, millis);

        int usedBuckets = CustomHashMap.usedBuckets();

        System.out.println("----- Hash Table Stats -----");
        System.out.printf("Table Size      : %d\n", TABLE_SIZE);
        System.out.printf("Used Buckets    : %d\n", usedBuckets);
        System.out.printf("Load Factor     : %f\n", ((double) usedBuckets / TABLE_SIZE));
        System.out.printf("Lookups         : %d\n", CustomHashMap.lookupCount);
        System.out.printf("Collisions      : %d\n", CustomHashMap.collisionCount);
        System.out.printf("Max Probe Len   : %d\n", CustomHashMap.maxProbeLength);
        System.out.printf("Avg Probe Len   : %f\n", ((double) CustomHashMap.totalProbeLength / CustomHashMap.lookupCount));
    }
}

//  ----- Hash Table Stats -----
//  Table Size      : 1024
//  Used Buckets    : 413
//  Load Factor     : 0.403320
//  Lookups         : 1000000000
//  Collisions      : 355907879
//  Max Probe Len   : 8
//  Avg Probe Len   : 0.355908
//
//  === EXECUTION FINISHED ===
//  Time Taken: 216.786 seconds / 89.392 seconds / 89.578 seconds

// ----- Hash Table Stats -----
// Table Size      : 4096
// Used Buckets    : 413
// Load Factor     : 0.100830
// Lookups         : 1000000000
// Collisions      : 36314025
// Max Probe Len   : 2
// Avg Probe Len   : 0.036314
//
// === EXECUTION FINISHED ===
// Time Taken: 83.777 seconds

// ----- Hash Table Stats -----
// Table Size      : 16384
// Used Buckets    : 413
// Load Factor     : 0.025208
// Lookups         : 1000000000
// Collisions      : 12105654
// Max Probe Len   : 1
// Avg Probe Len   : 0.012106
//
// === EXECUTION FINISHED ===
// Time Taken: 86.812 seconds

// ----- Hash Table Stats -----
// Table Size      : 65536
// Used Buckets    : 413
// Load Factor     : 0.006302
// Lookups         : 1000000000
// Collisions      : 4843108
// Max Probe Len   : 1
// Avg Probe Len   : 0.004843
//
// === EXECUTION FINISHED ===
// Time Taken: 68.398 seconds / 51.373 seconds

// ----- Hash Table Stats -----
// Table Size      : 131072
// Used Buckets    : 413
// Load Factor     : 0.003151
// Lookups         : 1000000000
// Collisions      : 0
// Max Probe Len   : 0
// Avg Probe Len   : 0.000000
//
// === EXECUTION FINISHED ===
// Time Taken: 84.596 seconds