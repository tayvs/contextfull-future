package bench

//import bench.FutureBench.FutureInitValue
import org.openjdk.jmh.annotations._
import org.tayvs.util.{Future => MyFuture}

import java.util.concurrent.{ExecutorService, TimeUnit}
import scala.concurrent.{ExecutionContext, Future => SFuture}
import scala.util.{Random, Success, Try}

@State(Scope.Benchmark)
class FutureBench {

  @Param(Array("singleThread", "global"))
  final var pool: String = _

  var executor: ExecutionContext = _

  private var initValue = 42
  @Setup
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
  def sFuture_successful(/*init: FutureInitValue*/): Boolean = await(SFuture.successful(initValue))

  @Benchmark
  def myFuture_successful(/*init: FutureInitValue*/): Boolean = await(MyFuture.successful(initValue))

  @Benchmark
  def sFuture_successful_map(/*init: FutureInitValue*/): Boolean = await(SFuture.successful(initValue).map(_ + 1)(executor))

  @Benchmark
  def myFuture_successful_map(/*init: FutureInitValue*/): Boolean = await(MyFuture.successful(initValue).map(_ + 1)(executor))


  final def await[T](a: SFuture[T]): Boolean = {
    var r: Option[Try[T]] = None
    do {
      r = a.value
    } while (r eq None);
    r.get.isInstanceOf[Success[T]]
  }
}

object FutureBench {
//  @State(Scope.Benchmark)
//  class FutureInitValue {
//    var value: Int = Random.nextInt(100_000)
//  }
}
