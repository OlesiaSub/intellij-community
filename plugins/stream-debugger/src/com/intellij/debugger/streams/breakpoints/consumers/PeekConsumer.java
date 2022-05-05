// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.lang.String;

public class PeekConsumer {

  private static Object[] info;
  private static long startTime;
  private static AtomicInteger time;
  private static Map<Integer, Object>[] peekArray;
  private static Object streamResult;
  private static String toLoad = "string";

  public static Consumer<Object>[] consumersArray;

  public static void init(int n) {
    System.out.println(toLoad);
    time = new AtomicInteger();
    streamResult = null;
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
      System.out.println(">= " + index);
    }
    else {
      peekArray[index].put(time.get(), value);
    }
  }

  public static void setRetValueObject(Object value) {
    System.out.println("set object ret val");
    streamResult = new Object[]{value};
  }

  public static void setReturnValue(Object value) {
    System.out.println("set array ret val");
    streamResult = value;
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
      if (index == peekArray.length) {
        info[index - 1] = new Object[]{new Object[]{beforeArray, afterArray}, new int[]{time.get()}}; // collect
        //info[index - 1] = new Object[]{new Object[]{beforeArray, afterArray}, streamResult}; // anymatch
      }
      else {
        info[index - 1] = new Object[]{beforeArray, afterArray}; // count
      }
    }
    final long[] elapsedTime = new long[]{System.nanoTime() - startTime};
    myRes = new Object[]{info, streamResult, elapsedTime};
    System.out.println("myRes = " + myRes);
    return myRes;
  }
}
