// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class PeekConsumer {

  private static final Object[] info = new Object[2];
  private static final AtomicInteger time = new AtomicInteger();
  private static Map<Integer, Object>[] peekArray;

  public static Consumer<Object>[] consumersArray;

  public static void init(int n) {
    System.out.println("SIZE = " + n);
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

  public static void insertByIndex(int index, Object value) {
    if (index >= peekArray.length) {
      System.out.println(index);
    }
    else peekArray[index].put(time.get(), value);
  }

}
