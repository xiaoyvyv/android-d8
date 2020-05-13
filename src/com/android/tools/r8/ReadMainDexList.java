// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ProguardMapReader;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.Iterators;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility for applying proguard map and sorting the main dex list.
 */
public class ReadMainDexList {

    private String DOT_CLASS = ".class";

    private String stripDotClass(String name) {
        return name.endsWith(DOT_CLASS) ? name.substring(0, name.length() - DOT_CLASS.length()) : name;
    }

    private String toClassFilePath(String name) {
        return name.replace('.', '/') + DOT_CLASS;
    }

    private String toKeepRule(String className) {
        return "-keep class " + className + " {}";
    }

    private String deobfuscateClassName(String name, ClassNameMapper mapper) {
        if (mapper == null) {
            return name;
        }
        return mapper.deobfuscateClassName(name);
    }

    private void run(String[] args) throws Exception {
        if (args.length < 1 || args.length > 3) {
            System.out.println("Usage: command [-k] <main_dex_list> [<proguard_map>]");
            System.exit(0);
        }

        Iterator<String> arguments = Iterators.forArray(args);
        Function<String, String> outputGenerator;
        String arg = arguments.next();
        if (arg.equals("-k")) {
            outputGenerator = new Function<String, String>() {
                @Override
                public String apply(String className) {
                    return ReadMainDexList.this.toKeepRule(className);
                }
            };
            arg = arguments.next();
        } else {
            outputGenerator = new Function<String, String>() {
                @Override
                public String apply(String name) {
                    return ReadMainDexList.this.toClassFilePath(name);
                }
            };
        }
        Path mainDexList = Paths.get(arg);

        final ClassNameMapper mapper =
                arguments.hasNext() ? ProguardMapReader.mapperFromFile(Paths.get(arguments.next())) : null;

        FileUtils.readTextFile(mainDexList)
                .stream()
                .map(new Function<String, String>() {
                    @Override
                    public String apply(String name1) {
                        return ReadMainDexList.this.stripDotClass(name1);
                    }
                })
                .map(new Function<String, String>() {
                    @Override
                    public String apply(String name) {
                        return name.replace('/', '.');
                    }
                })
                .map(new Function<String, String>() {
                    @Override
                    public String apply(String name) {
                        return ReadMainDexList.this.deobfuscateClassName(name, mapper);
                    }
                })
                .map(outputGenerator)
                .sorted()
                .collect(Collectors.toList())
                .forEach(new Consumer<Object>() {
                    @Override
                    public void accept(Object x) {
                        System.out.println(x);
                    }
                });
    }

    public static void main(String[] args) throws Exception {
        new ReadMainDexList().run(args);
    }
}
