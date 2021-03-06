/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.util;


import java.math.BigInteger;
import java.util.Arrays;

/**
 * Helper APIs to encode numeric values as sortable bytes and vice-versa.
 *
 * @lucene.internal
 */
public final class NumericUtils {

  private NumericUtils() {} // no instance!
  
  /**
   * Converts a <code>double</code> value to a sortable signed <code>long</code>.
   * The value is converted by getting their IEEE 754 floating-point &quot;double format&quot;
   * bit layout and then some bits are swapped, to be able to compare the result as long.
   * By this the precision is not reduced, but the value can easily used as a long.
   * The sort order (including {@link Double#NaN}) is defined by
   * {@link Double#compareTo}; {@code NaN} is greater than positive infinity.
   * @see #sortableLongToDouble
   */
  public static long doubleToSortableLong(double val) {
    return sortableDoubleBits(Double.doubleToLongBits(val));
  }

  /**
   * Converts a sortable <code>long</code> back to a <code>double</code>.
   * @see #doubleToSortableLong
   */
  public static double sortableLongToDouble(long val) {
    return Double.longBitsToDouble(sortableDoubleBits(val));
  }

  /**
   * Converts a <code>float</code> value to a sortable signed <code>int</code>.
   * The value is converted by getting their IEEE 754 floating-point &quot;float format&quot;
   * bit layout and then some bits are swapped, to be able to compare the result as int.
   * By this the precision is not reduced, but the value can easily used as an int.
   * The sort order (including {@link Float#NaN}) is defined by
   * {@link Float#compareTo}; {@code NaN} is greater than positive infinity.
   * @see #sortableIntToFloat
   */
  public static int floatToSortableInt(float val) {
    return sortableFloatBits(Float.floatToIntBits(val));
  }

  /**
   * Converts a sortable <code>int</code> back to a <code>float</code>.
   * @see #floatToSortableInt
   */
  public static float sortableIntToFloat(int val) {
    return Float.intBitsToFloat(sortableFloatBits(val));
  }
  
  /** Converts IEEE 754 representation of a double to sortable order (or back to the original) */
  public static long sortableDoubleBits(long bits) {
    return bits ^ (bits >> 63) & 0x7fffffffffffffffL;
  }
  
  /** Converts IEEE 754 representation of a float to sortable order (or back to the original) */
  public static int sortableFloatBits(int bits) {
    return bits ^ (bits >> 31) & 0x7fffffff;
  }


  /** Result = a - b, where a &gt;= b, else {@code IllegalArgumentException} is thrown.  */
  public static void subtract(int bytesPerDim, int dim, byte[] a, byte[] b, byte[] result) {
    int start = dim * bytesPerDim;
    int end = start + bytesPerDim;
    int borrow = 0;
    for(int i=end-1;i>=start;i--) {
      int diff = (a[i]&0xff) - (b[i]&0xff) - borrow;
      if (diff < 0) {
        diff += 256;
        borrow = 1;
      } else {
        borrow = 0;
      }
      result[i-start] = (byte) diff;
    }
    if (borrow != 0) {
      throw new IllegalArgumentException("a < b");
    }
  }

  /** Result = a + b, where a and b are unsigned.  If there is an overflow, {@code IllegalArgumentException} is thrown. */
  public static void add(int bytesPerDim, int dim, byte[] a, byte[] b, byte[] result) {
    int start = dim * bytesPerDim;
    int end = start + bytesPerDim;
    int carry = 0;
    for(int i=end-1;i>=start;i--) {
      int digitSum = (a[i]&0xff) + (b[i]&0xff) + carry;
      if (digitSum > 255) {
        digitSum -= 256;
        carry = 1;
      } else {
        carry = 0;
      }
      result[i-start] = (byte) digitSum;
    }
    if (carry != 0) {
      throw new IllegalArgumentException("a + b overflows bytesPerDim=" + bytesPerDim);
    }
  }

  /** Returns true if N-dim rect A contains N-dim rect B */
  public static boolean contains(int bytesPerDim,
                                 byte[] minPackedA, byte[] maxPackedA,
                                 byte[] minPackedB, byte[] maxPackedB) {
    int dims = minPackedA.length / bytesPerDim;
    for(int dim=0;dim<dims;dim++) {
      int offset = dim * bytesPerDim;
      if (StringHelper.compare(bytesPerDim, minPackedA, offset, minPackedB, offset) > 0) {
        return false;
      }
      if (StringHelper.compare(bytesPerDim, maxPackedA, offset, maxPackedB, offset) < 0) {
        return false;
      }
    }

    return true;
  }

  public static void intToBytes(int x, byte[] dest, int offset) {
    // Flip the sign bit, so negative ints sort before positive ints correctly:
    x ^= 0x80000000;
    for (int i = 0; i < 4; i++) {
      dest[offset+i] = (byte) (x >> 24-i*8);
    }
  }

  public static int bytesToInt(byte[] src, int offset) {
    int x = 0;
    for (int i = 0; i < 4; i++) {
      x |= (src[offset+i] & 0xff) << (24-i*8);
    }
    // Re-flip the sign bit to restore the original value:
    return x ^ 0x80000000;
  }

  public static void longToBytes(long v, byte[] bytes, int offset) {
    // Flip the sign bit so negative longs sort before positive longs:
    v ^= 0x8000000000000000L;
    longToBytesDirect(v, bytes, offset);
  }

  public static void longToBytesDirect(long v, byte[] bytes, int offset) {
    bytes[offset] = (byte) (v >> 56);
    bytes[offset+1] = (byte) (v >> 48);
    bytes[offset+2] = (byte) (v >> 40);
    bytes[offset+3] = (byte) (v >> 32);
    bytes[offset+4] = (byte) (v >> 24);
    bytes[offset+5] = (byte) (v >> 16);
    bytes[offset+6] = (byte) (v >> 8);
    bytes[offset+7] = (byte) v;
  }

  public static long bytesToLong(byte[] bytes, int offset) {
    long v = bytesToLongDirect(bytes, offset);
    // Flip the sign bit back
    v ^= 0x8000000000000000L;
    return v;
  }

  public static long bytesToLongDirect(byte[] bytes, int offset) {
    long v = ((bytes[offset] & 0xffL) << 56) |
      ((bytes[offset+1] & 0xffL) << 48) |
      ((bytes[offset+2] & 0xffL) << 40) |
      ((bytes[offset+3] & 0xffL) << 32) |
      ((bytes[offset+4] & 0xffL) << 24) |
      ((bytes[offset+5] & 0xffL) << 16) |
      ((bytes[offset+6] & 0xffL) << 8) |
      (bytes[offset+7] & 0xffL);
    return v;
  }

  public static void sortableBigIntBytes(byte[] bytes) {
    // Flip the sign bit so negative bigints sort before positive bigints:
    bytes[0] ^= 0x80;
  }

  public static void bigIntToBytes(BigInteger bigInt, int bigIntSize, byte[] result, int offset) {
    byte[] bigIntBytes = bigInt.toByteArray();
    byte[] fullBigIntBytes;

    if (bigIntBytes.length < bigIntSize) {
      fullBigIntBytes = new byte[bigIntSize];
      System.arraycopy(bigIntBytes, 0, fullBigIntBytes, bigIntSize-bigIntBytes.length, bigIntBytes.length);
      if ((bigIntBytes[0] & 0x80) != 0) {
        // sign extend
        Arrays.fill(fullBigIntBytes, 0, bigIntSize-bigIntBytes.length, (byte) 0xff);
      }
    } else if (bigIntBytes.length == bigIntSize) {
      fullBigIntBytes = bigIntBytes;
    } else {
      throw new IllegalArgumentException("BigInteger: " + bigInt + " requires more than " + bigIntSize + " bytes storage");
    }
    sortableBigIntBytes(fullBigIntBytes);

    System.arraycopy(fullBigIntBytes, 0, result, offset, bigIntSize);

    assert bytesToBigInt(result, offset, bigIntSize).equals(bigInt): "bigInt=" + bigInt + " converted=" + bytesToBigInt(result, offset, bigIntSize);
  }

  public static BigInteger bytesToBigInt(byte[] bytes, int offset, int length) {
    byte[] bigIntBytes = new byte[length];
    System.arraycopy(bytes, offset, bigIntBytes, 0, length);
    sortableBigIntBytes(bigIntBytes);
    return new BigInteger(bigIntBytes);
  }
}
