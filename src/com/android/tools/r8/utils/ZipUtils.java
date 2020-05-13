// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.errors.CompilationError;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtils {

  public interface OnEntryHandler {
    void onEntry(ZipEntry entry, ZipInputStream input) throws IOException;
  }

  public static void iter(String zipFile, OnEntryHandler handler) throws IOException {
    try (ZipInputStream input = new ZipInputStream(new FileInputStream(zipFile))){
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        handler.onEntry(entry, input);
      }
    }
  }

  public static List<File> unzip(String zipFile, File outDirectory) throws IOException {
    return unzip(zipFile, outDirectory, new Predicate<ZipEntry>() {
      @Override
      public boolean test(ZipEntry entry) {
        return true;
      }
    });
  }

  public static List<File> unzip(String zipFile, File outDirectory, Predicate<ZipEntry> filter)
      throws IOException {
    final Path outDirectoryPath = outDirectory.toPath();
    final List<File> outFiles = new ArrayList<>();
      iter(zipFile, new OnEntryHandler() {
        @Override
        public void onEntry(ZipEntry entry, ZipInputStream input) throws IOException {
          String name = entry.getName();
          if (!entry.isDirectory() && filter.test(entry)) {
            if (name.contains("..")) {
              // Protect against malicious archives.
              throw new CompilationError("Invalid entry name \"" + name + "\"");
            }
            Path outPath = outDirectoryPath.resolve(name);
            File outFile = outPath.toFile();
            outFile.getParentFile().mkdirs();
            FileOutputStream output = new FileOutputStream(outFile);
            ByteStreams.copy(input, output);
            outFiles.add(outFile);
          }
        }
      });
    return outFiles;
  }
}
