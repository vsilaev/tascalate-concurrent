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
package net.tascalate.concurrent.core;

class CurrentCompletionStageAPI {
    static final CompletionStageAPI INSTANCE;
    
    private static int getJavaVersion() {
        String version = System.getProperty("java.version");
        
        if (version.startsWith("1.")) {
            version = version.substring(2, version.indexOf('.', 2));
        } else {
            int dot = version.indexOf(".");
            if (dot > 0) { 
                version = version.substring(0, dot); 
            }
        } 
        return Integer.parseInt(version);
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
