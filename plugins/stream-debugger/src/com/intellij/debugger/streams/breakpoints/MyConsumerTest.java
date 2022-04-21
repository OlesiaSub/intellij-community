// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints;

import java.util.function.Consumer;

public class MyConsumerTest {
  public static Consumer<Integer> consumer = o -> System.out.println("in my consumer " + o);
}
