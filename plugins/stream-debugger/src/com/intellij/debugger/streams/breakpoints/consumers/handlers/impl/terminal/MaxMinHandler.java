// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal;

import com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer;
import com.intellij.debugger.streams.breakpoints.consumers.handlers.StreamOperationHandlerBase;
import com.intellij.openapi.util.InvalidDataException;

import java.util.Optional;

public class MaxMinHandler extends StreamOperationHandlerBase {
  public static void setOperationResult(int index) {
    Object[] processingResult = basicPeekResultProcessing(index);
    Object result = PeekConsumer.streamResult;
    assert result instanceof Object[];
    Object[] arrayResult = (Object[])result;
    PeekConsumer.info[index - 1] = new Object[]{processingResult,
      new Object[]{new boolean[]{((Optional)arrayResult[0]).isPresent()}, new Object[]{((Optional)arrayResult[0]).orElse(new Object())}}};
  }
}
