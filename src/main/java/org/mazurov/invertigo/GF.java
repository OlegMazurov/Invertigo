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

public class GF {

    public static final long ZERO = 0;              // Galois field zero
    public static final long UNIT = 1;              // Galois field unit

    private static final long ROOT_4  = 0x13l;          // 2^4 Galois field generator
    private static final long ROOT_8  = 0x11dl;          // 2^8 Galois field generator
    private static final long ROOT_12 = 0x1053l;         // 2^12 Galois field generator
    private static final long ROOT_16 = 0x1002dl;        // 2^16 Galois field generator
    private static final long ROOT_32 = 0x1000000afl;   // 2^32 Galois field generator
    private static final long ROOT_36 = 0x1000000077l;   // 2^32 Galois field generator
    private static final long ROOT_42 = 0x4000000003fl;   // 2^42 Galois field generator

    private static final long ROOT = ROOT_32;
    private static final long MSBIT = 62 - Long.numberOfLeadingZeros(ROOT); // Highest degree in residual polynomials

    /**
     * Galois field multiplication
     * @param a field element
     * @param b field element
     * @return {@code a * b}
     */
    public static long mul(long a, long b) {
        long res = ZERO;
        while (b != 0) {
            res ^= a * (b & 1);
            a = (a << 1) ^ (a >>> MSBIT) * ROOT;
            b >>>= 1;
        }
        return res;
    }

    /**
     * Galois field power function
     * @param a field element
     * @param exp exponent to which {@code a} is to be raised
     * @return {@code a ^ exp}
     */
    public static long pow(long a, long exp) {
        long bit = Long.highestOneBit(exp);
        long res = UNIT;
        while (bit != 0) {
            res = mul(res, res);
            if ((exp & bit) != 0) {
                res = mul(res, a);
            }
            bit = bit >>> 1;
        }
        return res;
    }

    /**
     * Galois field division
     * Using the extended Euclid algorithm simultaneously multiplying by {@code a}
     * @param a
     * @param b
     * @return {@code a / b}
     */
    public static long div(long a, long b) {
        if (b == ZERO) {
            throw new IllegalArgumentException("Division by zero");
        }
        long p = ROOT;
        long vp = 0;
        long q = b;
        long vq = a;
        long m = 1l << (MSBIT + 1);

        while (p != UNIT) {
            for (;;) {
                if ((p & m) != 0) break;
                else if ((q & m) != 0) {
                    long t = p; p = q; q = t;
                    t = vp; vp = vq; vq = t;
                    break;
                }
                m >>>= 1;
            }
            long r = q;
            long vr = vq;
            while ((r & m) == 0) {
                r <<= 1;
                vr = (vr << 1) ^ (vr >>> MSBIT) * ROOT;
            }
            p ^= r;
            vp ^= vr;
        }
        return vp;
    }

    /**
     * Galois field reciprocal
     * @param a field element
     * @return {@code 1 / a}
     */
    public static long rev(long a) {
        return div(UNIT, a);
    }

    /**
     * Galois field addition
     * @param a field element
     * @param b field element
     * @return {@code a + b}
     */
    public static long add(long a, long b) {
        return a ^ b;
    }

    /**
     * Galois field subtraction
     * @param a field element
     * @param b field element
     * @return {@code a - b}
     */
    public static long sub(long a, long b) {
        return a ^ b;
    }

    /**
     * Galois field cardinality
     * @return the number of elements in the field
     */
    public static long cardinality() {
        return Long.highestOneBit(ROOT);
    }

}
