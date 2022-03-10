package crawler;

import org.apache.http.HttpEntity;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

public class Downloader implements Runnable{
    private static final Logger log = LogManager.getLogger();
    private CloseableHttpClient client;
    private final HttpClientBuilder clientBuilder;
    private String server = "";
    private final int RETRY_DELAY = 5 * 1000;
    private final int RETRY_COUNT = 2;
    private final int METADATA_TIMEOUT = 30*1000;

    public Downloader(String _server) {
        CookieStore httpCookieStore = new BasicCookieStore();
        clientBuilder = HttpClientBuilder.create().setDefaultCookieStore(httpCookieStore);
        client = clientBuilder.build();
        this.server = _server;
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
                            doc = Jsoup.parse(entity.getContent(), "UTF-8", server);
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

    public void run() {

    }
}
