/*
 Copyright (c) 2019 Gabriel Dimitriu All rights reserved.
 DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

 This file is part of poc_aws project.

 poc_aws is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 poc_aws is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with poc_aws.  If not, see <http://www.gnu.org/licenses/>.
 */
package poc_aws.poc_tests.sqs;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

public class MyMessageListener implements MessageListener {
    @Override
    public void onMessage(Message message) {
        try {
            System.out.println("Received message with correlation " + message.getJMSCorrelationID() + " and message Id " + message.getJMSMessageID());
            System.out.println("Message sequence number: " + message.getStringProperty("JMS_SQS_SequenceNumber"));
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
}
