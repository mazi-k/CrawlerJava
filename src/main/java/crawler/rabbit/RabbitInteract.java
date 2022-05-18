package crawler.rabbit;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import crawler.core.Parser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

public class RabbitInteract extends Thread {

    String exchangeName = "exchangeName";
    String queueNameDownload = "queueNameDownload";
    String routingKeyToDownload = "routingKeyToDownload";
    int threadCount = 3;
    private static Logger log = LogManager.getLogger();

    void messageProducer() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername("rabbitmq");
        factory.setPassword("rabbitmq");
        factory.setVirtualHost("/");
        factory.setHost("127.0.0.1");
        factory.setPort(5672);
        Connection conn = factory.newConnection();
        Channel channel = conn.createChannel();
        boolean durable = true;
        channel.exchangeDeclare(exchangeName, "direct", durable);
        channel.queueDeclare(queueNameDownload, durable, false, false, null);
        channel.queueBind(queueNameDownload, exchangeName, routingKeyToDownload);
        channel.queuePurge(queueNameDownload);


        Config config;
        PreBuiltTransportClient elasticSearchClient = null;
        Config conf = ConfigFactory.load();
        config = conf.getConfig("es");
        try {
            elasticSearchClient = createClient(config);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }


        CreateIndexRequest indexRequest = new CreateIndexRequest("site_logs");
        indexRequest.settings(Settings.builder()
                .put("index.number_of_shards", 1)
                .put("index.number_of_replicas", 1)
                .build()
        );

        try {
            elasticSearchClient.admin().indices().create(indexRequest).actionGet();
        } catch (ResourceAlreadyExistsException e){}

        QueueingConsumer consumer = new QueueingConsumer(channel);
        channel.basicConsume(queueNameDownload, false, consumer);
        boolean isRunning = true;

        Thread[] threadsPool = new Thread[threadCount];
        int i = 0;

        while (isRunning) {
            QueueingConsumer.Delivery delivery;
            try {
                delivery = consumer.nextDelivery();

                if (i < threadCount || threadsPool.length < threadCount) {
                    threadsPool[i] = new Thread(
                            new Parser(
                                    channel, elasticSearchClient,
                                    exchangeName,
                                    routingKeyToDownload,
                                    new String(delivery.getBody()),
                                    delivery.getEnvelope().getDeliveryTag()
                            )
                    );
                    threadsPool[i].start();
                    i += 1;
                } else {
                    int j = 0;
                    for (Thread thread : threadsPool) {
                        if (!thread.isAlive()) {
                            break;
                        } else {
                            j += 1;
                        }
                    }
                    if (j >= threadCount) {
                        Thread.sleep(100 + (Thread.currentThread().getId() % 5) * 50);
                    } else {
                        threadsPool[j] = new Thread(new Parser(channel, elasticSearchClient, exchangeName, routingKeyToDownload,
                                 new String(delivery.getBody()), delivery.getEnvelope().getDeliveryTag()));
                        threadsPool[j].start();
                    }
                }
            } catch (InterruptedException ie) {
                continue;
            }
        }

        channel.close();
        conn.close();
    }

    private static PreBuiltTransportClient createClient(Config config) throws UnknownHostException {
        Settings settings = Settings.builder()
                .put("cluster.name", config.getString("cluster"))
                .build();

        PreBuiltTransportClient cli = new PreBuiltTransportClient(settings);
        cli.addTransportAddress(
                new TransportAddress(InetAddress.getByName(config.getString("host")), 9300)
        );

        return cli;
    }

    @Override
    public void run() {
        try {
            messageProducer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

}
