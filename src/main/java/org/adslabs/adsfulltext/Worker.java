package org.adslabs.adsfulltext;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;

import org.adslabs.adsfulltext.ConfigLoader;

import java.util.HashMap;
import java.util.Map;

import org.adslabs.adsfulltext.Exchanges;
import org.adslabs.adsfulltext.Queues;
import org.adslabs.adsfulltext.Callback;

public class Worker {

    public Connection connection;
    public Channel channel;
    int prefetchCount;
    ConfigLoader config;

    // Class constructor
    public Worker() {
        prefetchCount = 1;
        config = new ConfigLoader();
        config.loadConfig();
    }

    public boolean disconnect() {
        try {

            this.channel.close();
            this.connection.close();
            return true;

        } catch (java.io.IOException error) {

            System.out.println("There is probably no connection with RabbitMQ currently made: " + error.getMessage());
            return false;

        }
    }

    public boolean connect () {

        ConnectionFactory rabbitMQInstance;

        // This creates a connection object, that allows connections to be open to the same
        // RabbitMQ instance running
        //
        // There seems to be a lot of catches, but each one should list the reason for it's raise
        //
        // It is not necessary to disconnect the connection, it should be dropped when the program exits
        // See the API guide for more info: https://www.rabbitmq.com/api-guide.html

        try {

            rabbitMQInstance = new ConnectionFactory();
            System.out.println();
            rabbitMQInstance.setUri(this.config.data.RABBITMQ_URI);
            this.connection = rabbitMQInstance.newConnection();
            this.channel = this.connection.createChannel();

            // This tells RabbitMQ not to give more than one message to a worker at a time.
            // Or, in other words, don't dispatch a new message to a worker until it has processed
            // and acknowledged the previous one.
            this.channel.basicQos(this.prefetchCount);

            return true;

        } catch (java.net.URISyntaxException error) {

            System.out.println("URI error: " + error.getMessage());
            return false;

        } catch (java.io.IOException error) {

            System.out.println("IO Error, is RabbitMQ running???: " + error.getMessage());
            return false;

        } catch (java.security.NoSuchAlgorithmException error) {

            System.out.println("Most likely an SSL related error: " + error.getMessage());
            return false;

        } catch (java.security.KeyManagementException error) {

            System.out.println("Most likely an SSL related error: " + error.getMessage());
            return false;

        }
    }

    public String process(String message) {
        String newMessage = message + " Processed";
        return newMessage;
    }

    public void subscribe() {

        // for array in subscribe array:
        //   start basic_consume
        //   if this is not a testing phase, then stay consuming

        String queueName = "PDFFileExtractorQueue";
        boolean autoAck = false; // This means it WILL acknowledge
        boolean testRun = true;
        String exchangeName = "FulltextExtractionExchange";
        String routingKey = "WriteMetaFileRoute";

        while (true) {
            try {
//                System.out.println("Start");
                QueueingConsumer consumer = new QueueingConsumer(this.channel);
                this.channel.basicConsume(queueName, autoAck, consumer);

                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                String message = new String(delivery.getBody());
                long deliveryTag = delivery.getEnvelope().getDeliveryTag();

                // Process the message
                String newMessage = this.process(message);

                // Publish to the next queue
                this.channel.basicPublish(exchangeName, routingKey, null, newMessage.getBytes());

                // Acknowledge the receipt of the message
                this.channel.basicAck(deliveryTag, false);

//                System.out.println("End");

                if (testRun){
                    break;
                }

            } catch (java.io.IOException error) {
                System.out.println("IO Error, does the queue exist and is RabbitMQ running?: " + error.getMessage());
            } catch(java.lang.InterruptedException error) {
                System.out.println("IO Error, does the queue exist and is RabbitMQ running?: " + error.getMessage());
            }
        }
    }

    public boolean declare_all() {

        Exchanges[] exchange = config.data.EXCHANGES;

        for (int i = 0; i < exchange.length; i++) {
            try {
                // OUTDATED
                // Parameters are exchange name, type, passive, durable, autoDelete, arguments
                // OUTDATED
                // On github of the client api: exchange name, type, durable, autoDelete, internal
                // internal: internal true if the exchange is internal, i.e. can't be directly published to by a client.

                this.channel.exchangeDeclare(exchange[i].exchange, exchange[i].exchange_type, exchange[i].durable, exchange[i].autoDelete, null);

            } catch (java.io.IOException error) {

                System.out.println("IO Error, is RabbitMQ running, check the passive/active settings!: " + error.getMessage() + error.getStackTrace());
                return false;
            }
        }
        return true;
    }

    public boolean purge_all() {

        Queues[] queues = config.data.QUEUES;
        for (int i = 0; i < queues.length; i++) {
            try {
//                System.out.println("Purging queue: " + queues[i].queue);
                this.channel.queuePurge(queues[i].queue);

            } catch (java.io.IOException error) {

                System.out.println("IO Error, is RabbitMQ running, check the passive/active settings!: " + error.getMessage() + error.getStackTrace());
                return false;
            }
        }
        return true;
    }

    public void run() {
        this.connect();
        this.subscribe();
    }

}
