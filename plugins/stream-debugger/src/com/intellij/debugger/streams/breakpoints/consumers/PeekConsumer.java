// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class PeekConsumer {

  private static Object[] info;
  private static long startTime;
  private static final AtomicInteger time = new AtomicInteger();
  private static Map<Integer, Object>[] peekArray;
  private static Object streamResult = null;

  public static Consumer<Object>[] consumersArray;

  public static void init(int n) {
    info = new Object[n];
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
      };
    }
    startTime = System.nanoTime();
  }

  public static void insertByIndex(int index, Object value) {
    if (index >= peekArray.length) {
      System.out.println(index);
    }
    else {
      peekArray[index].put(time.get(), value);
    }
  }

  public static void setReturnValue(long returnValue) {
    streamResult = new long[]{returnValue};
  }

  public static Object getResult() {
    Object myRes;
    for (int index = 1; index <= peekArray.length; index++) {
      Map<Integer, Object> prev = peekArray[index - 1];
      Map<Integer, Object> cur;
      if (index == peekArray.length) {
        cur = new HashMap<>(prev.size());
      }
      else {
        cur = peekArray[index];
      }
      Object[] beforeArray;
      {
        final int size = prev.size();
        final int[] keys = new int[size];
        final Object[] values = new Object[size];
        int i = 0;
        for (int key : prev.keySet()) {
          keys[i] = key;
          values[i] = prev.get(key);
          i++;
        }
        beforeArray = new Object[]{keys, values};
      }
      Object[] afterArray;
      {
        final int size = cur.size();
        final int[] keys = new int[size];
        final Object[] values = new Object[size];
        int i = 0;
        for (int key : cur.keySet()) {
          keys[i] = key;
          values[i] = cur.get(key);
          i++;
        }
        afterArray = new Object[]{keys, values};
      }
      info[index - 1] = new Object[]{beforeArray, afterArray};
    }
    final long[] elapsedTime = new long[]{System.nanoTime() - startTime};
    myRes = new Object[]{info, streamResult, elapsedTime};
    return myRes;
  }
}
