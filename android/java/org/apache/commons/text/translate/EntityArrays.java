/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.text.translate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * @noinspection Java9CollectionFactory, SpellCheckingInspection
 */
public class EntityArrays {

    public static final Map<CharSequence, CharSequence> BASIC_ESCAPE;

    static {
        final Map<CharSequence, CharSequence> initialMap = new HashMap<>();
        initialMap.put("\"", "&quot;");
        initialMap.put("&", "&amp;");
        initialMap.put("<", "&lt;");
        initialMap.put(">", "&gt;");
        BASIC_ESCAPE = Collections.unmodifiableMap(initialMap);
    }

    public static final Map<CharSequence, CharSequence> BASIC_UNESCAPE;

    static {
        BASIC_UNESCAPE = Collections.unmodifiableMap(invert(BASIC_ESCAPE));
    }

    public static final Map<CharSequence, CharSequence> APOS_ESCAPE;

    static {
        final Map<CharSequence, CharSequence> initialMap = new HashMap<>();
        initialMap.put("'", "&apos;");
        APOS_ESCAPE = Collections.unmodifiableMap(initialMap);
    }

    public static final Map<CharSequence, CharSequence> APOS_UNESCAPE;

    static {
        APOS_UNESCAPE = Collections.unmodifiableMap(invert(APOS_ESCAPE));
    }

    public static final Map<CharSequence, CharSequence> JAVA_CTRL_CHARS_ESCAPE;

    static {
        final Map<CharSequence, CharSequence> initialMap = new HashMap<>();
        initialMap.put("\b", "\\b");
        initialMap.put("\n", "\\n");
        initialMap.put("\t", "\\t");
        initialMap.put("\f", "\\f");
        initialMap.put("\r", "\\r");
        JAVA_CTRL_CHARS_ESCAPE = Collections.unmodifiableMap(initialMap);
    }

    public static final Map<CharSequence, CharSequence> JAVA_CTRL_CHARS_UNESCAPE;

    static {
        JAVA_CTRL_CHARS_UNESCAPE = Collections.unmodifiableMap(invert(JAVA_CTRL_CHARS_ESCAPE));
    }

    public static Map<CharSequence, CharSequence> invert(final Map<CharSequence, CharSequence> map) {
        return map.entrySet().stream().collect(Collectors.toMap(Entry::getValue, Entry::getKey));
    }
}
