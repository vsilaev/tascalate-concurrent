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
package net.tascalate.concurrent;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * An object that may hold resources that must be explicitly released, where the release may be
 * performed asynchronously.
 *
 * <p>
 * Examples of such resources are manually managed memory, open file handles, socket descriptors
 * etc. While similar to {@link AutoCloseable}, this interface should be used when the resource
 * release operation may possibly be async. For example, if an object is thread-safe and has many
 * consumers, an implementation may require all current ongoing operations to complete before
 * resources are relinquished. 
 *
 * <p>
 * May be used with the methods {@link Promises#tryApply(CompletionStage, Function)},
 * {@link Promises#tryCompose(AsyncCloseable, Function)} to emulate the behavior of a try
 * with resources block.
 * 
 */
@FunctionalInterface
public interface AsyncCloseable {
  /**
   * Relinquishes any resources associated with this object.
   *
   * @return a {@link CompletionStage} that completes when all resources associated with this object
   *         have been released, or with an exception if the resources cannot be released.
   */
  CompletionStage<Void> close();
}