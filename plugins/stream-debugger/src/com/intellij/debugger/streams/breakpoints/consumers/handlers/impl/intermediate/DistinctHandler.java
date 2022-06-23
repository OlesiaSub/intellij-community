// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.intermediate;

import com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer;
import com.intellij.debugger.streams.breakpoints.consumers.handlers.StreamOperationHandlerBase;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DistinctHandler extends StreamOperationHandlerBase {
  public static void setOperationResult(int index) {
    Object[] processingResult = distinctPeekResultProcessing(index);
    PeekConsumer.info[index - 1] = processingResult;
  }

  public static Object[] distinctPeekResultProcessing(int index) {
    Map<Integer, Object> beforeMap = PeekConsumer.peekArray[index - 1];
    Map<Integer, Object> afterMap = (index == PeekConsumer.peekArray.length ? new HashMap<>(beforeMap.size()) : PeekConsumer.peekArray[index]);
    final Map<Integer, Integer> mapping = new LinkedHashMap<>();
    final Map<Object, Map<Integer, Object>> eqClasses = new HashMap<>();
    for (int beforeTime : beforeMap.keySet()) {
      final Object beforeValue = beforeMap.get(beforeTime);
      final Map<Integer, Object> classItems = eqClasses.computeIfAbsent(beforeValue, key -> new HashMap<>());
      classItems.put(beforeTime, beforeValue);
    }
    for (int afterTime : afterMap.keySet()) {
      final Object afterValue = afterMap.get(afterTime);
      final Map<Integer, Object> classes = eqClasses.get(afterValue);
      for (int classElementTime : classes.keySet()) {
        mapping.put(classElementTime, afterTime);
      }
    }
    Object[] resolve;
    {
      final int size = mapping.size();
      final int[] keys = new int[size];
      final int[] values = new int[size];
      int i = 0;
      for (int key : mapping.keySet()) {
        keys[i] = key;
        values[i] = mapping.get(key);
        i++;
      }
      resolve = new Object[]{keys, values};
    }
    final Object peekResult = getBeforeAndAfterArrays(beforeMap, afterMap);
    return new Object[]{peekResult, resolve};
  }
}

