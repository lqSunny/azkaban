/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.datapps.zebra.workflow.utils;

import com.datapps.zebra.workflow.Constants;
import com.datapps.zebra.workflow.alert.Alerter;
import com.datapps.zebra.workflow.executor.ExecutableFlow;
import com.datapps.zebra.workflow.executor.ExecutableNode;
import com.datapps.zebra.workflow.executor.ExecutionOptions;
import com.datapps.zebra.workflow.executor.Status;
import com.datapps.zebra.workflow.executor.mail.DefaultMailCreator;
import com.datapps.zebra.workflow.executor.mail.MailCreator;
import com.datapps.zebra.workflow.metrics.CommonMetrics;
import com.datapps.zebra.workflow.sla.SlaOption;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

@Singleton
public class Emailer extends AbstractMailer implements Alerter {

  private static final String HTTPS = "https";
  private static final String HTTP = "http";
  private static final Logger logger = Logger.getLogger(Emailer.class);
  private final CommonMetrics commonMetrics;
  private final String scheme;
  private final String clientHostname;
  private final String clientPortNumber;
  private final String mailHost;
  private final int mailPort;
  private final String mailUser;
  private final String mailPassword;
  private final String mailSender;
  private final String azkabanName;
  private final String tls;
  private boolean testMode = false;

  @Inject
  public Emailer(final Props props, final CommonMetrics commonMetrics) {
    super(props);
    this.commonMetrics = requireNonNull(commonMetrics, "commonMetrics is null.");
    this.azkabanName = props.getString("azkaban.name", "azkaban");
    this.mailHost = props.getString("mail.host", "localhost");
    this.mailPort = props.getInt("mail.port", DEFAULT_SMTP_PORT);
    this.mailUser = props.getString("mail.user", "");
    this.mailPassword = props.getString("mail.password", "");
    this.mailSender = props.getString("mail.sender", "");
    this.tls = props.getString("mail.tls", "false");

    final int mailTimeout = props.getInt("mail.timeout.millis", 30000);
    EmailMessage.setTimeout(mailTimeout);
    final int connectionTimeout =
        props.getInt("mail.connection.timeout.millis", 30000);
    EmailMessage.setConnectionTimeout(connectionTimeout);

    EmailMessage.setTotalAttachmentMaxSize(getAttachmentMaxSize());

    this.clientHostname = props.getString("jetty.hostname", "localhost");

    if (props.getBoolean("jetty.use.ssl", true)) {
      this.scheme = HTTPS;
      this.clientPortNumber = Integer.toString(props.getInt("jetty.ssl.port",
          Constants.DEFAULT_SSL_PORT_NUMBER));
    } else {
      this.scheme = HTTP;
      this.clientPortNumber = Integer.toString(props.getInt("jetty.port",
          Constants.DEFAULT_PORT_NUMBER));
    }

    this.testMode = props.getBoolean("test.mode", false);
  }

  public static List<String> findFailedJobs(final ExecutableFlow flow) {
    final ArrayList<String> failedJobs = new ArrayList<>();
    for (final ExecutableNode node : flow.getExecutableNodes()) {
      if (node.getStatus() == Status.FAILED) {
        failedJobs.add(node.getId());
      }
    }
    return failedJobs;
  }

  private void sendSlaAlertEmail(final SlaOption slaOption, final String slaMessage) {
    final String subject = "Sla Violation Alert on " + getAzkabanName();
    final String body = slaMessage;
    final List<String> emailList =
        (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
    if (emailList != null && !emailList.isEmpty()) {
      final EmailMessage message =
          super.createEmailMessage(subject, "text/html", emailList);

      message.setBody(body);

      if (!this.testMode) {
        try {
          message.sendEmail();
          this.commonMetrics.markSendEmailSuccess();
        } catch (final MessagingException e) {
          logger.error("Failed to send SLA email message" + slaMessage, e);
          this.commonMetrics.markSendEmailFail();
        }
      }
    }
  }

  public void sendFirstErrorMessage(final ExecutableFlow flow) {
    final EmailMessage message = new EmailMessage(this.mailHost, this.mailPort, this.mailUser,
        this.mailPassword);
    message.setFromAddress(this.mailSender);
    message.setTLS(this.tls);
    message.setAuth(super.hasMailAuth());

    final ExecutionOptions option = flow.getExecutionOptions();

    final MailCreator mailCreator =
        DefaultMailCreator.getCreator(option.getMailCreator());

    logger.debug("ExecutorMailer using mail creator:"
        + mailCreator.getClass().getCanonicalName());

    final boolean mailCreated =
        mailCreator.createFirstErrorMessage(flow, message, this.azkabanName, this.scheme,
            this.clientHostname, this.clientPortNumber);

    if (mailCreated && !this.testMode) {
      try {
        message.sendEmail();
        this.commonMetrics.markSendEmailSuccess();
      } catch (final MessagingException e) {
        logger.error(
            "Failed to send first error email message for execution " + flow.getExecutionId(), e);
        this.commonMetrics.markSendEmailFail();
      }
    }
  }

  public void sendErrorEmail(final ExecutableFlow flow, final String... extraReasons) {
    final EmailMessage message = new EmailMessage(this.mailHost, this.mailPort, this.mailUser,
        this.mailPassword);
    message.setFromAddress(this.mailSender);
    message.setTLS(this.tls);
    message.setAuth(super.hasMailAuth());

    final ExecutionOptions option = flow.getExecutionOptions();

    final MailCreator mailCreator =
        DefaultMailCreator.getCreator(option.getMailCreator());
    logger.debug("ExecutorMailer using mail creator:"
        + mailCreator.getClass().getCanonicalName());

    final boolean mailCreated =
        mailCreator.createErrorEmail(flow, message, this.azkabanName, this.scheme,
            this.clientHostname, this.clientPortNumber, extraReasons);

    if (mailCreated && !this.testMode) {
      try {
        message.sendEmail();
        this.commonMetrics.markSendEmailSuccess();
      } catch (final MessagingException e) {
        logger
            .error("Failed to send error email message for execution " + flow.getExecutionId(), e);
        this.commonMetrics.markSendEmailFail();
      }
    }
  }

  public void sendSuccessEmail(final ExecutableFlow flow) {
    final EmailMessage message = new EmailMessage(this.mailHost, this.mailPort, this.mailUser,
        this.mailPassword);
    message.setFromAddress(this.mailSender);
    message.setTLS(this.tls);
    message.setAuth(super.hasMailAuth());

    final ExecutionOptions option = flow.getExecutionOptions();

    final MailCreator mailCreator =
        DefaultMailCreator.getCreator(option.getMailCreator());
    logger.debug("ExecutorMailer using mail creator:"
        + mailCreator.getClass().getCanonicalName());

    final boolean mailCreated =
        mailCreator.createSuccessEmail(flow, message, this.azkabanName, this.scheme,
            this.clientHostname, this.clientPortNumber);

    if (mailCreated && !this.testMode) {
      try {
        message.sendEmail();
        this.commonMetrics.markSendEmailSuccess();
      } catch (final MessagingException e) {
        logger.error("Failed to send success email message for execution " + flow.getExecutionId(),
            e);
        this.commonMetrics.markSendEmailFail();
      }
    }
  }

  @Override
  public void alertOnSuccess(final ExecutableFlow exflow) throws Exception {
    sendSuccessEmail(exflow);
  }

  @Override
  public void alertOnError(final ExecutableFlow exflow, final String... extraReasons)
      throws Exception {
    sendErrorEmail(exflow, extraReasons);
  }

  @Override
  public void alertOnFirstError(final ExecutableFlow exflow) throws Exception {
    sendFirstErrorMessage(exflow);
  }

  @Override
  public void alertOnSla(final SlaOption slaOption, final String slaMessage)
      throws Exception {
    sendSlaAlertEmail(slaOption, slaMessage);
  }
}
