/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package software.amazon.ai.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;

public interface Repository {

    Gson GSON =
            new GsonBuilder()
                    .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .setPrettyPrinting()
                    .create();

    static Repository newInstance(String name, String url) {
        URI uri = URI.create(url);
        if (!uri.isAbsolute()) {
            return new LocalRepository(name, Paths.get(url));
        }

        String scheme = uri.getScheme();
        if ("file".equalsIgnoreCase(scheme)) {
            return new LocalRepository(name, Paths.get(uri.getPath()));
        }

        return new RemoteRepository(name, uri);
    }

    String getName();

    URI getBaseUri();

    Metadata locate(MRL mrl) throws IOException;

    Artifact resolve(MRL mrl, String version, Map<String, String> filter) throws IOException;

    InputStream openStream(Artifact.Item item, String path) throws IOException;

    default void prepare(Artifact artifact) throws IOException {
        Path cacheDir = getCacheDirectory();
        URI resourceUri = artifact.getResourceUri();
        Path resourceDir = cacheDir.resolve(resourceUri.getPath());
        // files have been downloaded
        if (Files.exists(resourceDir)) {
            return;
        }

        // TODO: extract to temp directory first.
        Files.createDirectories(resourceDir);
        Metadata metadata = artifact.getMetadata();
        URI baseUri = metadata.getRepositoryUri();
        Map<String, Artifact.Item> files = artifact.getFiles();
        for (Map.Entry<String, Artifact.Item> entry : files.entrySet()) {
            Artifact.Item item = entry.getValue();
            URI fileUri = URI.create(item.getUri());
            if (!fileUri.isAbsolute()) {
                fileUri = getBaseUri().resolve(baseUri).resolve(fileUri);
            }

            // This is file is on remote, download it
            String fileName = item.getName();
            String extension = item.getExtension();
            if ("dir".equals(item.getType())) {
                Path dir;
                if (!fileName.isEmpty()) {
                    // honer the name set in metadata.json
                    dir = resourceDir.resolve(fileName);
                    Files.createDirectories(dir);
                } else {
                    dir = resourceDir;
                }
                if (!"zip".equals(extension) && !"tgz".equals(extension)) {
                    throw new UnsupportedOperationException(
                            "File type is not supported: " + extension);
                }
                try (InputStream is = fileUri.toURL().openStream()) {
                    if ("zip".equals(extension)) {
                        ZipUtils.unzip(is, dir);
                    } else if ("tgz".equals(extension)) {
                        try (TarArchiveInputStream fin =
                                new TarArchiveInputStream(new GzipCompressorInputStream(is))) {
                            TarArchiveEntry tarArchiveEntry;
                            while ((tarArchiveEntry = fin.getNextTarEntry()) != null) {
                                if (tarArchiveEntry.isDirectory()) {
                                    continue;
                                }
                                Path file = resourceDir.resolve(fileName);
                                File currentFile =
                                        new File(file.toString(), tarArchiveEntry.getName());
                                IOUtils.copy(
                                        fin,
                                        Files.newOutputStream(Paths.get(currentFile.getName())));
                            }
                        }
                    }
                }
                return;
            }

            try (InputStream is = fileUri.toURL().openStream()) {
                Path file = resourceDir.resolve(fileName);
                if ("zip".equals(extension)) {
                    try (ZipInputStream zis = new ZipInputStream(is)) {
                        zis.getNextEntry();
                        Files.copy(zis, file);
                    }
                } else if ("gzip".equals(extension)) {
                    try (GZIPInputStream zis = new GZIPInputStream(is)) {
                        Files.copy(zis, file);
                    }
                } else if (extension.isEmpty()) {
                    Files.copy(is, file);
                } else {
                    throw new UnsupportedOperationException(
                            "File type is not supported: " + extension);
                }
            }
        }

        // TODO: clean up obsoleted files
    }

    Path getCacheDirectory() throws IOException;
}
