package zio
package interop

import java.util.concurrent.Executors

import com.google.common.util.concurrent.{ Futures, ListenableFuture }
import org.specs2.concurrent.ExecutionEnv
import zio.Cause.{ die, fail }
import zio.interop.guava._

class guavaSpec(implicit ee: ExecutionEnv) extends TestRuntime {

  def is = s2"""
  `Task.fromListenableFuture` must
    be lazy on the `Future` parameter                    $lazyOnParamRef
    catch exceptions thrown by lazy block                $catchBlockException
    return an `IO` that fails if `Future` fails 1        $propagateExceptionFromFuture1
    return an `IO` that fails if `Future` fails 2        $propagateExceptionFromFuture2
    return an `IO` that produces the value from `Future` $produceValueFromFuture
    handle null produced by the completed `Future`       $handleNullFromFuture
  `Task.toListenableFuture` must
    produce always a successful `IO` of `Future`         $toListenableFutureAlwaysSucceeds
    be polymorphic in error type                         $toListenableFuturePoly
    return a `ListenableFuture` that fails if `IO` fails           $toListenableFutureFailed
    return a `ListenableFuture` that produces the value from `IO`  $toListenableFutureValue
  `Task.toListenableFutureWith` must
    convert error of type `E` to `Throwable`             $toListenableFutureWith
  `Fiber.fromListenableFuture` must
    be lazy on the `Future` parameter                    $lazyOnParamRefFiber
    catch exceptions thrown by lazy block                $catchBlockExceptionFiber
    return an `IO` that fails if `Future` fails 1        $propagateExceptionFromFutureFiber1
    return an `IO` that fails if `Future` fails 2        $propagateExceptionFromFutureFiber2
    return an `IO` that produces the value from `Future` $produceValueFromFutureFiber
  """

  def lazyOnParamRef = {
    var evaluated = false
    def ftr: ListenableFuture[Unit] =
      Futures.submitAsync({ () =>
        evaluated = true
        Futures.immediateFuture(())
      }, Executors.newCachedThreadPool())
    Task.fromListenableFuture(UIO.succeedLazy(ftr))
    evaluated must beFalse
  }

  def catchBlockException = {
    val ex                                    = new Exception("no future for you!")
    val noFuture: UIO[ListenableFuture[Unit]] = UIO.succeedLazy(throw ex)
    unsafeRunSync(Task.fromListenableFuture(noFuture)) must_=== Exit.Failure(die(ex))
  }

  def propagateExceptionFromFuture1 = {
    val ex                                   = new Exception("no value for you!")
    val noValue: UIO[ListenableFuture[Unit]] = UIO.succeedLazy(Futures.immediateFailedFuture(ex))
    unsafeRunSync(Task.fromListenableFuture(noValue)) must_=== Exit.Failure(fail(ex))
  }

  def propagateExceptionFromFuture2 = {
    val ex = new Exception("no value for you!")
    val noValue: UIO[ListenableFuture[Unit]] = UIO.succeedLazy(Futures.submitAsync({ () =>
      Futures.immediateFailedFuture(ex)
    }, Executors.newCachedThreadPool()))
    unsafeRunSync(Task.fromListenableFuture(noValue)) must_=== Exit.Failure(fail(ex))
  }

  def produceValueFromFuture = {
    val someValue: UIO[ListenableFuture[Int]] = UIO.succeedLazy(Futures.submitAsync({ () =>
      Futures.immediateFuture(42)
    }, Executors.newCachedThreadPool()))
    unsafeRun(Task.fromListenableFuture(someValue)) must_=== 42
  }

  def handleNullFromFuture = {
    val someValue: UIO[ListenableFuture[String]] = UIO.succeedLazy(Futures.immediateFuture[String](null))
    unsafeRun(Task.fromListenableFuture[String](someValue)) must_=== null
  }

  def toListenableFutureAlwaysSucceeds = {
    val failedIO = IO.fail[Throwable](new Exception("IOs also can fail"))
    unsafeRun(failedIO.toListenableFuture) must beAnInstanceOf[ListenableFuture[Unit]]
  }

  def toListenableFuturePoly = {
    val unitIO: Task[Unit]                         = Task.unit
    val polyIO: IO[String, ListenableFuture[Unit]] = unitIO.toListenableFuture
    val _                                          = polyIO // avoid warning
    ok
  }

  def toListenableFutureFailed = {
    val failedIO: Task[Unit] = IO.fail[Throwable](new Exception("IOs also can fail"))
    unsafeRun(failedIO.toListenableFuture).get() must throwA[Exception](message = "IOs also can fail")
  }

  def toListenableFutureValue = {
    val someIO = Task.succeed[Int](42)
    unsafeRun(someIO.toListenableFuture).get() must beEqualTo(42)
  }

  def toListenableFutureWith = {
    val failedIO: IO[String, Unit] = IO.fail[String]("IOs also can fail")
    unsafeRun(failedIO.toListenableFutureWith(new Exception(_))).get() must throwA[Exception](
      message = "IOs also can fail"
    )
  }

  def lazyOnParamRefFiber = {
    var evaluated = false
    def ftr: ListenableFuture[Unit] =
      Futures.submitAsync({ () =>
        evaluated = true
        Futures.immediateFuture(())
      }, Executors.newCachedThreadPool())
    Fiber.fromListenableFuture(ftr)
    evaluated must beFalse
  }

  def catchBlockExceptionFiber = {
    val ex                               = new Exception("no future for you!")
    def noFuture: ListenableFuture[Unit] = throw ex
    unsafeRunSync(Fiber.fromListenableFuture(noFuture).join) must_=== Exit.Failure(die(ex))
  }

  def propagateExceptionFromFutureFiber1 = {
    val ex                              = new Exception("no value for you!")
    def noValue: ListenableFuture[Unit] = Futures.immediateFailedFuture(ex)
    unsafeRunSync(Fiber.fromListenableFuture(noValue).join) must_=== Exit.Failure(fail(ex))
  }

  def propagateExceptionFromFutureFiber2 = {
    val ex = new Exception("no value for you!")
    def noValue: ListenableFuture[Unit] =
      Futures.submitAsync({ () =>
        Futures.immediateFailedFuture(ex)
      }, Executors.newCachedThreadPool())
    unsafeRunSync(Fiber.fromListenableFuture(noValue).join) must_=== Exit.Failure(fail(ex))
  }

  def produceValueFromFutureFiber = {
    def someValue: ListenableFuture[Int] = Futures.immediateFuture(42)
    unsafeRun(Fiber.fromListenableFuture(someValue).join) must_=== 42
  }
}
