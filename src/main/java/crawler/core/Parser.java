package crawler.core;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import org.apache.http.HttpEntity;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.*;

import org.apache.logging.log4j.Logger;

public class Parser implements Runnable{
    private final Logger log = Main.log;
    private CloseableHttpClient client;
    private final HttpClientBuilder clientBuilder;
    private final int RETRY_DELAY = 5 * 1000;
    private final int RETRY_COUNT = 2;
    private final int METADATA_TIMEOUT = 30*1000;
    private final ArrayList<Article> articles = new ArrayList<Article>();

    private Channel channel;
    private String exchangeName;
    private String routingKeyToDownload;

    private PreBuiltTransportClient elasticSearchClient;
    private String message;
    private long tag;

    public Parser(Channel channel, PreBuiltTransportClient elasticSearchClient, String exchangeName, String routingKeyToDownload,
                  String message, long tag) {
        this.channel = channel;
        this.elasticSearchClient = elasticSearchClient;
        this.exchangeName = exchangeName;
        this.routingKeyToDownload = routingKeyToDownload;
        this.message = message;
        this.tag = tag;
        CookieStore httpCookieStore = new BasicCookieStore();
        clientBuilder = HttpClientBuilder.create().setDefaultCookieStore(httpCookieStore);
        client = clientBuilder.build();
    }

    public Document getDocumentByUrl(String url)
    {
        int code;
        boolean bStop = false;
        Document doc = null;
        for (int iTry = 0; iTry < RETRY_COUNT && !bStop; iTry++) {
            RequestConfig requestConfig = RequestConfig.custom()
                    .setSocketTimeout(METADATA_TIMEOUT)
                    .setConnectTimeout(METADATA_TIMEOUT)
                    .setConnectionRequestTimeout(METADATA_TIMEOUT)
                    .setExpectContinueEnabled(true)
                    .build();
            HttpGet request = new HttpGet(url);
            request.setConfig(requestConfig);
            CloseableHttpResponse response = null;
            try {
                response = client.execute(request);
                code = response.getStatusLine().getStatusCode();
                if (code == 404) {
                    log.warn("error get url " + url + " code " + code);
                    bStop = true;//break;
                }
                else if (code == 200) {
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {

                        try {
                            doc = Jsoup.parse(entity.getContent(), "UTF-8", url);
                            break;
                        } catch (IOException e) {
                            log.error(e);
                        }
                    }
                    bStop = true;
                } else {
                    log.warn("error get url " + url + " code " + code);
                    response.close();
                    response = null;
                    client.close();
                    CookieStore httpCookieStore = new BasicCookieStore();
                    clientBuilder.setDefaultCookieStore(httpCookieStore);
                    client = clientBuilder.build();
                    int delay = RETRY_DELAY * 1000 * (iTry + 1);
                    log.info("wait " + delay / 1000 + " s...");
                    try {
                        Thread.sleep(delay);
                        continue;
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            } catch (IOException e) {
                log.error(e);
            }
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }
        }
        return doc;
    }

    private void parsePage(String url) {
        Document page = getDocumentByUrl(url);

        String title = page.title();

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

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < content.childNodeSize(); i++){
            text.append(content.child(i).text());
        }

        log.debug(title);
        log.debug(text.toString());
        log.debug(author);

        pushSomeData(new Article(title, url, author, dateTime, text.toString()));
    }

    void pushSomeData(Article article){
//        String json = "{" +
//                "\"user\":\"kimchy\"," +
//                "\"postDate\":\"2013-01-30\"," +
//                "\"message\":\"trying out Elasticsearch\"" +
//                "}";
        Map<String, Object> articleMap = new HashMap<String, Object>();
        articleMap.put("url", article.getUrl());
        articleMap.put("title", article.getTitle());
        articleMap.put("dateTime", article.getDateTime());
        articleMap.put("author", article.getAuthor());
        articleMap.put("content", article.getContent());
        String temp_json = JSON.toJSONString(articleMap);
        IndexResponse response = elasticSearchClient.prepareIndex("site_logs", "page")
                .setSource(temp_json, XContentType.JSON)
                .get();
    }

    void getSomeDataAll() {
        QueryBuilder query = QueryBuilders.matchAllQuery();
        SearchResponse response = elasticSearchClient.prepareSearch("site_logs").setQuery(query).get();

        Iterator<SearchHit> sHits = response.getHits().iterator();
        List<String> results = new ArrayList<String>(20); //some hack! initial size of array!
        while (sHits.hasNext()) {
            results.add(sHits.next().getSourceAsString());
            //jackson

        }
        for (String it : results){
            System.out.println(it);
        }
        log.info(response.getHits().getTotalHits());
    }

    void getSomeData() {
        QueryBuilder query = QueryBuilders.matchQuery("date", "21.03.2022");

        SearchResponse response = elasticSearchClient.prepareSearch("site_logs").setQuery(query).get();
        System.out.println(response.getHits().getTotalHits());
    }

    void getSomeDataList(String field_name, String value) {
        QueryBuilder query = QueryBuilders.matchQuery(field_name, value);
        SearchResponse response = elasticSearchClient.prepareSearch("site_logs").setQuery(query).get();

        Iterator<SearchHit> sHits = response.getHits().iterator();
        List<String> results = new ArrayList<String>(20); //some hack! initial size of array!
        while (sHits.hasNext()) {
            results.add(sHits.next().getSourceAsString());
            //jackson

        }
        for (String it : results){
            System.out.println(it);
        }

        System.out.println(response.getHits().getTotalHits());
    }


    @Override
    public void run() {
        parsePage(message);
    }
}
