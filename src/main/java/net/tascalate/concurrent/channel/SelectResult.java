/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent.channel;

public final class SelectResult<T> {
    private final int index;
    private final T value;
    private final boolean isSend;

    public SelectResult(int index, T value, boolean isSend) {
        this.index = index;
        this.value = value;
        this.isSend = isSend;
    }

    public int index() { 
        return index; 
    }
    
    public T value() { 
        return value; 
    }
    
    public boolean isSend() { 
        return isSend; 
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SelectResult)) return false;
        SelectResult<?> result = (SelectResult<?>) o;
        return index == result.index &&
               isSend == result.isSend &&
               java.util.Objects.equals(value, result.value);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(index, value, isSend);
    }

    @Override
    public String toString() {
        return "SelectResult{" +
               "index=" + index +
               ", value=" + value +
               ", isSend=" + isSend +
               '}';
    }
}