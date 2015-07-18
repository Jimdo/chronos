package org.apache.mesos.chronos.notification

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestActorRef}
import org.apache.commons.mail.Email
import org.apache.mesos.chronos.scheduler.jobs.ScheduleBasedJob
import org.specs2.mock.Mockito
import org.specs2.mutable.{After, SpecificationWithJUnit}

// http://blog.xebia.com/2012/10/01/testing-akka-with-specs2/
abstract class AkkaTestKitSpecs2Support extends TestKit(ActorSystem()) with After {
  // make sure we shut down the actor system after all tests have run
  def after = system.shutdown()
}

class MailClientSpec extends SpecificationWithJUnit with Mockito {
  val server = "mail.devel:25"
  val fromUser = "unit@test.devel"
  val mailUser = Option("unit@test.devel")
  val password = Option("secret")
  val ssl = false

  "MailClient" should {
    "send an e-mail" in new AkkaTestKitSpecs2Support {
      val jobName = "unittest-job"
      val message = "Test Message"
      val subject = "Unit Test"
      val to = "test@chronos.devel"

      val mailMock = mock[Email]

      val actorRef = TestActorRef(new MailClient(server, fromUser, None, None, ssl, None, () => { mailMock }))
      val mc = actorRef.underlyingActor

      mc.sendNotification(
        new ScheduleBasedJob("R/2015-05-10T15:00:00.000+02:00/PT2M", jobName, "sleep 1;"),
        to,
        subject,
        Option(message))

      there was one(mailMock).setHostName("mail.devel")
      there was one(mailMock).addTo(to)
      there was one(mailMock).setFrom(fromUser)
      there was one(mailMock).setSubject(subject)
      there was one(mailMock).setMsg(message)
      there was one(mailMock).setSSLOnConnect(ssl)
      there was one(mailMock).setSmtpPort(25)
    }

    "reads the message template from a file" in new AkkaTestKitSpecs2Support {
      val jobName = "unittest-job"
      val message = "Template File Message"
      val subject = "Unit Test"
      val to = "test@chronos.devel"

      val templateDirPath = Option(getClass.getResource("/notification.mustache").getFile)

      val mailMock = mock[Email]

      val ref = TestActorRef(
        new MailClient(server, fromUser, None, None, ssl, templateDirPath, () => { mailMock }))
      val mc = ref.underlyingActor

      mc.sendNotification(
        new ScheduleBasedJob("R/2015-05-10T15:00:00.000+02:00/PT2M", jobName, "sleep 1;"),
        to,
        subject,
        Option(message))

      there was one(mailMock).setMsg(s"$jobName $message")
    }

    "falls back to the default template" in new AkkaTestKitSpecs2Support {
      val jobName = "unittest-job"
      val message = "Template File Message"
      val subject = "Unit Test"
      val to = "test@chronos.devel"

      val templateDirPath = Option("/missing/template.mustache")

      val mailMock = mock[Email]

      val ref = TestActorRef(
        new MailClient(server, fromUser, None, None, ssl, templateDirPath, () => { mailMock }))
      val mc = ref.underlyingActor

      mc.sendNotification(
        new ScheduleBasedJob("R/2015-05-10T15:00:00.000+02:00/PT2M", jobName, "sleep 1;"),
        to,
        subject,
        Option(message))

      there was one(mailMock).setMsg(message)
    }
  }
}
