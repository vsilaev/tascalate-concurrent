/**
 * Copyright 2015-2021 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.concurrent.core;

final class CurrentCompletionStageAPI {
    static final CompletionStageAPI INSTANCE;
    
    private CurrentCompletionStageAPI() {}
    
    private static int getJavaVersion() {
        // Use specification version instead of java.version
        String version = System.getProperty("java.specification.version");
        if (version == null || version.length() == 0) return 0;

        // Handle legacy Java 8 ("1.8")
        if (version.startsWith("1.")) {
            // Safe because "1." is always followed by at least one digit
            int dot2 = version.indexOf('.', 2);
            if (dot2 == -1) {
                return Integer.parseInt(version.substring(2));
            }
            return Integer.parseInt(version.substring(2, dot2));
        }

        // Handle modern Java ("11", "21", "22-ea")
        // Just read digits until we hit a non-digit character
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) {
            end++;
        }
        
        return (end == 0) ? 0 : Integer.parseInt(version.substring(0, end));
    }
    
    static {
        int version = getJavaVersion();
        if (version >= 12) 
            INSTANCE = new J12CompletionStageAPI();
        else if (version >= 9) 
            INSTANCE = new J9CompletionStageAPI();
        else
            INSTANCE = new J8CompletionStageAPI();
    }
}
