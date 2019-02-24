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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class NoWaitInverse {

    private int nThreads;
    private AbstractExecutorService executor;
    private CountDownLatch finished;
    private RuntimeException exception;
    private long[][] A;
    private int[] perm;

    public NoWaitInverse(int par) {
        nThreads = par;
    }

    class Task implements Runnable {

        int             idx;
        int             step;
        AtomicInteger   state;
        Task            baseTask, nextTask;
        Task[]          curTasks;

        Task(int i, Task[] tasks) {
            idx = i;
            step = 0;
            curTasks = tasks;
            state = new AtomicInteger(1);
        }

        Task(Task prev, Task[] tasks) {
            idx = prev.idx;
            step = prev.step + 1;
            curTasks = tasks;
            state = new AtomicInteger(prev.step == idx ? A.length : step == idx ? 1 : 2);
        }

        void send() {
            if (state.decrementAndGet() == 0) {
                executor.execute(this);
            }
        }

        public void run() {
            long[] curRow = A[idx];
            boolean lastTask = step + 1 == A.length;
            if (step == idx) {
                // Compute
                perm[idx] = idx;
                for (int c = idx; c < A.length; ++c) {
                    if (curRow[c] != GF.ZERO) {
                        perm[idx] = c;
                        break;
                    }
                }

                int colIdx = perm[idx];
                long m = GF.rev(curRow[colIdx]);
                curRow[colIdx] = curRow[idx];
                curRow[idx] = GF.UNIT;
                for (int c = 0; c < curRow.length; ++c) {
                    curRow[c] = GF.mul(curRow[c], m);
                }

                // Notify
                Task base = this;
                if (!lastTask) {
                    Task[] next = new Task[A.length];
                    for (int i = 0; i < next.length; ++i) {
                        next[i] = new Task(curTasks[i], next);
                        curTasks[i].nextTask = next[i];
                    }
                    base = next[idx];
                }
                for (Task t : curTasks) {
                    if (t != this) {
                        t.baseTask = base;
                        t.send();
                    }
                }
            }
            else {
                // Compute
                int colIdx = perm[step];
                long m = curRow[colIdx];
                curRow[colIdx] = curRow[step];
                curRow[step] = GF.ZERO;
                long[] baseRow = A[baseTask.idx];
                for (int c = 0; c < curRow.length; ++c) {
                    curRow[c] = GF.sub(curRow[c], GF.mul(baseRow[c], m));
                }

                // Notify
                if (!lastTask) {
                    baseTask.send();
                    nextTask.send();
                }
            }

            if (lastTask) finished.countDown();
        }
    }

    public void invert(long[][] a) {
        A = a;
        finished = new CountDownLatch(A.length);
        perm = new int[A.length];

        executor = new ForkJoinPool(
                nThreads,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                (t, e) -> {
                    exception = new RuntimeException("ERROR", e);
                    while (finished.getCount() > 0) finished.countDown();
                },
                false);

        Task[] tasks = new Task[A.length];
        for (int i = 0; i < A.length; ++i) {
            tasks[i] = new Task(i, tasks);
        }
        executor.submit(tasks[0]);
        tasks = null;

        try {
            finished.await();
        }
        catch (InterruptedException ex) {
            exception = new RuntimeException("INTERRUPTED", ex);
        }
        executor.shutdown();
        if (exception != null) throw exception;

        for (int r = perm.length - 1; r >= 0; --r) {
            if (perm[r] != r) {
                long[] t = A[r];
                A[r] = A[perm[r]];
                A[perm[r]] = t;
            }
        }
    }
}
