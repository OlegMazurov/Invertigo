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

public class SerialInverse {

    /**
     * In-place matrix inversion
     * @param A input-output matrix
     * throws {@code IllegalArgumentException} for a singular matrix
     */
    static void invert(long[][] A) {
        int[] perm = new int[A.length];
        for (int k = 0; k < A.length; ++k) {

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

            // Update other rows
            for (int r = 0; r < A.length; ++r) {
                if (r == k) continue;
                long[] curRow = A[r];
                m = curRow[colIdx];
                curRow[colIdx] = curRow[k];
                curRow[k] = GF.ZERO;
                for (int c = 0; c < curRow.length; ++c) {
                    curRow[c] = GF.sub(curRow[c], GF.mul(baseRow[c], m));
                }
            }
        }

        // Apply the permutation to matrix rows
        for (int r = perm.length - 1; r >= 0; --r) {
            if (perm[r] != r) {
                long[] t = A[r];
                A[r] = A[perm[r]];
                A[perm[r]] = t;
            }
        }
    }
}
