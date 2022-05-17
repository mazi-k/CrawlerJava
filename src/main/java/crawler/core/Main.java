package crawler.core;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import crawler.rabbit.RabbitInteract;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

public class Main {
    public static final Logger log = LogManager.getLogger();
    public static String site = "https://www.womanhit.ru";
    public static int threadCount = 3;
    static String exchangeName = "exchangeName";
    static String routingKeyToDownload = "routingKeyToDownload";

    public static void main(String[] args) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername("rabbitmq");
        factory.setPassword("rabbitmq");
        factory.setVirtualHost("/");
        factory.setHost("127.0.0.1");
        factory.setPort(5672);
        Connection conn = factory.newConnection();
        Channel channel = conn.createChannel();

        Thread downloaderThread = new Thread(new MainPageProcessor(channel, site));
        downloaderThread.start();
        Thread rabbitInteractThread = new Thread(
                new RabbitInteract());
        rabbitInteractThread.start();

        try {
            rabbitInteractThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            downloaderThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

//        threadMaker(channel, exchangeName);
//        downloader = new Downloader(site);
//        Document doc = downloader.getDocumentByUrl(site);
//        Parser parser = new Parser();
//        if (doc != null) {
//            parser.parseNews(doc, site);
//        }
//        ArrayList<Article> articles = parser.getArticles();
//        logging(articles);
    }

    public static void threadMaker(Channel channel, String exchangeName) throws IOException {
        Thread downloaderThread = new Thread(
                new RabbitInteract());
        downloaderThread.start();
        byte[] messageBodyBytes = site.getBytes();
        channel.basicPublish(
                exchangeName,
                routingKeyToDownload,
                MessageProperties.PERSISTENT_TEXT_PLAIN, messageBodyBytes);
    }

    public static void logging(ArrayList<Article> articles){
        for (Article article: articles){
            String strForLog = String.format(
                    "%s, %s \n%s: %s \n%s",
                    article.getAuthor(),
                    article.getDateTime(),
                    article.getTitle(),
                    article.getUrl(),
                    article.getContent());

            log.info(strForLog);
        }
    }
}
