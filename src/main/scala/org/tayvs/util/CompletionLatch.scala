package org.tayvs.util

import scala.concurrent.{Batchable, CanAwait, ExecutionContext, ExecutionException, Future => ClassicFuture, TimeoutException, Promise => ClassicPromise}
import scala.concurrent.duration.Duration
import scala.annotation.{nowarn, switch, tailrec}
import scala.util.control.{ControlThrowable, NoStackTrace, NonFatal}
import scala.util.{Failure, Success, Try}
import scala.runtime.NonLocalReturnControl
import java.util.concurrent.locks.AbstractQueuedSynchronizer
import java.util.concurrent.atomic.AtomicReference
import java.util.Objects.requireNonNull
import java.io.{IOException, NotSerializableException, ObjectInputStream, ObjectOutputStream}

/**
 * Latch used to implement waiting on a DefaultPromise's result.
 *
 * Inspired by: http://gee.cs.oswego.edu/cgi-bin/viewcvs.cgi/jsr166/src/main/java/util/concurrent/locks/AbstractQueuedSynchronizer.java
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * https://creativecommons.org/publicdomain/zero/1.0/
 */
final private[util] class CompletionLatch[T] extends AbstractQueuedSynchronizer with (Try[T] => Unit) {
  //@volatie not needed since we use acquire/release
  /*@volatile*/
  private[this] var _result: Try[T] = null

  final def result: Try[T] = _result

  override protected def tryAcquireShared(ignored: Int): Int = if (getState != 0) 1 else -1

  override protected def tryReleaseShared(ignore: Int): Boolean = {
    setState(1)
    true
  }

  override def apply(value: Try[T]): Unit = {
    _result = value // This line MUST go before releaseShared
    releaseShared(1)
  }
}

object Promise {
  /** Creates a promise object which can be completed with a value.
   *
   * @tparam T the type of the value in the promise
   * @return the newly created `Promise` instance
   */
  final def apply[T](): ClassicPromise[T] = new DefaultPromise[T]()

  /** Creates an already completed Promise with the specified exception.
   *
   * @tparam T the type of the value in the promise
   * @return the newly created `Promise` instance
   */
  final def failed[T](exception: Throwable): ClassicPromise[T] = fromTry(Failure(exception))

  /** Creates an already completed Promise with the specified result.
   *
   * @tparam T the type of the value in the promise
   * @return the newly created `Promise` instance
   */
  final def successful[T](result: T): ClassicPromise[T] = fromTry(Success(result))

  /** Creates an already completed Promise with the specified result or exception.
   *
   * @tparam T the type of the value in the promise
   * @return the newly created `Promise` instance
   */
  final def fromTry[T](result: Try[T]): ClassicPromise[T] = new DefaultPromise[T](result)


//  final val recoverWithFailedMarker: Future[Nothing] =
//    scala.concurrent.Future.failed(new Throwable with NoStackTrace)
//
//  final val filterFailure =
//    Failure[Nothing](new NoSuchElementException("Future.filter predicate is not satisfied") with NoStackTrace)
//
//  final val collectFailed =
//    (t: Any) => throw new NoSuchElementException("Future.collect partial function is not defined at: " + t) with NoStackTrace

  /**
   * Link represents a completion dependency between 2 DefaultPromises.
   * As the DefaultPromise referred to by a Link can itself be linked to another promise
   * `relink` traverses such chains and compresses them so that the link always points
   * to the root of the dependency chain.
   *
   * In order to conserve memory, the owner of a Link (a DefaultPromise) is not stored
   * on the Link, but is instead passed in as a parameter to the operation(s).
   *
   * If when compressing a chain of Links it is discovered that the root has been completed,
   * the `owner`'s value is completed with that value, and the Link chain is discarded.
   * */
  final private[util] class Link[T](to: DefaultPromise[T]) extends AtomicReference[DefaultPromise[T]](to) {

    /**
     * Compresses this chain and returns the currently known root of this chain of Links.
     * */
    final def promise(owner: DefaultPromise[T]): DefaultPromise[T] = {
      val c = get()
      compressed(current = c, target = c, owner = owner)
    }

    /**
     * The combination of traversing and possibly unlinking of a given `target` DefaultPromise.
     * */
    @inline
    @tailrec final private[this] def compressed(
                                                 current: DefaultPromise[T],
                                                 target: DefaultPromise[T],
                                                 owner: DefaultPromise[T]
                                               ): DefaultPromise[T] = {
      val value = target.get()
      if (value.isInstanceOf[Callbacks[_]]) {
        if (compareAndSet(current, target)) target // Link
        else compressed(current = get(), target = target, owner = owner) // Retry
      } else if (value.isInstanceOf[Link[_]])
        compressed(current = current, target = value.asInstanceOf[Link[T]].get(), owner = owner) // Compress
      else
      /*if (value.isInstanceOf[Try[T]])*/ {
        owner.unlink(value.asInstanceOf[Try[T]]) // Discard links
        owner
      }
    }
  }

  /**
   * The process of "resolving" a Try is to validate that it only contains
   * those values which makes sense in the context of Futures.
   * */
  // requireNonNull is paramount to guard against null completions
  final private[this] def resolve[T](value: Try[T]): Try[T] =
    if (requireNonNull(value).isInstanceOf[Success[T]]) value
    else {
      val t = value.asInstanceOf[Failure[T]].exception
      if (t.isInstanceOf[ControlThrowable] || t.isInstanceOf[InterruptedException] || t.isInstanceOf[Error]) {
        if (t.isInstanceOf[NonLocalReturnControl[T@unchecked]])
          Success(t.asInstanceOf[NonLocalReturnControl[T]].value)
        else
          Failure(new ExecutionException("Boxed Exception", t))
      } else value
    }

  // Left non-final to enable addition of extra fields by Java/Scala converters in scala-java8-compat.
  class DefaultPromise[T] private[this](initial: AnyRef, context: ContextHolderThreadLocalHolder)
    extends AtomicReference[AnyRef](initial)
      with scala.concurrent.Promise[T]
      with Future[T]
      with (Try[T] => Unit) {

    protected val atomicContext = new AtomicReference[ContextHolderThreadLocalHolder](context)

    /**
     * Constructs a new, completed, Promise.
     */
    final def this(result: Try[T]) = this(resolve(result): AnyRef, ContextHolderThreadLocalHolder.readContext)

    final def this(result: Try[T], context: ContextHolderThreadLocalHolder) = this(resolve(result): AnyRef, context)

    /**
     * Constructs a new, un-completed, Promise.
     */
    final def this() = this(Noop: AnyRef, ContextHolderThreadLocalHolder())

    /**
     * WARNING: the `resolved` value needs to have been pre-resolved using `resolve()`
     * INTERNAL API
     */
    final override def apply(resolved: Try[T]): Unit =
      tryComplete0(get(), resolved, atomicContext.get())

    /**
     * Returns the associated `Future` with this `Promise`
     */
    final override def future: Future[T] = this

    final override def transform[S](f: Try[T] => Try[S])(implicit executor: ExecutionContext): Future[S] =
      dispatchOrAddCallbacks(get(), new Transformation[T, S](Xform_transform, f, executor), context)

    final override def transformWith[S](f: Try[T] => ClassicFuture[S])(implicit executor: ExecutionContext): ClassicFuture[S] =
      dispatchOrAddCallbacks(get(), new Transformation[T, S](Xform_transformWith, f, executor), context)

    def zipWith[U, R](that: Future[U])(f: (T, U) => R)(implicit executor: ExecutionContext): Future[R] = {
      val state = get()
      if (state.isInstanceOf[Try[_]]) {
        if (state.asInstanceOf[Try[T]].isFailure) this.asInstanceOf[Future[R]]
        else {
          val l = state.asInstanceOf[Success[T]].get
          that.map(r => f(l, r))
        }
      } else {
        val buffer = new AtomicReference[Success[Any]]()
        val zipped = new DefaultPromise[R]()

        val thisF: Try[T] => Unit = {
          case left: Success[_] =>
            val right = buffer.getAndSet(left).asInstanceOf[Success[U]]
            if (right ne null)
              zipped.tryComplete(
                try Success(f(left.get, right.get))
                catch {
                  case e if NonFatal(e) => Failure(e)
                }
              )
          case f => // Can only be Failure
            zipped.tryComplete(f.asInstanceOf[Failure[R]])
        }

        val thatF: Try[U] => Unit = {
          case right: Success[_] =>
            val left = buffer.getAndSet(right).asInstanceOf[Success[T]]
            if (left ne null)
              zipped.tryComplete(
                try Success(f(left.get, right.get))
                catch {
                  case e if NonFatal(e) => Failure(e)
                }
              )
          case f => // Can only be Failure
            zipped.tryComplete(f.asInstanceOf[Failure[R]])
        }
        // Cheaper than this.onComplete since we already polled the state
        this.dispatchOrAddCallbacks(state, new Transformation[T, Unit](Xform_onComplete, thisF, executor), context)
        that.onComplete(thatF)
        zipped.future
      }
    }

    final override def foreach[U](f: T => U)(implicit executor: ExecutionContext): Unit = {
      val state = get()
      val context = atomicContext.get()
      if (!state.isInstanceOf[Failure[_]]) dispatchOrAddCallbacks(state, new Transformation[T, Unit](Xform_foreach, f, executor), context)
    }

    final override def flatMap[S](f: T => ClassicFuture[S])(implicit executor: ExecutionContext): ClassicFuture[S] = {
      val state = get()
      val context = atomicContext.get()
      if (!state.isInstanceOf[Failure[_]]) dispatchOrAddCallbacks(state, new Transformation[T, S](Xform_flatMap, f, executor), context)
      else this.asInstanceOf[Future[S]]
    }

    final override def map[S](f: T => S)(implicit executor: ExecutionContext): Future[S] = {
      val state = get()
      val context = atomicContext.get()
      if (!state.isInstanceOf[Failure[_]]) dispatchOrAddCallbacks(state, new Transformation[T, S](Xform_map, f, executor), context)
      else this.asInstanceOf[Future[S]]
    }

    final override def filter(p: T => Boolean)(implicit executor: ExecutionContext): Future[T] = {
      val state = get()
      val context = atomicContext.get()
      if (!state.isInstanceOf[Failure[_]])
        dispatchOrAddCallbacks(state, new Transformation[T, T](Xform_filter, p, executor), context) // Short-circuit if we get a Success
      else this
    }

    final override def collect[S](pf: PartialFunction[T, S])(implicit executor: ExecutionContext): Future[S] = {
      val state = get()
      val context = atomicContext.get()
      if (!state.isInstanceOf[Failure[_]])
        dispatchOrAddCallbacks(state, new Transformation[T, S](Xform_collect, pf, executor), context) // Short-circuit if we get a Success
      else this.asInstanceOf[Future[S]]
    }

    final override def recoverWith[U >: T](pf: PartialFunction[Throwable, ClassicFuture[U]])(implicit executor: ExecutionContext): ClassicFuture[U] = {
      val state = get()
      val context = atomicContext.get()
      if (!state.isInstanceOf[Success[_]])
        dispatchOrAddCallbacks(state, new Transformation[T, U](Xform_recoverWith, pf, executor), context) // Short-circuit if we get a Failure
      else this.asInstanceOf[Future[U]]
    }

    final override def recover[U >: T](pf: PartialFunction[Throwable, U])(implicit executor: ExecutionContext): Future[U] = {
      val state = get()
      val context = atomicContext.get()
      if (!state.isInstanceOf[Success[_]])
        dispatchOrAddCallbacks(state, new Transformation[T, U](Xform_recover, pf, executor), context) // Short-circuit if we get a Failure
      else this.asInstanceOf[Future[U]]
    }

    final override def mapTo[S](implicit tag: scala.reflect.ClassTag[S]): Future[S] =
      if (!get().isInstanceOf[Failure[_]]) super[Future].mapTo[S](tag) // Short-circuit if we get a Success
      else this.asInstanceOf[Future[S]]

    final override def onComplete[U](func: Try[T] => U)(implicit executor: ExecutionContext): Unit =
      dispatchOrAddCallbacks(get(), new Transformation[T, Unit](Xform_onComplete, func, executor), atomicContext.get())

    final override def failed: ClassicFuture[Throwable] =
      if (!get().isInstanceOf[Success[_]]) super.failed
      else ClassicFuture.failed(new NoSuchElementException("Future.failed not completed with a throwable.") with NoStackTrace) // Cached instance in case of already known success

    @tailrec final override def toString: String = {
      val state = get()
      if (state.isInstanceOf[Try[_]]) "Future(" + state + ")"
      else if (state.isInstanceOf[Link[_]]) state.asInstanceOf[Link[T]].promise(this).toString
      else /*if (state.isInstanceOf[Callbacks[T]]) */ "Future(<not completed>)"
    }

    final private[this] def tryAwait0(atMost: Duration): Try[T] =
      if (atMost ne Duration.Undefined) {
        val v = value0
        if (v ne null) v
        else {
          val r =
            if (atMost <= Duration.Zero) null
            else {
              val l = new CompletionLatch[T]()
              onComplete(l)(ExecutionContext.parasitic)

              if (atMost.isFinite)
                l.tryAcquireSharedNanos(1, atMost.toNanos)
              else
                l.acquireSharedInterruptibly(1)

              l.result
            }
          if (r ne null) r
          else throw new TimeoutException("Future timed out after [" + atMost + "]")
        }
      } else throw new IllegalArgumentException("Cannot wait for Undefined duration of time")

    @throws(classOf[TimeoutException])
    @throws(classOf[InterruptedException])
    final def ready(atMost: Duration)(implicit permit: CanAwait): this.type = {
      tryAwait0(atMost)
      this
    }

    @throws(classOf[Exception])
    final def result(atMost: Duration)(implicit permit: CanAwait): T =
      tryAwait0(atMost).get // returns the value, or throws the contained exception

    final override def isCompleted: Boolean = value0 ne null

    final override def value: Option[Try[T]] = Option(value0)

    @tailrec // returns null if not completed
    final private def value0: Try[T] = {
      val state = get()
      if (state.isInstanceOf[Try[_]]) state.asInstanceOf[Try[T]]
      else if (state.isInstanceOf[Link[_]]) state.asInstanceOf[Link[T]].promise(this).value0
      else /*if (state.isInstanceOf[Callbacks[T]])*/ null
    }

    final override def tryComplete(value: Try[T]): Boolean = {
      val state = get()
      if (state.isInstanceOf[Try[_]]) false
      // TODO: Context should be new or current?
      else tryComplete0(state, resolve(value), ContextHolderThreadLocalHolder())
    }

    @tailrec // WARNING: important that the supplied Try really is resolve():d
    final private[Promise] def tryComplete0(
                                             state: AnyRef,
                                             resolved: Try[T],
                                             context: ContextHolderThreadLocalHolder
                                           ): Boolean =
      if (state.isInstanceOf[Callbacks[_]]) {
        if (compareAndSet(state, resolved)) {
          atomicContext.set(context)
          if (state ne Noop) submitWithValue(state.asInstanceOf[Callbacks[T]], resolved, context)
          true
        } else tryComplete0(get(), resolved, context)
      } else if (state.isInstanceOf[Link[_]]) {
        val p = state.asInstanceOf[Link[T]].promise(this) // If this returns owner/this, we are in a completed link
        (p ne this) && p.tryComplete0(p.get(), resolved, context) // Use this to get tailcall optimization and avoid re-resolution
      } else /* if(state.isInstanceOf[Try[T]]) */ false

    def completeWith(other: Future[T]): this.type = {
      if (other ne this) {
        val state = get()
        if (!state.isInstanceOf[Try[_]]) {
          val resolved = if (other.isInstanceOf[DefaultPromise[_]]) other.asInstanceOf[DefaultPromise[T]].value0 else other.value.orNull
          if (resolved ne null) tryComplete0(state, resolved, context)
          else other.onComplete(this)(ExecutionContext.parasitic)
        }
      }

      this
    }

    /** Tries to add the callback, if already completed, it dispatches the callback to be executed.
     * Used by `onComplete()` to add callbacks to a promise and by `link()` to transfer callbacks
     * to the root promise when linking two promises together.
     */
    @tailrec final private def dispatchOrAddCallbacks[C <: Callbacks[T]](
                                                                          state: AnyRef,
                                                                          callbacks: C,
                                                                          context: ContextHolderThreadLocalHolder
                                                                        ): C =
      if (state.isInstanceOf[Try[_]]) {
        submitWithValue(callbacks, state.asInstanceOf[Try[T]], context) // invariant: callbacks should never be Noop here
        callbacks
      } else if (state.isInstanceOf[Callbacks[_]]) {
        if (compareAndSet(state, if (state ne Noop) concatCallbacks(callbacks, state.asInstanceOf[Callbacks[T]]) else callbacks)) callbacks
        else dispatchOrAddCallbacks(get(), callbacks, context)
      } else
      /*if (state.isInstanceOf[Link[T]])*/ {
        val p = state.asInstanceOf[Link[T]].promise(this)
        p.dispatchOrAddCallbacks(p.get(), callbacks, context)
      }

    // IMPORTANT: Noop should never be passed in here, neither as left OR as right
    @tailrec final private[this] def concatCallbacks(
                                                      left: Callbacks[T],
                                                      right: Callbacks[T]
                                                    ): Callbacks[T] =
      if (left.isInstanceOf[Transformation[T, _]]) new ManyCallbacks[T](left.asInstanceOf[Transformation[T, _]], right)
      else
      /*if (left.isInstanceOf[ManyCallbacks[T]) */ { // This should only happen when linking
        val m = left.asInstanceOf[ManyCallbacks[T]]
        concatCallbacks(m.rest, new ManyCallbacks(m.first, right))
      }

    // IMPORTANT: Noop should not be passed in here, `callbacks` cannot be null
    @tailrec
    final private[this] def submitWithValue(
                                             callbacks: Callbacks[T],
                                             resolved: Try[T],
                                             context: ContextHolderThreadLocalHolder
                                           ): Unit =
      if (callbacks.isInstanceOf[ManyCallbacks[T]]) {
        val m: ManyCallbacks[T] = callbacks.asInstanceOf[ManyCallbacks[T]]
        m.first.submitWithValue(resolved, context)
        submitWithValue(m.rest, resolved, context)
      } else {
        callbacks.asInstanceOf[Transformation[T, _]].submitWithValue(resolved, context)
      }

    /** Link this promise to the root of another promise.
     */
    @tailrec final private[util] def linkRootOf(
                                                 target: DefaultPromise[T],
                                                 link: Link[T]
                                               ): Unit =
      if (this ne target) {
        val state = get()
        //        val context = atomicContext.get
        if (state.isInstanceOf[Try[_]]) {
          val context = atomicContext.get()
          if (!target.tryComplete0(target.get(), state.asInstanceOf[Try[T]], context))
            throw new IllegalStateException("Cannot link completed promises together")
        } else if (state.isInstanceOf[Callbacks[_]]) {
          val l = if (link ne null) link else new Link(target)
          val p = l.promise(this)
          if ((this ne p) && compareAndSet(state, l)) {
            if (state ne Noop) p.dispatchOrAddCallbacks(p.get(), state.asInstanceOf[Callbacks[T]], atomicContext.get) // Noop-check is important here
          } else linkRootOf(p, l)
        } else
        /* if (state.isInstanceOf[Link[T]]) */
          state.asInstanceOf[Link[T]].promise(this).linkRootOf(target, link)
      }

    /**
     * Unlinks (removes) the link chain if the root is discovered to be already completed,
     * and completes the `owner` with that result.
     * */
    @tailrec final private[util] def unlink(resolved: Try[T]): Unit = {
      val state = get()
      if (state.isInstanceOf[Link[_]]) {
        val next = if (compareAndSet(state, resolved)) state.asInstanceOf[Link[T]].get() else this
        next.unlink(resolved)
      } else tryComplete0(state, resolved, context)
    }

    @throws[IOException]
    private def writeObject(out: ObjectOutputStream): Unit =
      throw new NotSerializableException("Promises and Futures cannot be serialized")

    @throws[IOException]
    @throws[ClassNotFoundException]
    private def readObject(in: ObjectInputStream): Unit =
      throw new NotSerializableException("Promises and Futures cannot be deserialized")
  }

  // Constant byte tags for unpacking transformation function inputs or outputs
  // These need to be Ints to get compiled into constants.
  final val Xform_noop = 0
  final val Xform_map = 1
  final val Xform_flatMap = 2
  final val Xform_transform = 3
  final val Xform_transformWith = 4
  final val Xform_foreach = 5
  final val Xform_onComplete = 6
  final val Xform_recover = 7
  final val Xform_recoverWith = 8
  final val Xform_filter = 9
  final val Xform_collect = 10

  /* Marker trait
   */
  sealed trait Callbacks[-T]

  final class ManyCallbacks[-T](
                                 final val first: Transformation[T, _],
                                 final val rest: Callbacks[T]
                               ) extends Callbacks[T] {
    final override def toString: String = "ManyCallbacks"
  }

  final private[this] val Noop = new Transformation[Nothing, Nothing](Xform_noop, null, ExecutionContext.parasitic)

  /**
   * A Transformation[F, T] receives an F (it is a Callback[F]) and applies a transformation function to that F,
   * Producing a value of type T (it is a Promise[T]).
   * In order to conserve allocations, indirections, and avoid introducing bi/mega-morphicity the transformation
   * function's type parameters are erased, and the _xform tag will be used to reify them.
   * */
  final class Transformation[-F, T] private[this](
                                                   final private[this] var _fun: Any => Any,
                                                   final private[this] var _ec: ExecutionContext,
                                                   final private[this] var _arg: Try[F],
                                                   final private[this] val _xform: Int,
                                                   final private[this] var _context: ContextHolderThreadLocalHolder
                                                 ) extends DefaultPromise[T]()
    with Callbacks[F]
    with Runnable
    with Batchable {
    final def this(
                    xform: Int,
                    f: _ => _,
                    ec: ExecutionContext
                  ) =
      this(f.asInstanceOf[Any => Any], ec.prepare(): @nowarn("cat=deprecation"), null, xform, null)

    final def benefitsFromBatching: Boolean = _xform != Xform_onComplete && _xform != Xform_foreach

    // Gets invoked when a value is available, schedules it to be run():ed by the ExecutionContext
    // submitWithValue *happens-before* run(), through ExecutionContext.execute.
    // Invariant: _arg is `null`, _ec is non-null. `this` ne Noop.
    // requireNonNull(resolved) will hold as guarded by `resolve`
    final def submitWithValue(resolved: Try[F], context: ContextHolderThreadLocalHolder): this.type = {
      _arg = resolved
      _context = context
      val e = _ec
      try e.execute(this) /* Safe publication of _arg, _fun, _ec */
      catch {
        case t: Throwable =>
          _fun = null // allow to GC
          _arg = null // see above
          _ec = null // see above again
          _context = null
          handleFailure(t, e, context)
      }

      this
    }

    final private[this] def handleFailure(
                                           t: Throwable,
                                           e: ExecutionContext,
                                           c: ContextHolderThreadLocalHolder
                                         ): Unit = {
      val wasInterrupted = t.isInstanceOf[InterruptedException]
      if (wasInterrupted || NonFatal(t)) {
        val completed = tryComplete0(get(), resolve(Failure(t)), c)
        if (completed && wasInterrupted) Thread.currentThread.interrupt()

        // Report or rethrow failures which are unlikely to otherwise be noticed
        if (_xform == Xform_foreach || _xform == Xform_onComplete || !completed)
          e.reportFailure(t)
      } else throw t
    }

    // Gets invoked by the ExecutionContext, when we have a value to transform.
    final override def run(): Unit = {
      val v = _arg
      val fun = _fun
      val ec = _ec
      val oldState = ContextHolderThreadLocalHolder.readContext
      val newCopy = _context.copy
      _fun = null // allow to GC
      _arg = null // see above
      _ec = null // see above
      _context = null
      //      ContextHolderThreadLocalHolder.ContextHolderThreadLocalHolder.set(newCopy)
      newCopy.inject
      atomicContext.set(newCopy)
      try {
        val resolvedResult: Try[_] =
          (_xform: @switch) match {
            case Xform_noop =>
              null
            case Xform_map =>
              if (v.isInstanceOf[Success[F]]) Success(fun(v.get)) else v // Faster than `resolve(v map fun)`
            case Xform_flatMap =>
              if (v.isInstanceOf[Success[F]]) {
                val f = fun(v.get)
                if (f.isInstanceOf[DefaultPromise[_]]) f.asInstanceOf[DefaultPromise[T]].linkRootOf(this, null)
                else completeWith(f.asInstanceOf[Future[T]])
                null
              } else v
            case Xform_transform =>
              resolve(fun(v).asInstanceOf[Try[T]])
            case Xform_transformWith =>
              val f = fun(v)
              if (f.isInstanceOf[DefaultPromise[_]]) f.asInstanceOf[DefaultPromise[T]].linkRootOf(this, null)
              else completeWith(f.asInstanceOf[Future[T]])
              null
            case Xform_foreach =>
              v.foreach(fun)
              null
            case Xform_onComplete =>
              fun(v)
              null
            case Xform_recover =>
              if (v.isInstanceOf[Failure[_]]) resolve(v.recover(fun.asInstanceOf[PartialFunction[Throwable, F]])) else v //recover F=:=T
            case Xform_recoverWith =>
              if (v.isInstanceOf[Failure[F]]) {
                val f = fun
                  .asInstanceOf[PartialFunction[Throwable, Future[T]]]
                  .applyOrElse(v.asInstanceOf[Failure[F]].exception, Future.recoverWithFailed)
                if (f ne Future.recoverWithFailedMarker) {
                  if (f.isInstanceOf[DefaultPromise[T]]) f.asInstanceOf[DefaultPromise[T]].linkRootOf(this, null)
                  else completeWith(f.asInstanceOf[Future[T]])
                  null
                } else v
              } else v
            case Xform_filter =>
              if (v.isInstanceOf[Failure[F]] || fun.asInstanceOf[F => Boolean](v.get)) v else Future.filterFailure
            case Xform_collect =>
              if (v.isInstanceOf[Success[F]]) Success(fun.asInstanceOf[PartialFunction[F, T]].applyOrElse(v.get, Future.collectFailed))
              else v
            case _ =>
              Failure(
                new IllegalStateException("BUG: encountered transformation promise with illegal type: " + _xform)
              ) // Safe not to `resolve`
          }
        //        ContextHolderThreadLocalHolder.ContextHolderThreadLocalHolder.remove()
//        newCopy.clean
        // TODO: return old value need after task completion
//        oldState.inject
//        println(s"[Future.run][xform:${_xform}][${Thread.currentThread().getName}] new copy is ${newCopy}")
        if (resolvedResult ne null)
          tryComplete0(get(), resolvedResult.asInstanceOf[Try[T]], newCopy) // T is erased anyway so we won't have any use for it above
      } catch {
        case t: Throwable => handleFailure(t, ec, newCopy)
      }
    }
  }
}
