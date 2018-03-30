
import java.util.Calendar;
import java.util.HashSet;
import java.util.Random;
import javax.net.ssl.SSLContext;
import javax.xml.bind.DatatypeConverter;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.nodes.Document;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Main {

    private static final String USER = "YourUsername",
            PASS = "YourPassword",
            LOGIN = "https://bibliotik.me/",
            FETCH = "https://bibliotik.me/torrents/",
            CATEGORY = "ebooks-category,magazines-category",
            YEARS = "2010,2011,2012,2013,2014,2015,2016,2017,2018",
            UNITS = "MB",
            USERAGENT = "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36",
            RUTUSER = "YourRutorrentUser",
            RUTPASS = "YourRutorrentPassword",
            RUTLINK = "PathToRutorrent";

    private static final int MINSLEEP = 5000,
            MAXSLEEP = 15000,
            MINSIZE = 5,
            MAXSIZE = 150,
            MAXTIMEDIF = 20000;

    private static double time;

    public static void main(String[] args) {
        log("Program started.");

        Connection con = base(LOGIN)
                .data("username", USER, "password", PASS)
                .method(Method.POST);

        try {
            Connection.Response resp = con.execute();
            con = base(FETCH).cookies(resp.cookies());

            HashSet<String> set = new HashSet();

            while (true) {
                Thread.sleep(new Random().nextInt(MAXSLEEP - MINSLEEP) + MINSLEEP);
                Document doc;
                updateTime();
                resp = con.execute();
                //log(measureTime());

                if (resp.statusCode() == 200) {
                    updateTime();
                    doc = resp.parse();
                    //log(measureTime());
                } else {
                    log("Failed to connect. Trying again.");
                    continue;
                }

                updateTime();

                Elements raws = doc.select("#torrents_table tbody tr");

                for (Element raw : raws) {

                    String title = raw.select(".title").text();

                    if (!set.contains(title) && hasClass(raw) && YEARS.contains(raw.select(".torYear").text().replaceAll("\\[|\\]", ""))) {
                        //log("First if.");
                        Element sizeEl = raw.select(".t_files_size_added").first();
                        String unparsedSize = sizeEl.text();
                        int unitIndex = unparsedSize.indexOf(UNITS);

                        if (unitIndex > -1) {
                            //log("Second if.");
                            int commaIndex = unparsedSize.indexOf(',');
                            double parsedSize = Double.parseDouble(unparsedSize.substring(commaIndex + 1, unitIndex));

                            if (parsedSize >= MINSIZE && parsedSize <= MAXSIZE) {
                                //log("Third if.");
                                Calendar calAdded = DatatypeConverter.parseDateTime(sizeEl.select("time").attr("datetime"));
                                Calendar calNow = Calendar.getInstance(calAdded.getTimeZone());
                                log("Found: " + title);
                                long timeDif = calNow.getTimeInMillis() - calAdded.getTimeInMillis();
                                log("Added before (milliseconds): " + timeDif);

                                if (timeDif < MAXTIMEDIF) {
                                    //log("Fourth if.");
                                    Element link = raw.select("a[title=Download]").first();
                                    String dl = link.attr("abs:href");
                                    log("Trying to send to rutorrent; answer received: ");
                                    printHeaders(feedToRutorrent(title, con.url(dl).execute().bodyAsBytes()));
                                    con.url(FETCH);
                                    set.add(title);
                                }
                            }
                        }
                    }
                }
                //log(measureTime());
                System.out.println();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static Connection base(String url) {
        return Jsoup.connect(url)
                .userAgent(USERAGENT)
                .ignoreContentType(true);
    }

    private static HttpResponse feedToRutorrent(String title, byte[] b) throws Exception {
        SSLContext sslContext = new SSLContextBuilder()
                .loadTrustMaterial(null, (certificate, authType) -> true).build();

        ContentType ct = ContentType.create("application/x-bittorrent");
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(RUTUSER, RUTPASS);
        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(AuthScope.ANY, credentials);

        CloseableHttpClient client = HttpClients.custom()
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(new NoopHostnameVerifier())
                .setDefaultCredentialsProvider(provider)
                .build();

        HttpPost httpPost = new HttpPost(RUTLINK + "/php/addtorrent.php");

        HttpEntity entity = MultipartEntityBuilder
                .create()
                .addBinaryBody("torrent_file", b, ct, title + ".torrent")
                .build();

        httpPost.setEntity(entity);
        return client.execute(httpPost);
    }

    private static void printHeaders(HttpResponse response) {
        Header[] headers = response.getAllHeaders();
        for (Header header : headers) {
            System.out.println("Key : " + header.getName()
                    + " ,Value : " + header.getValue());
        }
    }

    private static void log(Object o) {
        System.out.println('[' + Calendar.getInstance().getTime().toString() + "] " + o);
    }

    /*private static void log(double d) {
        System.out.printf('[' + Calendar.getInstance().getTime().toString() + "] %.5f\n", d);
    }*/
    
    private static void updateTime() {
        time = System.currentTimeMillis();
    }

    private static double measureTime() {
        double oldTime = time;
        updateTime();
        return (time - oldTime) / 1000;
    }

    private static boolean hasClass(Element raw) {
        String[] arr = CATEGORY.split(",");
        for (String s : arr) {
            if (raw.hasClass(s)) {
                return true;
            }
        }
        return false;
    }

}
