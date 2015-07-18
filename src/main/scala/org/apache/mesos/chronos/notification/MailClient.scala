package org.apache.mesos.chronos.notification

import java.io.{File, StringReader, StringWriter}
import java.util
import java.util.logging.Logger
import scala.collection.JavaConversions._

import com.github.mustachejava.{Mustache, MustacheException, DefaultMustacheFactory}
import org.apache.mesos.chronos.scheduler.jobs.BaseJob
import org.apache.commons.mail.{Email, DefaultAuthenticator}

/**
 * A very simple mail client that works out of the box with providers such as Amazon SES.
 * TODO(FL): Test with other providers.

 * @author Florian Leibert (flo@leibert.de)
 */
class MailClient(
                  val mailServerString: String,
                  val fromUser: String,
                  val mailUser: Option[String],
                  val password: Option[String],
                  val ssl: Boolean,
                  val templatePath: Option[String],
                  val mailFactory: () => Email)
  extends NotificationClient {

  private[this] val log = Logger.getLogger(getClass.getName)
  private[this] val split = """(.*):([0-9]*)""".r
  private[this] val split(mailHost, mailPortStr) = mailServerString
  val mailPort = mailPortStr.toInt

  private[this] val mf = new DefaultMustacheFactory()
  // Use three mustaches to preserve line breaks
  private[this] val defaultTemplate = "{{{ message }}}"
  private[this] val template = loadTemplate(templatePath)

  def loadTemplate(templatePath: Option[String]): Mustache = {
    if (templatePath.nonEmpty && templatePath.get.nonEmpty) {
      val tpl = templatePath.get
      val f = new File(tpl)

      if (f.isFile && f.canRead) {
        mf.compile(tpl)
      } else {
        log.warning(s"Mail Notification template '$tpl' does not exist or is not readable - falling back to default template")
        mf.compile(new StringReader(defaultTemplate), "default")
      }
    } else {
      mf.compile(new StringReader(defaultTemplate), "default")
    }
  }

  def sendNotification(job: BaseJob, to: String, subject: String, message: Option[String]) {
    val email = mailFactory()
    email.setHostName(mailHost)

    if (mailUser.isDefined && password.nonEmpty) {
      email.setAuthenticator(new DefaultAuthenticator(mailUser.get, password.get))
    }

    email.addTo(to)
    email.setFrom(fromUser)

    email.setSubject(subject)

    if (message.nonEmpty && message.get.nonEmpty) {
      val scopes = Map("job" -> job, "message" -> message.get)
      val sw = new StringWriter()

      val body = template.execute(sw, new util.HashMap[String, Object](scopes))

      email.setMsg(body.toString)
    }

    email.setSSLOnConnect(ssl)
    email.setSmtpPort(mailPort)
    val response = email.send
    log.info("Sent email to '%s' with subject: '%s', got response '%s'".format(to, subject, response))
  }

}
