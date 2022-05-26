// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers;

import com.intellij.debugger.streams.breakpoints.consumers.handlers.ConsumerStruct;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class PeekConsumer {

  public static Object[] info;
  public static AtomicInteger time;
  public static Map<Integer, Object>[] peekArray;
  public static Object streamResult;
  public static Consumer<Object>[] consumersArray;
  public static IntConsumer[] intConsumersArray;
  private static long startTime;

  public static void init(Object array) {
    char[] charArray = (char[])array;
    int n = charArray.length;
    time = new AtomicInteger();
    streamResult = null;
    info = new Object[n];
    peekArray = new LinkedHashMap[n];
    for (int i = 0; i < n; i++) {
      peekArray[i] = new LinkedHashMap<>();
    }
    consumersArray = new Consumer[n];
    intConsumersArray = new IntConsumer[n];
    for (int i = 0; i < n; i++) {
      int finalI = i;
      //System.out.println("Char array[i] " + charArray[i]);
      if (charArray[i] == '.') {
        consumersArray[i] = o -> {
          time.incrementAndGet();
          insertByIndex(finalI, o);
        };
        //System.out.println(". " + i);
      }
      else if (charArray[i] == 'i') {
        intConsumersArray[i] = o -> {
          time.incrementAndGet();
          insertByIndex(finalI, o);
        };
        //System.out.println("i " + i);
      }
      //ConsumerStruct cs = new ConsumerStruct(consumer, intConsumer, charArray[i]);
    }
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
    //System.out.println("set ret val " + value);
    streamResult = value;
  }

  public static Object getResult() {
    Object myRes;
    final long[] elapsedTime = new long[]{System.nanoTime() - startTime};
    myRes = new Object[]{info, streamResult, elapsedTime};
    return myRes;
  }

  public static Consumer<Object> getConsumer(int i) {
    //System.out.println("cons size is " + consumersArray.length + ", but index is " + i);
    Consumer<Object> consumer = consumersArray[i];
    return consumer;
  }

  public static IntConsumer getIntConsumer(int i) {
    //System.out.println("size is " + intConsumersArray.length + ", but index is " + i);
    IntConsumer intConsumer = intConsumersArray[i];
    return intConsumer;
  }
}
