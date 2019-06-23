# はじめに

この記事では非関数型のコードとZIOを組み合わせる方法を紹介します。

ZIOを利用してアプリケーションを開発するとき、アプリケーションのロジックでは副作用の記述のみを行い、アプリケーションの入り口（main)で記述に従い副作用を実行します。

```scala
object SimpleMain extends scalaz.zio.DefaultRuntime {
  /**
    * ロジックでは副作用の記述。
    */
  val myApplicationLogic: scalaz.zio.ZIO[scalaz.zio.console.Console, java.io.IOException, Unit] =
    for {
      name <- scalaz.zio.console.getStrLn
      _ <- scalaz.zio.console.putStr(s"hello, $name")
    } yield ()

  /**
    * メインで記述を実行。
    */
  def main(args: Array[String]): Unit = {
    unsafeRun(myApplicationLogic)
  }
}
```

しかし既存のコードへZIOを導入する場合や利用したいフレームワークの制限のため上記の構成にできない場合があります。

- `main`が既に存在していて編集できない。フレームワーク側がmainを提供している。
- 継承やコールバックで利用できる型が固定されているためZIOの型を利用できない。

今回はいくつかのフレームワークで具体的にどのように問題を回避するか紹介します。

# AWS Lambda Java Core Library

AWS Lambda Java Core LibraryはScalaなどのJVM系言語でラムダを実装するときに使用するライブラリです。このライブラリを使用してラムダを実装するにはライブラリが用意するハンドラ`RequestHandler[I, O]`を実装する必要があります。`I`と`O`はそれぞれリクエストの入力と出力の型です。例えば文字列を受け取って大文字にするラムダを実装するには`I=String`、`O=String`で以下のメソッドを実装する必要があります。

```scala
import com.amazonaws.services.lambda.{runtime => aws}

class LambdaJavaCoreSample extends aws.RequestHandler[String, String]{
  override def handleRequest(input: String, context: aws.Context): String = ???
}
```

`RequestHandler`がアプリケーションの入り口になるため`RequestHandler`に`scalaz.zio.DefaultRuntime`をミックスインして実装します。

```scala
import com.amazonaws.services.lambda.{runtime => aws}

class LambdaJavaCoreSample extends aws.RequestHandler[String, String] with scalaz.zio.DefaultRuntime {
  override def handleRequest(input: String, context: aws.Context): String =
    unsafeRun(LambdaJavaCoreSample.myApplicationLogic(input))
}

object LambdaJavaCoreSample {
  def myApplicationLogic(input: String): scalaz.zio.UIO[String] = scalaz.zio.UIO.succeed(input.toUpperCase)
}
```

似たようなケースのPlay Frameworkの場合、アプリケーションの入り口はControllerのです。Controllerに`scalaz.zio.DefaultRuntime`をミックスインし、副作用を実行することでactionを実装できます。

# Reactive Streams

Reactive Streamsは非同期ストリーミング処理のためのインターフェイスを提供するライブラリです。

`Subscriber`インターフェイスを実装するには以下のコールバックを実装しなければいけません。

```scala
import org.{reactivestreams => rs}

class SubscriberZio extends rs.Subscriber[Int] {
  override def onSubscribe(s: rs.Subscription): Unit = ???

  override def onNext(t: Int): Unit = ???

  override def onError(t: Throwable): Unit = ???

  override def onComplete(): Unit = ???
}
```

`AWS Lambda Java`の`RequestHandler`と異なり`Subscriber`はアプリケーションの途中で利用するため`SubscriberZio`に`DefaultRuntime`をミックスインするべきではありません。代わりにコンストラクタで`Runtime`のインスタンスを渡して使用します。

`Runtime`のインスタンスを取得するためには`Zio#runtime`メソッドを使用します。

```scala
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

  lazy val myApplicationLogic: scalaz.zio.ZIO[ReactiveStreamsSubscriberSample.Environment, Nothing, Unit] = for {
    q <- scalaz.zio.Ref.make(List.empty[Int])
    runtime <- scalaz.zio.ZIO.runtime[Any]
    sub = SubscriberZio(runtime, q)
    _ <- rangePublisher.map(_.subscribe(sub))
    _ <- sub.queue.get.map(q => println(q.sum))
  } yield ()

  lazy val rangePublisher: scalaz.zio.UIO[rs.Publisher[Int]] = scalaz.zio.UIO {
    new rs.Publisher[Int] {
      override def subscribe(s: rs.Subscriber[_ >: Int]): Unit = {
        Iterator.from(0, 1).take(10).foreach(s.onNext)
        s.onComplete()
      }
    }
  }
}
```

実際にReactive StreamをZIOで使用する場合には`interop-reactiveStreams`パッケージが用意されているのでそちらを使用しましょう。

# まとめ

今回は`AWS Lambda Java Core Library`と`Reactive Streams`を`ZIO`と組み合わせる方法を紹介しました。

`Runtime`をミックスインする方法、`runtime`メソッドで`Runtime`インスタンスにアクセスする方法を利用すると、既存コードを部分的にZIOを導入することができお勧めです。

# 参考

- [コード@github](https://github.com/mitsutaka-takeda/zio-with-non-functional-framework)
