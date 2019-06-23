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
