package zio
package interop.guava

import com.google.common.util.concurrent.{Futures, ListenableFuture}
import zio.test.Assertion._
import zio.test._

import java.util.concurrent.Executors
import java.util.{concurrent => juc}

object GuavaSpec extends ZIOSpecDefault {
  def spec: Spec[Environment, Any] = suite("GuavaSpec")(
    suite("`ZIO.fromListenableFuture` must")(
      test("be lazy on the `Future` parameter") {
        var evaluated                   = false
        def ftr: ListenableFuture[Unit] =
          Futures.submitAsync(
            { () =>
              evaluated = true
              Futures.immediateFuture(())
            },
            Executors.newCachedThreadPool()
          )
        assertZIO(ZIO.fromListenableFuture(ZIO.succeed(ftr)).when(false).as(evaluated))(isFalse)
      },
      test("catch exceptions thrown by make block") {
        val ex                                                    = new Exception("no future for you!")
        lazy val noFuture: juc.Executor => ListenableFuture[Unit] = _ => throw ex
        assertZIO(ZIO.fromListenableFuture(noFuture).exit)(fails(equalTo(ex)))
      },
      test("return an `IO` that fails if `Future` fails 1") {
        val ex                                   = new Exception("no value for you!")
        val noValue: UIO[ListenableFuture[Unit]] = ZIO.succeed(Futures.immediateFailedFuture(ex))
        assertZIO(ZIO.fromListenableFuture(noValue).exit)(fails[Throwable](equalTo(ex)))
      },
      test("return an `IO` that fails if `Future` fails 2") {
        val ex                                   = new Exception("no value for you!")
        val noValue: UIO[ListenableFuture[Unit]] =
          ZIO.succeed(Futures.submitAsync(() => Futures.immediateFailedFuture(ex), Executors.newCachedThreadPool()))
        assertZIO(ZIO.fromListenableFuture(noValue).exit)(fails[Throwable](equalTo(ex)))
      },
      test("return an `IO` that produces the value from `Future`") {
        val someValue: UIO[ListenableFuture[Int]] =
          ZIO.succeed(Futures.submitAsync(() => Futures.immediateFuture(42), Executors.newCachedThreadPool()))
        assertZIO(ZIO.fromListenableFuture(someValue).exit)(succeeds(equalTo(42)))
      },
      test("handle null produced by the completed `Future`") {
        val someValue: UIO[ListenableFuture[String]] = ZIO.succeed(Futures.immediateFuture[String](null))
        assertZIO(ZIO.fromListenableFuture[String](someValue).map(Option(_)))(isNone)
      },
      test("be referentially transparent") {
        var n    = 0
        val task = ZIO.fromListenableFuture(
          ZIO.succeed(Futures.submitAsync(() => Futures.immediateFuture(n += 1), Executors.newCachedThreadPool()))
        )
        for {
          _ <- task
          _ <- task
        } yield assert(n)(equalTo(2))
      }
    ),
    suite("`ZIO.toListenableFuture` must")(
      test("produce always a successful `IO` of `Future`") {
        val failedIO = ZIO.fail[Throwable](new Exception("IOs also can fail"))
        assertZIO(failedIO.toListenableFuture)(isSubtype[ListenableFuture[Unit]](anything))
      },
      test("be polymorphic in error type") {
        val unitIO: Task[Unit]                         = ZIO.unit
        val polyIO: IO[String, ListenableFuture[Unit]] = unitIO.toListenableFuture
        val _                                          = polyIO // avoid warning
        assert(polyIO)(anything)
      },
      test("return a `ListenableFuture` that fails if `IO` fails") {
        val ex                       = new Exception("IOs also can fail")
        val failedIO: Task[Unit]     = ZIO.fail[Throwable](ex)
        val failedFuture: Task[Unit] = failedIO.toListenableFuture.flatMap(f => ZIO.attempt(f.get()))
        assertZIO(failedFuture.exit)(
          fails[Throwable](hasField("message", _.getMessage, equalTo("java.lang.Exception: IOs also can fail")))
        )
      },
      test("return a `ListenableFuture` that produces the value from `IO`") {
        val someIO = ZIO.succeed[Int](42)
        assertZIO(someIO.toListenableFuture.map(_.get()))(equalTo(42))
      }
    ),
    suite("`ZIO.toListenableFutureWith` must")(
      test("convert error of type `E` to `Throwable`") {
        val failedIO: IO[String, Unit] = ZIO.fail[String]("IOs also can fail")
        val failedFuture: Task[Unit]   =
          failedIO.toListenableFutureWith(new Exception(_)).flatMap(f => ZIO.attempt(f.get()))
        assertZIO(failedFuture.exit)(
          fails[Throwable](hasField("message", _.getMessage, equalTo("java.lang.Exception: IOs also can fail")))
        )
      }
    ),
    suite("`Fiber.fromListenableFuture` must")(
      test("be lazy on the `Future` parameter") {
        var evaluated                   = false
        def ftr: ListenableFuture[Unit] =
          Futures.submitAsync(
            { () =>
              evaluated = true
              Futures.immediateFuture(())
            },
            Executors.newCachedThreadPool()
          )
        Fiber.fromListenableFuture(ftr)
        assert(evaluated)(isFalse)
      },
      test("catch exceptions thrown by lazy block") {
        val ex                               = new Exception("no future for you!")
        def noFuture: ListenableFuture[Unit] = throw ex
        assertZIO(Fiber.fromListenableFuture(noFuture).join.exit)(fails(equalTo(ex)))
      },
      test("return an `IO` that fails if `Future` fails 1") {
        val ex                              = new Exception("no value for you!")
        def noValue: ListenableFuture[Unit] = Futures.immediateFailedFuture(ex)
        assertZIO(Fiber.fromListenableFuture(noValue).join.exit)(fails[Throwable](equalTo(ex)))
      },
      test("return an `IO` that fails if `Future` fails 2") {
        val ex                              = new Exception("no value for you!")
        def noValue: ListenableFuture[Unit] =
          Futures.submitAsync(() => Futures.immediateFailedFuture(ex), Executors.newCachedThreadPool())
        assertZIO(Fiber.fromListenableFuture(noValue).join.exit)(fails[Throwable](equalTo(ex)))
      },
      test("return an `IO` that produces the value from `Future`") {
        def someValue: ListenableFuture[Int] = Futures.immediateFuture(42)
        assertZIO(Fiber.fromListenableFuture(someValue).join.exit)(succeeds(equalTo(42)))
      }
    )
  )
}
