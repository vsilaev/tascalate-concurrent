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

import java.util.Objects;

public final class SelectResult<T> {
    private final int index;
    private final SelectCase<T> match;
    private final T value;

    public SelectResult(int index, SelectCase<T> match, T value) {
        this.index = index;
        this.match = match;
        this.value = value;
    }

    public int index() { 
        return index; 
    }
    
    public T value() { 
        return value; 
    }
    
    public SelectCase<T> match() { 
        return match; 
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        
        if (!(o instanceof SelectResult)) {
            return false;
        }
        
        SelectResult<?> result = (SelectResult<?>) o;
        return index == result.index &&
               Objects.equals(match, result.match) &&
               Objects.equals(value, result.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, value, match);
    }

    @Override
    public String toString() {
        return "SelectResult{" +
               "index=" + index + ", " +
               "match=" + match + ", " +
               "value=" + value +
               '}';
    }
}