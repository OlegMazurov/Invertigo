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

public class DoubleInverse {

    static abstract class Matrix {
        long seed;
        int  n;

        Matrix(long seed, int n) {
            this.seed = Math.abs(seed) + 1;
            this.n = n;
        }

        public abstract double[][] getMatrix();
        public abstract double[]   getRow(int row);

        static void invert(double[][] A) {
            int[] perm = new int[A.length];
            for (int k = 0; k < A.length; ++k) {

                // Find the largest element in the base row
                double maxAbs = -1.;
                double[] baseRow = A[k];
                for (int c = k; c < A.length; ++c) {
                    if (maxAbs < Math.abs(baseRow[c])) {
                        maxAbs = Math.abs(baseRow[c]);
                        perm[k] = c;
                    }
                }

                // Process the base row
                int colIdx = perm[k];
                double m = 1. / baseRow[colIdx];
                baseRow[colIdx] = baseRow[k];
                baseRow[k] = 1.;
                for (int c = 0; c < baseRow.length; ++c) {
                    baseRow[c] *= m;
                }

                // Update other rows
                for (int r = 0; r < A.length; ++r) {
                    if (r == k) continue;
                    double[] curRow = A[r];
                    m = curRow[colIdx];
                    curRow[colIdx] = curRow[k];
                    curRow[k] = 0.;
                    for (int c = 0; c < curRow.length; ++c) {
                        curRow[c] -= baseRow[c] * m;
                    }
                }
            }

            // Apply the permutation to matrix rows
            for (int r = perm.length - 1; r >= 0; --r) {
                if (perm[r] != r) {
                    double[] t = A[r];
                    A[r] = A[perm[r]];
                    A[perm[r]] = t;
                }
            }
        }

        public double checkInverted(double[][] A) {
            double[] errs = new double[n];
            int par = Runtime.getRuntime().availableProcessors();
            ThreadPoolExecutor executor = new ThreadPoolExecutor(par, par,
                    Long.MAX_VALUE, TimeUnit.NANOSECONDS,
                    new LinkedBlockingDeque<>(n));

            for (int r = 0; r < n; ++r) {
                final int rr = r;
                executor.submit(() -> {
                    double[] row = getRow(rr);
                    double maxErr = -1.;
                    for (int c = 0; c < n; ++c) {
                        double sum = 0.;
                        for (int k = 0; k < n; ++k) {
                            sum += row[k] * A[k][c];
                        }
                        if (c == rr) sum -= 1.;
                        maxErr = Math.max(maxErr, Math.abs(sum));
                    }
                    errs[rr] = maxErr;
                });
            }
            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            }
            catch (InterruptedException ie) {}

            double res = -1.;
            for (double err : errs) res = Math.max(res, Math.abs(err));
            return res;
        }
    }

    static class RandomMatrix extends Matrix {

        RandomMatrix(long seed, int n) {
            super(seed, n);
        }

        public double[][] getMatrix() {
            double[][] A = new double[n][];
            for (int r = 0; r < n; ++r) {
                A[r] = getRow(r);
            }
            return A;
        }

        public double[] getRow(int r) {
            double[] row = new double[n];
            Random rnd = new Random(seed + r);
            for (int c = 0; c < n; ++c) {
                row[c] = rnd.nextDouble();
            }
            return row;
        }
    }

    static class RandomSingularMatrix extends Matrix {
        double[] lastRow;

        RandomSingularMatrix(long seed, int n) {
            super(seed, n);
            lastRow = new double[n];
        }

        public double[][] getMatrix() {
            double[][] A = new double[n][];
            for (int r = 0; r < n - 1; ++r) {
                A[r] = getRow(r);
                for (int j = 0; j < n; ++j) {
                    lastRow[j] += A[r][j] * r;
                }
            }
            A[n-1] = lastRow;
            return A;
        }

        public double[] getRow(int r) {
            if (r == n - 1) return lastRow;
            double[] row = new double[n];
            Random rnd = new Random(seed + r);
            for (int c = 0; c < n; ++c) {
                row[c] = rnd.nextDouble();
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

        public double[][] getMatrix() {
            double[][] A = new double[n][];
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

        public double[] getRow(int r) {
            double[] row = new double[n];
            row[perm[r]] = 1.;
            return row;
        }
    }

    static void usage() {
        System.out.println("Usage: java -cp Invertigo.jar " + DoubleInverse.class.getName() + " [-s seed] [-check] [-SINGULAR] [-PERM] [size]");
        System.exit(1);
    }

    public static void main(String[] args) {
        int n = 1024;
        boolean check = false;
        long seed = System.currentTimeMillis() % 1000000l;
        boolean singular = false;
        boolean permutation = false;

        for (int i = 0; i< args.length; ++i) {
            String arg = args[i];
            if (arg.length() == 0) usage();
            if (arg.charAt(0) == '-') {
                switch (args[i]) {
                    case "-c":
                        check = true;
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
        double[][] A = matrix.getMatrix();

        long start = System.currentTimeMillis();
        matrix.invert(A);
        long end = System.currentTimeMillis();

        double score = 1000. * n * n * n / (end - start);
        System.out.println(" n: " + n + "  seed: " + seed + "  time: " + (end - start) + " ms  score: " + (long)score + " ops/sec");

        if (check) {
            double res = matrix.checkInverted(A);
            System.out.println("max abs(error): " + res + (res < 1e-7 ? " OK" : " FAIL") + " time: " + (System.currentTimeMillis() - end) + " ms");
        }
    }
}
