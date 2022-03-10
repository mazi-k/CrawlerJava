package crawler;

public class Article {
    private String title;
    private String url;
    private String author;
    private String dateTime;
    private String content;


    public Article(String title, String url, String author, String dateTime, String content) {
        this.title = title;
        this.url = url;
        this.author = author;
        this.dateTime = dateTime;
        this.content = content;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getAuthor() {
        return author;
    }

    public String getDateTime() {
        return dateTime;
    }

    public String getContent() {
        return content;
    }
}
