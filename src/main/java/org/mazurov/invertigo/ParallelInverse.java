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

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;

public class ParallelInverse {
    private int nThreads;
    private CyclicBarrier barrier;
    private volatile RuntimeException error;
    private AtomicLong count;
    private long[][] A;
    private int[] perm;

    public ParallelInverse(int par) {
        nThreads = par;
    }

    private void processBaseRow(int k) {
        // Find a non-zero element in the base row
        perm[k] = k;
        long[] baseRow = A[k];
        for (int c = k; c < A.length; ++c) {
            if (baseRow[c] != GF.ZERO) {
                perm[k] = c;
                break;
            }
        }

        // Process the base row
        int colIdx = perm[k];
        long m = GF.rev(baseRow[colIdx]);
        baseRow[colIdx] = baseRow[k];
        baseRow[k] = GF.UNIT;
        for (int c = 0; c < baseRow.length; ++c) {
            baseRow[c] = GF.mul(baseRow[c], m);
        }
    }

    private void runDynamicSchedule(int id) {
        try {
            if (id == 0) {
                processBaseRow(0);
            }
            int step = -1;
            while (error == null) {
                long task = count.getAndIncrement();
                int k = (int) (task / A.length);
                int l = (int) (task % A.length);
                if (k != step) {
                    if (k == A.length) return;
                    barrier.await();
                    step = k;
                }

                // Update row
                int r = (k + l + 1) % A.length;
                if (r == k) continue;
                long[] baseRow = A[k];
                int colIdx = perm[k];
                long[] curRow = A[r];
                long m = curRow[colIdx];
                curRow[colIdx] = curRow[k];
                curRow[k] = GF.ZERO;
                for (int c = 0; c < curRow.length; ++c) {
                    curRow[c] = GF.sub(curRow[c], GF.mul(baseRow[c], m));
                }

                if (r == k + 1) processBaseRow(r);
            }
        }
        catch (RuntimeException t) {
            if (error == null) {
                error = t;
                // Notify other threads waiting on the barrier
                Thread.currentThread().interrupt();
                try {
                    barrier.await();
                }
                catch (Exception ex) {}
            }
        }
        catch (Exception ex) {}
    }

    public void invert(long[][] a) {
        A = a;
        perm = new int[A.length];

        count = new AtomicLong(0);
        barrier = new CyclicBarrier(nThreads);

        Thread[] threads = new Thread[nThreads];
        for (int t = 0; t < threads.length; ++t) {
            final int id = t;
            Thread thread = new Thread(() -> runDynamicSchedule(id));
            threads[t] = thread;
            thread.start();
        }

        try {
            for (Thread thread : threads) {
                thread.join();
            }
        }
        catch (InterruptedException ie) {
            ie.printStackTrace();
        }
        if (error != null) {
            throw error;
        }

        for (int r = perm.length - 1; r >= 0; --r) {
            if (perm[r] != r) {
                long[] t = A[r];
                A[r] = A[perm[r]];
                A[perm[r]] = t;
            }
        }
    }
}
