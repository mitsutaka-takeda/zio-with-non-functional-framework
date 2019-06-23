import com.amazonaws.services.lambda.{runtime => aws}

class LambdaJavaCoreSample extends aws.RequestHandler[String, String] with scalaz.zio.DefaultRuntime {
  override def handleRequest(input: String, context: aws.Context): String =
    unsafeRun(LambdaJavaCoreSample.myApplicationLogic(input))
}

object LambdaJavaCoreSample {
  def myApplicationLogic(input: String): scalaz.zio.UIO[String] = scalaz.zio.UIO.succeed(input.toUpperCase)
}
