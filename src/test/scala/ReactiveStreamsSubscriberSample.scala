import org.{reactivestreams => rs}

final case class SubscriberZio(runtime: scalaz.zio.Runtime[Any],
                               queue: scalaz.zio.Ref[List[Int]]) extends rs.Subscriber[Int] {

  override def onSubscribe(s: rs.Subscription): Unit = runtime.unsafeRun(scalaz.zio.UIO(s.request(Long.MaxValue)))

  override def onNext(t: Int): Unit = runtime.unsafeRun(queue.update(_ :+ t))

  override def onError(t: Throwable): Unit = runtime.unsafeRun(scalaz.zio.UIO(println(t.getMessage)))

  override def onComplete(): Unit = ()
}

object ReactiveStreamsSubscriberSample extends scalaz.zio.DefaultRuntime {
  def main(args: Array[String]): Unit = {
    unsafeRun(myApplicationLogic)
  }

  lazy val myApplicationLogic: scalaz.zio.ZIO[ReactiveStreamsSubscriberSample.Environment, Nothing, Int] = for {
    q <- scalaz.zio.Ref.make(List.empty[Int])
    runtime <- scalaz.zio.ZIO.runtime[Any]
    sub = SubscriberZio(runtime, q)
    _ <- rangePublisher.map(_.subscribe(sub))
    _ <- sub.queue.get.map(q => println(q.sum))
  } yield 0

  lazy val rangePublisher: scalaz.zio.UIO[rs.Publisher[Int]] = scalaz.zio.UIO {
    new rs.Publisher[Int] {
      override def subscribe(s: rs.Subscriber[_ >: Int]): Unit = {
        Iterator.from(0, 1).take(10).foreach(s.onNext)
        s.onComplete()
      }
    }
  }
}
