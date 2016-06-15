/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.taskcache;

import com.google.common.hash.HashCode;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;

public class LocalDirectoryTaskResultCache implements TaskResultCache {
    private final File directory;

    public LocalDirectoryTaskResultCache(File directory) {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IllegalArgumentException(String.format("Cache directory %s must be a directory", directory));
            }
            if (!directory.canRead()) {
                throw new IllegalArgumentException(String.format("Cache directory %s must be readable", directory));
            }
            if (!directory.canWrite()) {
                throw new IllegalArgumentException(String.format("Cache directory %s must be writable", directory));
            }
        } else {
            if (!directory.mkdirs()) {
                throw new UncheckedIOException(String.format("Could not create cache directory: %s", directory));
            }
        }
        this.directory = directory;
    }

    @Override
    public TaskOutputReader get(HashCode key) throws IOException {
        final File file = getFile(key);
        if (file.isFile()) {
            return new TaskOutputReader() {
                @Override
                public ByteSource read() throws IOException {
                    return Files.asByteSource(file);
                }
            };
        }
        return null;
    }

    @Override
    public void put(HashCode key, TaskOutputWriter result) throws IOException {
        result.writeTo(Files.asByteSink(getFile(key)));
    }

    private File getFile(HashCode key) {
        return new File(directory, key.toString());
    }

    @Override
    public String getDescription() {
        return "local directory cache in " + directory;
    }
}