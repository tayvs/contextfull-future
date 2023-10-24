package org.tayvs.util

import scala.collection.mutable.Builder
import scala.concurrent.ExecutionContext.parasitic
import scala.concurrent.{ExecutionContext, Future => ClassicFuture}
import scala.reflect.ClassTag
import scala.util.control.{NoStackTrace, NonFatal}
import scala.util.{Failure, Success, Try}

// Basically it is delegator
trait Future[T] extends ClassicFuture[T] {

  override def foreach[U](f: T => U)(implicit executor: ExecutionContext): Unit = super.foreach(f)

  def transform[S](f: Try[T] => Try[S])(implicit executor: ExecutionContext): Future[S]

  override def transform[S](s: T => S, f: Throwable => Throwable)(implicit executor: ExecutionContext): Future[S] =
    transform {
      t =>
        if (t.isInstanceOf[Success[T]]) t map s
        else throw f(t.asInstanceOf[Failure[T]].exception) // will throw fatal errors!
    }

  override def transformWith[S](f: Try[T] => ClassicFuture[S])(implicit executor: ExecutionContext): ClassicFuture[S]

  override def map[S](f: T => S)(implicit executor: ExecutionContext): Future[S] = transform(_ map f)

  override def flatMap[S](f: T => ClassicFuture[S])(implicit executor: ExecutionContext): ClassicFuture[S] = transformWith {
    t =>
      if (t.isInstanceOf[Success[T]]) f(t.asInstanceOf[Success[T]].value)
      else this.asInstanceOf[Future[S]] // Safe cast
  }

  override def flatten[S](implicit ev: T <:< ClassicFuture[S]): ClassicFuture[S] = flatMap(ev)(parasitic)

  override def filter(p: T => Boolean)(implicit executor: ExecutionContext): Future[T] =
    transform {
      t =>
        if (t.isInstanceOf[Success[T]]) {
          if (p(t.asInstanceOf[Success[T]].value)) t
          else Future.filterFailure
        } else t
    }

  override def collect[S](pf: PartialFunction[T, S])(implicit executor: ExecutionContext): Future[S] =
    transform {
      t =>
        if (t.isInstanceOf[Success[T]])
          Success(pf.applyOrElse(t.asInstanceOf[Success[T]].value, Future.collectFailed))
        else t.asInstanceOf[Failure[S]]
    }

  override def recover[U >: T](pf: PartialFunction[Throwable, U])(implicit executor: ExecutionContext): Future[U] =
    transform {
      _ recover pf
    }

  override def recoverWith[U >: T](pf: PartialFunction[Throwable, ClassicFuture[U]])(implicit executor: ExecutionContext): ClassicFuture[U] =
    transformWith {
      t =>
        if (t.isInstanceOf[Failure[T]]) {
          val result = pf.applyOrElse(t.asInstanceOf[Failure[T]].exception, Future.recoverWithFailed)
          if (result ne Future.recoverWithFailedMarker) result
          else this
        } else this
    }

  override def zip[U](that: ClassicFuture[U]): ClassicFuture[(T, U)] = zipWith(that)(Future.zipWithTuple2Fun)(parasitic)

  override def zipWith[U, R](that: ClassicFuture[U])(f: (T, U) => R)(implicit executor: ExecutionContext): ClassicFuture[R] = {
    // This is typically overriden by the implementation in DefaultPromise, which provides
    // symmetric fail-fast behavior regardless of which future fails first.
    //
    // TODO: remove this implementation and make Future#zipWith abstract
    //  when we're next willing to make a binary incompatible change
    flatMap(r1 => that.map(r2 => f(r1, r2)))(/*if (executor.isInstanceOf[BatchingExecutor]) executor else*/ parasitic)
  }

  override def fallbackTo[U >: T](that: ClassicFuture[U]): ClassicFuture[U] =
    if (this eq that) this
    else {
      implicit val ec = parasitic
      transformWith {
        t =>
          if (t.isInstanceOf[Success[T]]) this
          else that transform { tt => if (tt.isInstanceOf[Success[U]]) tt else t }
      }
    }

  override def mapTo[S](implicit tag: ClassTag[S]): Future[S] = {
    implicit val ec = parasitic
    val boxedClass = {
      val c = tag.runtimeClass
      if (c.isPrimitive) Future.toBoxed(c) else c
    }
    require(boxedClass ne null)
    map(s => boxedClass.cast(s).asInstanceOf[S])
  }

  override def andThen[U](pf: PartialFunction[Try[T], U])(implicit executor: ExecutionContext): Future[T] =
    transform {
      result =>
        try pf.applyOrElse[Try[T], Any](result, Future.id[Try[T]])
        catch {
          case t if NonFatal(t) => executor.reportFailure(t)
        }
        // TODO: use `finally`?
        result
    }
}

object Future {

  final val toBoxed = Map[Class[_], Class[_]](
    classOf[Boolean] -> classOf[java.lang.Boolean],
    classOf[Byte] -> classOf[java.lang.Byte],
    classOf[Char] -> classOf[java.lang.Character],
    classOf[Short] -> classOf[java.lang.Short],
    classOf[Int] -> classOf[java.lang.Integer],
    classOf[Long] -> classOf[java.lang.Long],
    classOf[Float] -> classOf[java.lang.Float],
    classOf[Double] -> classOf[java.lang.Double],
    classOf[Unit] -> classOf[scala.runtime.BoxedUnit]
  )

  private[this] final val _cachedId: AnyRef => AnyRef = Predef.identity _

  private[util] final def id[T]: T => T = _cachedId.asInstanceOf[T => T]

  private[util] final val collectFailed =
    (t: Any) => throw new NoSuchElementException("Future.collect partial function is not defined at: " + t) with NoStackTrace

  private[util] final val filterFailure =
    Failure[Nothing](new NoSuchElementException("Future.filter predicate is not satisfied") with NoStackTrace)

  private[this] final val failedFailure =
    Failure[Nothing](new NoSuchElementException("Future.failed not completed with a throwable.") with NoStackTrace)

  private[util] final val failedFailureFuture: Future[Nothing] =
    new Promise.DefaultPromise(failedFailure)

  private[this] final val _failedFun: Try[Any] => Try[Throwable] =
    v => if (v.isInstanceOf[Failure[Any]]) Success(v.asInstanceOf[Failure[Any]].exception) else failedFailure

  private[util] final def failedFun[T]: Try[T] => Try[Throwable] = _failedFun.asInstanceOf[Try[T] => Try[Throwable]]

  private[util] final val recoverWithFailedMarker: Future[Nothing] =
    new Promise.DefaultPromise(Failure(new Throwable with NoStackTrace))

  private[util] final val recoverWithFailed = (t: Throwable) => recoverWithFailedMarker

  private[this] final val _zipWithTuple2: (Any, Any) => (Any, Any) = Tuple2.apply _

  private[util] final def zipWithTuple2Fun[T, U] = _zipWithTuple2.asInstanceOf[(T, U) => (T, U)]

  private[this] final val _addToBuilderFun: (Builder[Any, Nothing], Any) => Builder[Any, Nothing] = (b: Builder[Any, Nothing], e: Any) => b += e

  private[util] final def addToBuilderFun[A, M] = _addToBuilderFun.asInstanceOf[Function2[Builder[A, M], A, Builder[A, M]]]

  final val unit: ClassicFuture[Unit] = fromTry(Success(()))

  /** Creates an already completed Future with the specified exception.
   *
   * @tparam T the type of the value in the future
   * @param exception the non-null instance of `Throwable`
   * @return the newly created `Future` instance
   */
  final def failed[T](exception: Throwable): ClassicFuture[T] = Promise.failed(exception).future

  /** Creates an already completed Future with the specified result.
   *
   * @tparam T the type of the value in the future
   * @param result the given successful value
   * @return the newly created `Future` instance
   */
  final def successful[T](result: T): ClassicFuture[T] = Promise.successful(result).future

  /** Creates an already completed Future with the specified result or exception.
   *
   * @tparam T the type of the value in the `Future`
   * @param result the result of the returned `Future` instance
   * @return the newly created `Future` instance
   */
  final def fromTry[T](result: Try[T]): ClassicFuture[T] = Promise.fromTry(result).future

  /** Starts an asynchronous computation and returns a `Future` instance with the result of that computation.
   *
   * The following expressions are equivalent:
   *
   * {{{
   *  val f1 = Future(expr)
   *  val f2 = Future.unit.map(_ => expr)
   *  val f3 = Future.unit.transform(_ => Success(expr))
   *   }}}
   *
   *  The result becomes available once the asynchronous computation is completed.
   *
   *  @tparam T        the type of the result
   *  @param body      the asynchronous computation
   *  @param executor  the execution context on which the future is run
   *  @return          the `Future` holding the result of the computation
   */
  final def apply[T](body: => T)(implicit executor: ExecutionContext): ClassicFuture[T] = {
    // CAn not inject new context into exists Future But can create new future with context that should be propagated further
    val unit = fromTry(Success(()))
//    val context = ContextHolderThreadLocalHolder()
    unit.map { _ =>
//      context.inject
      body
    }
  }


}
