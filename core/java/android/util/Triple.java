/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.util;

/**
 * Container to ease passing around a tuple of three objects. This object provides a sensible
 * implementation of equals(), returning true if equals() is true on each of the contained
 * objects.
 */
public class Triple<F, S, T> {
    public final F first;
    public final S second;
    public final T third;

    /**
     * Constructor for a Triple. If any are null then equals() and hashCode() will throw
     * a NullPointerException.
     * @param first the first object in the Triple
     * @param second the second object in the Triple
     * @param third the third object in the Triple
     */
    public Triple(F first, S second, T third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    /**
     * Checks the three objects for equality by delegating to their respective equals() methods.
     * @param o the Triple to which this one is to be checked for equality
     * @return true if the underlying objects of the Triple are all considered equals()
     */
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Triple)) return false;
        final Triple<F, S, T> other;
        try {
            other = (Triple<F, S, T>) o;
        } catch (ClassCastException e) {
            return false;
        }
        return first.equals(other.first) && second.equals(other.second) &&
            third.equals(other.third);
    }

    /**
     * Compute a hash code using the hash codes of the underlying objects
     * @return a hashcode of the Triple
     */
    public int hashCode() {
        int result = 17;
        result = 31 * result + first.hashCode();
        result = 31 * result + second.hashCode();
        result = 31 * result + third.hashCode();
        return result;
    }

    /**
     * Convenience method for creating an appropriately typed Triple.
     * @param a the first object in the Triple
     * @param b the second object in the Triple
     * @param c the third object in the Triple
     * @return a Triple that is templatized with the types of a, b and c
     */
    public static <A, B, C> Triple <A, B, C> create(A a, B b, C c) {
        return new Triple<A, B, C>(a, b, c);
    }
}
