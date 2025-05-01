/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.text.translate;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;

public abstract class CharSequenceTranslator {

    public CharSequenceTranslator() {
    }

    public final String translate(final CharSequence input) {
        if (input == null) {
            return null;
        }
        try {
            final StringWriter writer = new StringWriter(input.length() * 2);
            translate(input, writer);
            return writer.toString();
        } catch (final IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    public abstract int translate(CharSequence input, int index, Writer writer) throws IOException;

    public final void translate(final CharSequence input, final Writer writer) throws IOException {
        if (writer == null) {
            throw new IllegalArgumentException("The Writer must not be null");
        }
        if (input == null) {
            return;
        }
        int pos = 0;
        final int len = input.length();
        while (pos < len) {
            final int consumed = translate(input, pos, writer);
            if (consumed == 0) {
                final char c1 = input.charAt(pos);
                writer.write(c1);
                pos++;
                if (Character.isHighSurrogate(c1) && pos < len) {
                    final char c2 = input.charAt(pos);
                    if (Character.isLowSurrogate(c2)) {
                        writer.write(c2);
                        pos++;
                    }
                }
                continue;
            }
            for (int pt = 0; pt < consumed; pt++) {
                pos += Character.charCount(Character.codePointAt(input, pos));
            }
        }
    }
}
