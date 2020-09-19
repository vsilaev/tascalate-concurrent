/**
 * Copyright 2015-2020 Valery Silaev (http://vsilaev.com)
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

public class CompletionStageDecorator<T> extends AbstractCompletionStageDecorator<T, CompletionStage<T>> {
    
    public CompletionStageDecorator(CompletionStage<T> delegate) {
        super(delegate);
    }

    @Override
    protected <U> CompletionStage<U> wrap(CompletionStage<U> original) {
        return new CompletionStageDecorator<>(original);
    }
}
