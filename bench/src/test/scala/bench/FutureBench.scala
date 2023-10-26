package bench

import org.openjdk.jmh.annotations.{Benchmark, Level, Param, Scope, Setup, State, TearDown}
import org.tayvs.util.{Future => MyFuture}

import java.util.concurrent.{ExecutorService, TimeUnit}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future => SFuture}
import scala.util.{Success, Try}

@State(Scope.Benchmark)
class FutureBench {

  @Param(Array("singleThread", "global"))
  final var pool: String = _

  var executor: ExecutionContext = _

  @Setup(Level.Trial)
  def startup(): Unit = {
    executor = pool match {
      case "singleThread" => ExecutionContext.fromExecutorService(java.util.concurrent.Executors.newSingleThreadExecutor())
      case "global" => scala.concurrent.ExecutionContext.fromExecutorService(null)
    }
  }

  @TearDown(Level.Trial)
  final def shutdown: Unit = {
    executor = executor match {
      case es: ExecutorService =>
        try es.shutdown() finally es.awaitTermination(1, TimeUnit.MINUTES)
        null
      case _ => null
    }
  }

  @Benchmark
  def sFuture_successful(): Unit = await(SFuture.successful(12))

  @Benchmark
  def myFuture_successful(): Unit = await(MyFuture.successful(12))

  @Benchmark
  def sFuture_successful_map(): Unit = await(SFuture.successful(12).map(_ + 1)(executor))

  @Benchmark
  def myFuture_successful_map(): Unit = await(MyFuture.successful(12).map(_ + 1)(executor))


  final def await[T](a: SFuture[T]): Boolean = {
    var r: Option[Try[T]] = None
    do {
      r = a.value
    } while (r eq None);
    r.get.isInstanceOf[Success[T]]
  }
}
