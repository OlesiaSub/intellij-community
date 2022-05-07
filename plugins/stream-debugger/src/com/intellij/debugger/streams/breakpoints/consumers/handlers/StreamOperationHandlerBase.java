// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers;

import com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer;

import java.util.HashMap;
import java.util.Map;

public class StreamOperationHandlerBase {
  public static Object[] basicPeekResultProcessing(int index) {
    Map<Integer, Object> prev = PeekConsumer.peekArray[index - 1];
    Map<Integer, Object> cur = (index == PeekConsumer.peekArray.length ? new HashMap<>(prev.size()) : PeekConsumer.peekArray[index]);
    return getBeforeAndAfterArrays(prev, cur);
  }

  public static Object[] getBeforeAndAfterArrays(Map<Integer, Object> prev, Map<Integer, Object> cur) {
    Object[] beforeArray = collectMapResult(prev);
    Object[] afterArray = collectMapResult(cur);
    return new Object[]{beforeArray, afterArray};
  }

  public static Object[] collectMapResult(Map<Integer, Object> map) {
    final int size = map.size();
    final int[] keys = new int[size];
    final Object[] values = new Object[size];
    int i = 0;
    for (int key : map.keySet()) {
      keys[i] = key;
      values[i] = map.get(key);
      i++;
    }
    return new Object[]{keys, values};
  }
}
