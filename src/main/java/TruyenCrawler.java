import java.io.File;
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

            for (Element link : storyLinks) {
                String storyUrl = link.attr("href");
                crawlAndSaveStory(storyUrl);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void crawlAndSaveStory(String url) {
        try {
            Document doc = Jsoup.connect(url).get();

            String title = doc.select(".truyen-title").text();
            String sanitizedStoryTitle = title.replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("\\s+", "_");

            List<Map<String, Object>> chapterContents = crawlChapters(doc);

            // Tạo thư mục 'data/tên_truyện' nếu chưa tồn tại
            File storyDir = new File("data/" + sanitizedStoryTitle);
            if (!storyDir.exists()) {
                storyDir.mkdirs();
            }

            // Lưu thông tin truyện vào file JSON
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter("data/" + sanitizedStoryTitle + "/truyen.json")) {
                Map<String, Object> storyData = new HashMap<>();
                storyData.put("title", title);
                storyData.put("chapters", chapterContents);
                gson.toJson(storyData, writer);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Map<String, Object>> crawlChapters(Document doc) {
        List<Map<String, Object>> chapterContents = new ArrayList<>();

        Elements chapters = doc.select(".list-chapter a");
        int chapterCount = 1;
        for (Element chapter : chapters) {
            String chapterUrl = chapter.attr("href");
            Map<String, Object> chapterData = crawlChapter(chapterUrl);
            if (chapterData != null && !chapterData.isEmpty()) {
                chapterContents.add(chapterData);

                // Lưu chương dưới dạng file JSON
                saveChapterToFile(chapterData, chapterCount);
                chapterCount++;
            }
        }

        return chapterContents;
    }

    public static Map<String, Object> crawlChapter(String url) {
        Map<String, Object> chapterData = new HashMap<>();
        try {
            Document doc = Jsoup.connect(url).get();

            String chapterTitle = doc.select(".chapter-title").text();
            String chapterContent = doc.select(".chapter-c").text();

            chapterData.put("title", chapterTitle);
            chapterData.put("content", chapterContent);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return chapterData;
    }

    public static void saveChapterToFile(Map<String, Object> chapterData, int chapterNumber) {
        try {
            String storyTitle = (String) chapterData.get("title");
            String sanitizedStoryTitle = storyTitle.replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("\\s+", "_");

            String chapterTitle = (String) chapterData.get("title");
            String sanitizedChapterTitle = chapterTitle.replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("\\s+", "_");

            // Tạo thư mục 'data/tên_truyện' nếu chưa tồn tại
            File storyDir = new File("data/" + sanitizedStoryTitle);
            if (!storyDir.exists()) {
                storyDir.mkdirs();
            }

            // Lưu chương vào file JSON
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter("data/" + sanitizedStoryTitle + "/chapter" + chapterNumber + "_" + sanitizedChapterTitle + ".json")) {
                gson.toJson(chapterData, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
