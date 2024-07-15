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

            // test
            int index = 0;
            for (Element link : storyLinks) {
                if (index <= 3) {
                    String storyUrl = link.attr("href");
                    Map<String, Object> storyData = crawlAndSaveStory(storyUrl);
                    stories.add(storyData);
                    index++;
                }
            }

            // Lưu danh sách truyện vào file JSON
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter("data/truyen.json")) {
                gson.toJson(stories, writer);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Object> crawlAndSaveStory(String url) {
        Map<String, Object> storyData = new HashMap<>();
        try {
            Document doc = Jsoup.connect(url).get();

            String title = doc.select(".truyen-title").text();
            String sanitizedStoryTitle = title.replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("\\s+", "_");

            String status = doc.select(".label-success").text(); // Đây là trạng thái của truyện
            String author = doc.select(".info a[itemprop=author]").text(); // Đây là tên tác giả của truyện

            List<Map<String, String>> chapterContents = crawlAndSaveChapters(doc, sanitizedStoryTitle);

            // Tạo thư mục 'data/tên_truyện' nếu chưa tồn tại
            File storyDir = new File("data/" + sanitizedStoryTitle);
            if (!storyDir.exists()) {
                storyDir.mkdirs();
            }

            // Lưu thông tin truyện vào map storyData
            storyData.put("name", title);
            storyData.put("status", status);
            storyData.put("author", author);
            storyData.put("chapter", chapterContents);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return storyData;
    }

    public static List<Map<String, String>> crawlAndSaveChapters(Document doc, String storyFolderName) {
        List<Map<String, String>> chapterContents = new ArrayList<>();

        Elements chapters = doc.select(".list-chapter a");
        // test
        int index = 0;
        for (Element chapter : chapters) {
            if (index <= 10) {
                String chapterUrl = chapter.attr("href");
                Map<String, Object> chapterData = crawlChapter(chapterUrl);
                if (chapterData != null && !chapterData.isEmpty()) {
                    String chapterTitle = (String) chapterData.get("chapter_name");
                    String sanitizedChapterTitle = chapterTitle.replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("\\s+", "_");

                    // Tạo thư mục cho truyện nếu chưa tồn tại
                    File chapterDir = new File("data/" + storyFolderName);
                    if (!chapterDir.exists()) {
                        chapterDir.mkdirs();
                    }

                    // Lưu chương vào file JSON
                    String chapterFilePath = "data/" + storyFolderName + "/" + sanitizedChapterTitle + ".json";
                    saveChapterToJsonFile(chapterData, chapterFilePath);

                    // Thêm thông tin chương vào danh sách
                    Map<String, String> chapterInfo = new HashMap<>();
                    chapterInfo.put("chapter_name", chapterTitle);
                    chapterInfo.put("path", chapterFilePath);
                    chapterContents.add(chapterInfo);
                }
                index++;
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

            // Lưu thông tin chương vào map chapterData
            chapterData.put("chapter_name", chapterTitle);
            chapterData.put("content", chapterContent);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return chapterData;
    }

    public static void saveChapterToJsonFile(Map<String, Object> chapterData, String chapterFilePath) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(chapterFilePath)) {
            gson.toJson(chapterData, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
