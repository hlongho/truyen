import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TruyenCrawler {
    public static void main(String[] args) {
        try {
            String url = "https://truyenfull.vn/danh-sach/truyen-hot/";
            Document doc = Jsoup.connect(url).get();

            Elements storyLinks = doc.select(".truyen-title a");
            List<Map<String, String>> stories = new ArrayList<>();

            for (Element link : storyLinks) {
                String storyUrl = link.attr("href");
                Map<String, String> storyData = crawlStory(storyUrl);
                stories.add(storyData);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter("truyen.json")) {
                gson.toJson(stories, writer);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, String> crawlStory(String url) {
        Map<String, String> storyData = new HashMap<>();
        try {
            Document doc = Jsoup.connect(url).get();

            String title = doc.select(".truyen-title").text();
            String author = doc.select(".info a[itemprop=author]").text();
            Elements chapters = doc.select(".list-chapter a");

            storyData.put("title", title);
            storyData.put("author", author);
            List<String> chapterUrls = new ArrayList<>();
            for (Element chapter : chapters) {
                String chapterUrl = chapter.attr("href");
                chapterUrls.add(chapterUrl);
            }
            storyData.put("chapters", String.join(", ", chapterUrls));

        } catch (IOException e) {
            e.printStackTrace();
        }
        return storyData;
    }
}
