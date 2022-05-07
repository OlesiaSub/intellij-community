// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal;

import com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer;
import com.intellij.debugger.streams.breakpoints.consumers.handlers.StreamOperationHandlerBase;

public class CollectHandler extends StreamOperationHandlerBase {
  public static void setOperationResult(int index) {
    Object[] processingResult = basicPeekResultProcessing(index);
    PeekConsumer.info[index - 1] = new Object[]{processingResult, new int[]{PeekConsumer.time.get()}};
  }
}
