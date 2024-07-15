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
            List<Map<String, Object>> stories = new ArrayList<>();

            for (Element link : storyLinks) {
                String storyUrl = link.attr("href");
                Map<String, Object> storyData = crawlStory(storyUrl);
                stories.add(storyData);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter("data/truyen.json")) {
                gson.toJson(stories, writer);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Object> crawlStory(String url) {
        Map<String, Object> storyData = new HashMap<>();
        try {
            Document doc = Jsoup.connect(url).get();

            String title = doc.select(".truyen-title").text();
            String author = doc.select(".info a[itemprop=author]").text();
            Elements chapters = doc.select(".list-chapter a");

            storyData.put("title", title);
            storyData.put("author", author);

            List<Map<String, String>> chapterContents = new ArrayList<>();
            int chapterCount = 1;
            for (Element chapter : chapters) {
                String chapterUrl = chapter.attr("href");
                Map<String, String> chapterData = crawlChapter(chapterUrl);
                if (chapterData != null && !chapterData.isEmpty()) {
                    String chapterTitle = chapterData.get("title");
                    String chapterContent = chapterData.get("content");
                    // Lưu nội dung chương vào file
                    saveChapterToFile(title, chapterCount, chapterTitle, chapterContent);
                    chapterCount++;
                }
                chapterContents.add(chapterData);
            }

            storyData.put("chapters", chapterContents);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return storyData;
    }

    public static Map<String, String> crawlChapter(String url) {
        Map<String, String> chapterData = new HashMap<>();
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

    public static void saveChapterToFile(String storyTitle, int chapterNumber, String chapterTitle, String chapterContent) {
        try {
            // Tạo thư mục 'data/tên_truyện' nếu chưa tồn tại
            String sanitizedStoryTitle = storyTitle.replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("\\s+", "_");
            File storyDir = new File("data/" + sanitizedStoryTitle);
            if (!storyDir.exists()) {
                storyDir.mkdirs();
            }

            // Xử lý các ký tự không hợp lệ trong tên tệp
            String sanitizedChapterTitle = chapterTitle.replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("\\s+", "_");
            String fileName = String.format("data/%s/chapter%d_%s.txt", sanitizedStoryTitle, chapterNumber, sanitizedChapterTitle);

            try (FileWriter writer = new FileWriter(fileName)) {
                writer.write(chapterContent);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
