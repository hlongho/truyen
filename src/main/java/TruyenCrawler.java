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

            List<Map<String, String>> stories = new ArrayList<>();

            // Giới hạn lấy 3 truyện có ít nhất 10 chương mới
            int storyCount = 0;
            for (Element link : storyLinks) {
                String storyUrl = link.attr("href");
                Map<String, String> storyData = crawlAndSaveStory(storyUrl);
                if (storyData != null) {
                    stories.add(storyData);
                    if (storyData.containsKey("new_chapter_count") && Integer.parseInt(storyData.get("new_chapter_count")) >= 10) {
                        storyCount++;
                    }
                }
                if (storyCount >= 3) {
                    break;
                }
            }

            // Lưu danh sách truyện vào file JSON
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter("data/ds_truyen.json")) {
                gson.toJson(stories, writer);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, String> crawlAndSaveStory(String url) {
        Map<String, String> storyData = new HashMap<>();
        try {
            Document doc = Jsoup.connect(url).get();

            String title = doc.select("h3.title").text();
            String sanitizedStoryTitle = title.replaceAll("[^\\p{L}\\p{N}\\s]", "").replaceAll("\\s+", "_");

            String status = doc.select(".info .text-success").text(); // Đây là trạng thái của truyện
            String author = doc.select(".info a[itemprop=author]").text(); // Đây là tên tác giả của truyện
            String desc = doc.select(".desc-text").text(); // Đây là phần tóm tắt mở đầu của truyện
            String image = doc.select(".book img").attr("src"); // Đây là URL hình ảnh của truyện

            // Lấy thể loại truyện
            Elements genreElements = doc.select(".info a[href*=the-loai]");
            List<String> genres = new ArrayList<>();
            for (Element genreElement : genreElements) {
                genres.add(genreElement.text());
            }
            String genresString = String.join(", ", genres);

            // Tạo thư mục 'data/tên_truyện' nếu chưa tồn tại
            File storyDir = new File("data/" + sanitizedStoryTitle);
            if (!storyDir.exists()) {
                storyDir.mkdirs();
            }

            // Lưu thông tin truyện vào map storyData
            storyData.put("name", title);
            storyData.put("status", status);
            storyData.put("author", author);
            storyData.put("desc", desc);
            storyData.put("image", image);
            storyData.put("genres", genresString);

            boolean skipCrawl = false;
            if ("Full".equalsIgnoreCase(status)) {
                // Lấy chương cuối cùng của truyện
                Element lastChapterElement = doc.select(".list-chapter li:last-child a").first();
                if (lastChapterElement != null) {
                    String lastChapterUrl = lastChapterElement.attr("href");
                    Map<String, Object> lastChapterData = crawlChapter(lastChapterUrl);
                    if (lastChapterData != null) {
                        String lastChapterTitle = (String) lastChapterData.get("chapter_name");
                        String sanitizedLastChapterTitle = lastChapterTitle.replaceAll("[^\\p{L}\\p{N}\\s]", "").replaceAll("\\s+", "_");
                        String lastChapterFilePath = "data/" + sanitizedStoryTitle + "/" + sanitizedLastChapterTitle + ".json";

                        // Nếu chương cuối đã tồn tại thì bỏ qua việc crawl chương
                        File lastChapterFile = new File(lastChapterFilePath);
                        if (lastChapterFile.exists()) {
                            skipCrawl = true;
                            storyData.put("new_chapter_count", "0");
                        }
                    }
                }
            }

            if (!skipCrawl) {
                List<Map<String, String>> chapterContents = crawlAndSaveChapters(doc, url, sanitizedStoryTitle);

                // Kiểm tra số chương mới được lưu
                int newChapterCount = chapterContents.size();
                storyData.put("new_chapter_count", String.valueOf(newChapterCount));

                // Lưu danh sách chương vào file chapter.json trong thư mục của truyện
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String chapterFilePath = "data/" + sanitizedStoryTitle + "/chapter.json";
                try (FileWriter writer = new FileWriter(chapterFilePath)) {
                    gson.toJson(chapterContents, writer);
                }

                storyData.put("path", chapterFilePath);
            } else {
                storyData.put("path", "data/" + sanitizedStoryTitle + "/chapter.json");
            }

        } catch (IOException e) {
            e.printStackTrace();
            // Nếu có lỗi, tiếp tục lưu các thông tin cơ bản của truyện vào danh sách
            storyData.put("error", e.toString());
        }
        return storyData;
    }

    public static List<Map<String, String>> crawlAndSaveChapters(Document doc, String storyUrl, String storyFolderName) {
        List<Map<String, String>> chapterContents = new ArrayList<>();
        String nextPageUrl = storyUrl;
        int chapterCount = 0;

        while (nextPageUrl != null && chapterCount < 100) {
            try {
                doc = Jsoup.connect(nextPageUrl).get();
                Elements chapters = doc.select(".list-chapter a");
                for (Element chapter : chapters) {
                    String chapterUrl = chapter.attr("href");
                    Map<String, Object> chapterData = crawlChapter(chapterUrl);
                    if (chapterData != null && !chapterData.isEmpty()) {
                        String chapterTitle = (String) chapterData.get("chapter_name");
                        String sanitizedChapterTitle = chapterTitle.replaceAll("[^\\p{L}\\p{N}\\s]", "").replaceAll("\\s+", "_");

                        // Kiểm tra xem chương đã được lưu chưa
                        String chapterFilePath = "data/" + storyFolderName + "/" + sanitizedChapterTitle + ".json";
                        File chapterFile = new File(chapterFilePath);
                        if (!chapterFile.exists()) {
                            saveChapterToJsonFile(chapterData, chapterFilePath);

                            // Thêm thông tin chương vào danh sách
                            Map<String, String> chapterInfo = new HashMap<>();
                            chapterInfo.put("chapter_name", chapterTitle);
                            chapterInfo.put("path", chapterFilePath);
                            chapterContents.add(chapterInfo);

                            chapterCount++;
                            if (chapterCount >= 100) {
                                break;
                            }
                        }
                    }
                }

                // Kiểm tra xem có trang tiếp theo không
                Element nextPageElement = doc.select("li.active + li a").first(); // Chọn thẻ <a> của trang tiếp theo
                if (nextPageElement != null) {
                    nextPageUrl = nextPageElement.attr("abs:href"); // Sử dụng abs:href để lấy URL đầy đủ
                } else {
                    nextPageUrl = null;
                }

            } catch (IOException e) {
                e.printStackTrace();
                break;
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
        // Tạo thư mục nếu chưa tồn tại
        File file = new File(chapterFilePath);
        file.getParentFile().mkdirs();

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(chapterFilePath)) {
            gson.toJson(chapterData, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
