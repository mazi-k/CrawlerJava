package crawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import org.apache.logging.log4j.Logger;

public class Parser implements Runnable{
    private final Downloader downloader = Main.downloader;
    private final Logger log = Main.log;
    private final ArrayList<Article> articles = new ArrayList<Article>();

    public Parser() { }

    public ArrayList<Article> getArticles() {
        return articles;
    }

    public void parseNews(Document doc, String site) {
        Elements news = doc.getElementsByClass("card__inner");
        for (Element element: news) {
            try {
                String title = element
                        .select("h3")
                        .text();
                String url = element
                        .getElementsByTag("a")
                        .attr("href");

                url = site + url;
                ArrayList<String> data = parsePage(url);

                Article article = new Article(title, url, data.get(1), data.get(2), data.get(0));
                articles.add(article);

            } catch (Exception e) {
                log.error(e);
            }
        }
    }

    private ArrayList<String> parsePage(String url) {
        Document page = downloader.getDocumentByUrl(url);

        //[0] - text, [1] - author, [2] - date-time
        ArrayList<String> data = new ArrayList<String>();
        StringBuilder text = new StringBuilder();

        String dateTime = page
                .getElementsByClass("article__announce-date")
                .first()
                .text();

        String author = page
                .select("div.article__announce-authors")
                .select("a.author")
                .first()
                .child(0)
                .text();


        Element content = page
                .getElementsByClass("content")
                .select("main")
                .select("section")
                .first()
                .child(1)
                .child(8);

        for (int i = 0; i < content.childNodeSize(); i++){
            text.append(content.child(i).text());
        }

        data.add(text.toString());
        data.add(author);
        data.add(dateTime);

        return data;
    }

    public void run() {

    }
}
