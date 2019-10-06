package zio
package interop

import java.util.concurrent.Executors

import com.google.common.util.concurrent.{ Futures, ListenableFuture }
import zio.interop.guava._
import zio.test.Assertion.{ isFalse, succeeds, _ }
import zio.test.{ DefaultRunnableSpec, assertM, suite, testM, _ }

object GuavaSpec {
  def spec = suite("GuavaSpec")(
    suite("`Task.fromListenableFuture` must")(
      testM("be lazy on the `Future` parameter") {
        var evaluated = false
        def ftr: ListenableFuture[Unit] =
          Futures.submitAsync({ () =>
            evaluated = true
            Futures.immediateFuture(())
          }, Executors.newCachedThreadPool())
        assertM(Task.fromListenableFuture(UIO.effectTotal(ftr)).when(false).as(evaluated), isFalse)
      },
      testM("catch exceptions thrown by lazy block") {
        val ex                                    = new Exception("no future for you!")
        val noFuture: UIO[ListenableFuture[Unit]] = UIO.effectTotal(throw ex)
        assertM(Task.fromListenableFuture(noFuture).run, equalTo[Exit[Throwable, Unit]](Exit.die(ex)))
      },
      testM("return an `IO` that fails if `Future` fails 1") {
        val ex                                   = new Exception("no value for you!")
        val noValue: UIO[ListenableFuture[Unit]] = UIO.effectTotal(Futures.immediateFailedFuture(ex))
        assertM(Task.fromListenableFuture(noValue).run, fails[Throwable](equalTo(ex)))
      },
      testM("return an `IO` that fails if `Future` fails 2") {
        val ex = new Exception("no value for you!")
        val noValue: UIO[ListenableFuture[Unit]] = UIO.effectTotal(Futures.submitAsync({ () =>
          Futures.immediateFailedFuture(ex)
        }, Executors.newCachedThreadPool()))
        assertM(Task.fromListenableFuture(noValue).run, fails[Throwable](equalTo(ex)))
      },
      testM("return an `IO` that produces the value from `Future`") {
        val someValue: UIO[ListenableFuture[Int]] = UIO.effectTotal(Futures.submitAsync({ () =>
          Futures.immediateFuture(42)
        }, Executors.newCachedThreadPool()))
        assertM(Task.fromListenableFuture(someValue).run, succeeds(equalTo(42)))
      },
      testM("handle null produced by the completed `Future`") {
        val someValue: UIO[ListenableFuture[String]] = UIO.effectTotal(Futures.immediateFuture[String](null))
        assertM(Task.fromListenableFuture[String](someValue).run, succeeds(equalTo[String](null)))
      }
    ),
    suite("`Task.toListenableFuture` must")(
      testM("produce always a successful `IO` of `Future`") {
        val failedIO = IO.fail[Throwable](new Exception("IOs also can fail"))
        assertM(failedIO.toListenableFuture, isSubtype[ListenableFuture[Unit]](anything))
      },
      test("be polymorphic in error type") {
        val unitIO: Task[Unit]                         = Task.unit
        val polyIO: IO[String, ListenableFuture[Unit]] = unitIO.toListenableFuture
        val _                                          = polyIO // avoid warning
        assert(polyIO, anything)
      },
      testM("return a `ListenableFuture` that fails if `IO` fails") {
        val ex                       = new Exception("IOs also can fail")
        val failedIO: Task[Unit]     = IO.fail[Throwable](ex)
        val failedFuture: Task[Unit] = failedIO.toListenableFuture.flatMap(f => Task(f.get()))
        assertM(
          failedFuture.run,
          fails[Throwable](hasField("message", _.getMessage, equalTo("java.lang.Exception: IOs also can fail")))
        )
      },
      testM("return a `ListenableFuture` that produces the value from `IO`") {
        val someIO = Task.succeed[Int](42)
        assertM(someIO.toListenableFuture.map(_.get()), equalTo(42))
      }
    ),
    suite("`Task.toListenableFutureWith` must")(
      testM("convert error of type `E` to `Throwable`") {
        val failedIO: IO[String, Unit] = IO.fail[String]("IOs also can fail")
        val failedFuture: Task[Unit]   = failedIO.toListenableFutureWith(new Exception(_)).flatMap(f => Task(f.get()))
        assertM(
          failedFuture.run,
          fails[Throwable](hasField("message", _.getMessage, equalTo("java.lang.Exception: IOs also can fail")))
        )
      }
    ),
    suite("`Fiber.fromListenableFuture` must")(
      test("be lazy on the `Future` parameter") {
        var evaluated = false
        def ftr: ListenableFuture[Unit] =
          Futures.submitAsync({ () =>
            evaluated = true
            Futures.immediateFuture(())
          }, Executors.newCachedThreadPool())
        Fiber.fromListenableFuture(ftr)
        assert(evaluated, isFalse)
      },
      testM("catch exceptions thrown by lazy block") {
        val ex                               = new Exception("no future for you!")
        def noFuture: ListenableFuture[Unit] = throw ex
        assertM(Fiber.fromListenableFuture(noFuture).join.run, equalTo[Exit[Throwable, Unit]](Exit.die(ex)))
      },
      testM("return an `IO` that fails if `Future` fails 1") {
        val ex                              = new Exception("no value for you!")
        def noValue: ListenableFuture[Unit] = Futures.immediateFailedFuture(ex)
        assertM(Fiber.fromListenableFuture(noValue).join.run, fails[Throwable](equalTo(ex)))
      },
      testM("return an `IO` that fails if `Future` fails 2") {
        val ex = new Exception("no value for you!")
        def noValue: ListenableFuture[Unit] =
          Futures.submitAsync({ () =>
            Futures.immediateFailedFuture(ex)
          }, Executors.newCachedThreadPool())
        assertM(Fiber.fromListenableFuture(noValue).join.run, fails[Throwable](equalTo(ex)))
      },
      testM("return an `IO` that produces the value from `Future`") {
        def someValue: ListenableFuture[Int] = Futures.immediateFuture(42)
        assertM(Fiber.fromListenableFuture(someValue).join.run, succeeds(equalTo(42)))
      }
    )
  )
}

object GuavaSpecMain extends DefaultRunnableSpec(GuavaSpec.spec)
