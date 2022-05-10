// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal;

import com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer;
import com.intellij.debugger.streams.breakpoints.consumers.handlers.StreamOperationHandlerBase;
import com.intellij.openapi.util.InvalidDataException;

import java.util.Optional;

// todo пока не работает, вызывает внутри reduce
public class MaxMinHandler extends StreamOperationHandlerBase {
  public static void setOperationResult(int index) {
    Object[] processingResult = basicPeekResultProcessing(index);
    Object result = PeekConsumer.streamResult;
    if (result instanceof Optional[]) {
      Optional[] optionalResult = (Optional[])result;
      PeekConsumer.info[index - 1] = new Object[]{processingResult,
        new Object[]{new boolean[]{optionalResult[0].isPresent()}, new Object[]{optionalResult[0].orElse(new Object())}}};
    } else {
      System.out.println("not optional(((");
    }
  }
}
