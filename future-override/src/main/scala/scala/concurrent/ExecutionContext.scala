/*
 * Scala (https://www.scala-lang.org)
 *
 * Copyright EPFL and Lightbend, Inc.
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package scala.concurrent

import java.util.concurrent.{ ExecutorService, Executor }
import scala.annotation.implicitNotFound



/**
 * An [[ExecutionContext]] that is also a
 * Java [[java.util.concurrent.Executor Executor]].
 */
trait ExecutionContextExecutor extends ExecutionContext with Executor

/**
 * An [[ExecutionContext]] that is also a
 * Java [[java.util.concurrent.ExecutorService ExecutorService]].
 */
trait ExecutionContextExecutorService extends ExecutionContextExecutor with ExecutorService



