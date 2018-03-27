/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent.decorators;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

import net.tascalate.concurrent.Promise;

public class BlockingCompletionStageDecorator<T, D extends CompletionStage<T> & Future<T>> 
    extends AbstractFutureDecorator<T, D>
    implements Promise<T> {
    
    protected BlockingCompletionStageDecorator(D delegate) {
        super(delegate);
    }
    
    @Override
    protected <U> Promise<U> wrap(CompletionStage<U> original) {
        return new BlockingCompletionStageDecorator<>(cast(original));
    }
    
    private static <T, D extends CompletionStage<T> & Future<T>> D cast(CompletionStage<T> stage) {
        if (!(stage instanceof Future)) {
            throw new IllegalArgumentException(
                "Parameter must implement both " + 
                CompletionStage.class.getName() + " and " +
                Future.class.getName()
            );
        }
        @SuppressWarnings("unchecked")
        D result = (D)stage;
        return result;
    }
    
    public static <T> Promise<T> from(CompletionStage<T> stage) {
        return new BlockingCompletionStageDecorator<>(cast(stage));
    }
}
