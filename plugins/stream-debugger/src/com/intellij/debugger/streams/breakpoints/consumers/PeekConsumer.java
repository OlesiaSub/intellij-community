// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

public class PeekConsumer {

  public static Object[] info;
  public static AtomicInteger time;
  public static Map<Integer, Object>[] peekArray;
  public static Object streamResult;
  public static Consumer<Object>[] consumersArray;
  public static IntConsumer[] intConsumersArray;
  public static LongConsumer[] longConsumersArray;
  public static DoubleConsumer[] doubleConsumersArray;
  private static long startTime;

  public static void init(int n) {
    time = new AtomicInteger();
    streamResult = "undefined";
    info = new Object[n];
    peekArray = new LinkedHashMap[n];
    for (int i = 0; i < n; i++) {
      peekArray[i] = new LinkedHashMap<>();
    }
    consumersArray = new Consumer[n];
    intConsumersArray = new IntConsumer[n];
    longConsumersArray = new LongConsumer[n];
    doubleConsumersArray = new DoubleConsumer[n];
    startTime = System.nanoTime();
  }

  public static void insertByIndex(int index, Object value) {
    if (index >= peekArray.length) {
      return; // todo
    }
    else {
      peekArray[index].put(time.get(), value);
    }
  }

  public static void setReturnValue(Object value) {

    streamResult = value;
    if (streamResult == null) {
      streamResult = new Object[1];
    }

  }

  public static Object getResult() {
    Object myRes;
    final long[] elapsedTime = new long[]{System.nanoTime() - startTime};
    myRes = new Object[]{info, streamResult, elapsedTime};
    return myRes;
  }

  public static Consumer<Object> getConsumer(int i) {
    consumersArray[i] = o -> {
      time.incrementAndGet();
      insertByIndex(i, o);
    };
    return consumersArray[i];
  }

  public static IntConsumer getIntConsumer(int i) {
    intConsumersArray[i] = o -> {
      time.incrementAndGet();
      insertByIndex(i, o);
    };
    return intConsumersArray[i];
  }

  public static LongConsumer getLongConsumer(int i) {
    longConsumersArray[i] = o -> {
      time.incrementAndGet();
      insertByIndex(i, o);
    };
    return longConsumersArray[i];
  }

  public static DoubleConsumer getDoubleConsumer(int i) {
    doubleConsumersArray[i] = o -> {
      time.incrementAndGet();
      insertByIndex(i, o);
    };
    return doubleConsumersArray[i];
  }
}
