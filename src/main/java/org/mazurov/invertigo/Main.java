/*
 * Copyright 2019 Oleg Mazurov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mazurov.invertigo;

import java.util.Random;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Main {

    static abstract class Matrix {
        long seed;
        int  n;

        Matrix(long seed, int n) {
            this.seed = Math.abs(seed) + 1;
            this.n = n;
        }

        public abstract long[][] getMatrix();
        public abstract long[]   getRow(int row);

        public boolean checkInverted(long[][] A) {
            boolean[] status = new boolean[n];
            int par = Runtime.getRuntime().availableProcessors();
            ThreadPoolExecutor executor = new ThreadPoolExecutor(par, par,
                    Long.MAX_VALUE, TimeUnit.NANOSECONDS,
                    new LinkedBlockingDeque<>(n));

            for (int r = 0; r < n; ++r) {
                final int rr = r;
                executor.submit(() -> {
                    long[] row = getRow(rr);
                    for (int c = 0; c < n; ++c) {
                        long sum = GF.ZERO;
                        for (int k = 0; k < n; ++k) {
                            sum = GF.add(sum, GF.mul(row[k], A[k][c]));
                        }
                        if (c == rr) sum = GF.sub(sum, GF.UNIT);
                        if (sum != GF.ZERO) return;
                    }
                    status[rr] = true;
                });
            }
            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            }
            catch (InterruptedException ie) {}

            boolean res = true;
            for (boolean b : status) res &= b;
            return res;
        }
    }

    static class RandomMatrix extends Matrix {

        RandomMatrix(long seed, int n) {
            super(seed, n);
        }

        public long[][] getMatrix() {
            long[][] A = new long[n][];
            for (int r = 0; r < n; ++r) {
                A[r] = getRow(r);
            }
            return A;
        }

        public long[] getRow(int r) {
            long[] row = new long[n];
            Random rnd = new Random(seed + r);
            for (int c = 0; c < n; ++c) {
                row[c] = rnd.nextLong() & (GF.cardinality() - 1);
            }
            return row;
        }
    }

    static class RandomSingularMatrix extends Matrix {
        long[] lastRow;

        RandomSingularMatrix(long seed, int n) {
            super(seed, n);
            lastRow = new long[n];
        }

        public long[][] getMatrix() {
            long[][] A = new long[n][];
            for (int r = 0; r < n - 1; ++r) {
                A[r] = getRow(r);
                for (int j = 0; j < n; ++j) {
                    lastRow[j] ^= GF.mul(A[r][j], r);
                }
            }
            A[n-1] = lastRow;
            return A;
        }

        public long[] getRow(int r) {
            if (r == n - 1) return lastRow;
            long[] row = new long[n];
            Random rnd = new Random(seed + r);
            for (int c = 0; c < n; ++c) {
                row[c] = rnd.nextLong() & (GF.cardinality() - 1);
            }
            return row;
        }
    }

    static class PermutationMatrix extends Matrix {
        int[] perm;

        PermutationMatrix(long seed, int n) {
            super(seed, n);
            perm = new int[n];
        }

        public long[][] getMatrix() {
            long[][] A = new long[n][];
            for (int i = 0; i < n; ++i) perm[i] = i;
            Random rnd = new Random(seed);
            for (int i = 1; i < n; ++i) {
                int j = rnd.nextInt(i);
                int t = perm[i];
                perm[i] = perm[j];
                perm[j] = t;
            }

            for (int r = 0; r < n ; ++r) {
                A[r] = getRow(r);
            }
            return A;
        }

        public long[] getRow(int r) {
            long[] row = new long[n];
            row[perm[r]] = GF.UNIT;
            return row;
        }
    }

    static void usage() {
        System.out.println("Usage: java -jar MatrixInverse.jar [-s seed] [-p parallelism] [-check] [-SINGULAR] [-PERM] [-NOWAIT] [size]");
        System.exit(1);
    }

    public static void main(String[] args) {
        int n = 1024;
        int maxPar = Runtime.getRuntime().availableProcessors();
        int nThreads = maxPar;
        boolean check = false;
        long seed = System.currentTimeMillis() % 1000000l;
        boolean singular = false;
        boolean permutation = false;
        boolean nowait = false;

        for (int i = 0; i< args.length; ++i) {
            String arg = args[i];
            if (arg.length() == 0) usage();
            if (arg.charAt(0) == '-') {
                switch (args[i]) {
                    case "-c":
                        check = true;
                        break;
                    case "-p":
                        if (++i == args.length) usage();
                        nThreads = Integer.parseInt(args[i]);
                        break;
                    case "-s":
                        if (++i == args.length) usage();
                        seed = Long.parseLong(args[i]);
                        break;
                    case "-SINGULAR":
                        singular = true;
                        break;
                    case "-PERM":
                        permutation = true;
                        break;
                    case "-NOWAIT":
                        nowait = true;
                        break;
                    default:
                        usage();
                        break;
                }
            }
            else {
                n = Integer.parseInt(args[i]);
                break;
            }
        }

        Matrix matrix;
        if (singular) matrix = new RandomSingularMatrix(seed, n);
        else if (permutation) matrix = new PermutationMatrix(seed, n);
        else matrix = new RandomMatrix(seed, n);
        long[][] A = matrix.getMatrix();

        long start = System.currentTimeMillis();
        if (nThreads <= 0) {
            System.out.print("SerialInverse:");
            SerialInverse.invert(A);
        }
        else {
            if (nowait) {
                System.out.print("NoWaitInverse: threads: " + nThreads);
                new NoWaitInverse(nThreads).invert(A);
            }
            else {
                System.out.print("ParallelInverse: threads: " + nThreads);
                new ParallelInverse(nThreads).invert(A);
            }
        }
        long end = System.currentTimeMillis();

        double score = 1000. * n * n * n / (end - start);
        System.out.println(" n: " + n + "  seed: " + seed + "  time: " + (end - start) + " ms  score: " + (long)score + " ops/sec");

        if (check) {
            boolean res = matrix.checkInverted(A);
            System.out.println("check: " + (res ? "OK" : "FAIL") + " time: " + (System.currentTimeMillis() - end) + " ms");
        }
    }
}
