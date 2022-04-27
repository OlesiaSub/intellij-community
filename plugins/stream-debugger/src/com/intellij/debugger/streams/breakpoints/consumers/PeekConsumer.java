// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class PeekConsumer {

  private static final Object[] info = new Object[2];
  private static final AtomicInteger time = new AtomicInteger();
  private static Map<Integer, Object>[] peekArray;

  public static Consumer<Object>[] consumersArray;

  static {
    int n = 5;
    peekArray = new LinkedHashMap[n];
    for (int i = 0; i < n; i++) {
      peekArray[i] = new LinkedHashMap<>();
    }
    consumersArray = new Consumer[n];
    for (int i = 0; i < n; i++) {
      int finalI = i;
      consumersArray[i] = o -> {
        insertByIndex(finalI, o);
        time.incrementAndGet();
        System.out.println(finalI + "   " + peekArray[finalI]);
      };
    }
  }

  private static void insertByIndex(int index, Object value) {
    peekArray[index].put(time.get(), value);
  }

}
