// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.exec;

import com.intellij.debugger.streams.test.TraceExecutionTestCase;
import com.intellij.debugger.streams.trace.TraceExpressionBuilder;
import com.intellij.debugger.streams.wrapper.StreamChain;
import org.jetbrains.annotations.NotNull;

public class BreakpointBasedImplTest extends TraceExecutionTestCase {
  public void testDefaultInterfaceMethod()  {
    doTest(false);
  }
}
