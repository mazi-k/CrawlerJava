package crawler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import java.util.ArrayList;

public class Main {
    public static final Logger log = LogManager.getLogger();
    public static Downloader downloader;
    public static String site = "https://www.womanhit.ru";

    public static void main(String[] args) {
        downloader = new Downloader(site);
        Document doc = downloader.getDocumentByUrl(site);
        Parser parser = new Parser();
        if (doc != null) {
            parser.parseNews(doc, site);
        }
        ArrayList<Article> articles = parser.getArticles();
        logging(articles);
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
