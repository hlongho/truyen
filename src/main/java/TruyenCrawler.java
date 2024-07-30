import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class TruyenCrawler {

    public static void main(String[] args) {
        try {
            String url = "https://truyenfull.vn/danh-sach/truyen-hot/";
            List<Map<String, String>> stories = new ArrayList<>();
            File dsTruyenFile = new File("data/ds_truyen.json");

            if (dsTruyenFile.exists()) {
                try (FileReader reader = new FileReader(dsTruyenFile)) {
                    stories = new Gson().fromJson(reader, new TypeToken<List<Map<String, String>>>() {}.getType());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Giới hạn lấy 1 truyện có ít nhất 2 chương mới
            int storyCount = 0;
            int storyCountLimit = 1; // giới hạn số lượng truyện cần crawl
            int chapterCountOverLimit = 2; // số chương mới cần có

            while (url != null) {
                sleepToRun(1000);
                Document doc = Jsoup.connect(url).get();
                Elements storyLinks = doc.select(".truyen-title a");

                for (Element link : storyLinks) {
                    String storyUrl = link.attr("href");

                    boolean storyExists = false;
                    boolean storyCompleted = false;
                    int existingStoryIndex = -1;

                    for (int i = 0; i < stories.size(); i++) {
                        Map<String, String> existingStory = stories.get(i);
                        String[] existingLink = existingStory.get("url").split("/");
                        String name = "/"+existingLink[existingLink.length -1]+"/";
                        if (existingStory.get("url") != null && storyUrl.contains(name)) {
                            storyExists = true;
                            existingStoryIndex = i;
                            if ("true".equals(existingStory.get("success"))) {
                                storyCompleted = true;
                            }
                            break;
                        }
                    }

                    if (storyCompleted) {
                        continue;
                    }

                    Map<String, String> storyData = crawlAndSaveStory(storyUrl);
                    if (storyData != null) {
                        if (storyExists) {
                            stories.set(existingStoryIndex, storyData);
                        } else {
                            stories.add(storyData);
                        }

                        if (storyData.containsKey("new_chapter_count") && Integer.parseInt(storyData.get("new_chapter_count")) >= chapterCountOverLimit) {
                            storyCount++;
                        }
                    }
                    if (storyCount >= storyCountLimit) {
                        break;
                    }
                }

                if (storyCount >= storyCountLimit) {
                    break;
                }

                Element nextPageElement = doc.select("li.active + li a").first(); // chọn thẻ <a> của trang tiếp theo
                if (nextPageElement != null) {
                    url = nextPageElement.attr("abs:href");
                } else {
                    url = null; // không còn trang nào nữa
                }
            }

            saveStoriesToJson(stories, "data/ds_truyen.json");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, String> crawlAndSaveStory(String url) {
        Map<String, String> storyData = new HashMap<>();
        try {
            sleepToRun(1000);
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
            storyData.put("url", url);
            storyData.put("success", "false"); // Thiết lập mặc định là false

            List<Map<String, String>> chapterContents = crawlAndSaveChapters(doc, url, sanitizedStoryTitle, storyData);

            // Kiểm tra số chương mới được lưu
            int newChapterCount = chapterContents.size();
            storyData.put("new_chapter_count", String.valueOf(newChapterCount));

            // Đọc dữ liệu cũ từ tệp chapter.json
            List<Map<String, String>> existingChapters = new ArrayList<>();
            String chapterFilePath = "data/" + sanitizedStoryTitle + "/chapter.json";
            File chapterFile = new File(chapterFilePath);
            if (chapterFile.exists()) {
                try (FileReader reader = new FileReader(chapterFile)) {
                    existingChapters = new Gson().fromJson(reader, new TypeToken<List<Map<String, String>>>() {}.getType());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            existingChapters.addAll(chapterContents);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(chapterFilePath)) {
                gson.toJson(existingChapters, writer);
            }

            storyData.put("path", chapterFilePath);

        } catch (IOException e) {
            e.printStackTrace();
            // Nếu có lỗi, tiếp tục lưu các thông tin cơ bản của truyện vào danh sách
            storyData.put("error", e.toString());
        }
        return storyData;
    }

    public static List<Map<String, String>> crawlAndSaveChapters(Document doc, String storyUrl, String storyFolderName, Map<String, String> storyData) {
        List<Map<String, String>> chapterContents = new ArrayList<>();
        String nextPageUrl = storyUrl;
        int chapterCount = 0;
        int chapterCountLimit = 200; // giới hạn số chương tải cho mỗi truyện

        // Đọc danh sách các chương hiện có từ chapter.json
        List<Map<String, String>> existingChapters = new ArrayList<>();
        String chapterFileExistPath = "data/" + storyFolderName + "/chapter.json";
        File chapterExistFile = new File(chapterFileExistPath);
        if (chapterExistFile.exists()) {
            try (FileReader reader = new FileReader(chapterFileExistPath)) {
                existingChapters = new Gson().fromJson(reader, new TypeToken<List<Map<String, String>>>() {}.getType());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Chuyển danh sách các chương hiện có sang dạng Map để dễ kiểm tra
        Map<String, Boolean> existingChapterUrls = new HashMap<>();
        for (Map<String, String> existingChapter : existingChapters) {
            existingChapterUrls.put(existingChapter.get("url"), true);
        }

        while (nextPageUrl != null && chapterCount < chapterCountLimit) {
            try {
                sleepToRun(1000);
                doc = Jsoup.connect(nextPageUrl).get();
                Elements chapters = doc.select(".list-chapter a");
                for (Element chapter : chapters) {
                    String chapterUrl = chapter.attr("href");

                    // Kiểm tra xem chương đã tồn tại trong chapter.json chưa
                    if (existingChapterUrls.containsKey(chapterUrl)) {
                        continue;
                    }

                    Map<String, Object> chapterData = crawlChapter(chapterUrl);
                    if (chapterData != null && !chapterData.isEmpty()) {
                        String chapterTitle = (String) chapterData.get("chapter_name");
                        String sanitizedChapterTitle = chapterTitle.replaceAll("[^\\p{L}\\p{N}\\s]", "").replaceAll("\\s+", "_");

                        String chapterFilePath = "data/" + storyFolderName + "/" + sanitizedChapterTitle + ".json";
                        File chapterFile = new File(chapterFilePath);
                        if (!chapterFile.exists()) {
                            saveChapterToJsonFile(chapterData, chapterFilePath);

                            Map<String, String> chapterInfo = new HashMap<>();
                            chapterInfo.put("chapter_name", chapterTitle);
                            chapterInfo.put("path", chapterFilePath);
                            chapterInfo.put("url", chapterUrl);
                            chapterContents.add(chapterInfo);

                            chapterCount++;
                            if (chapterCount >= chapterCountLimit) {
                                break;
                            }
                        }
                    }
                }

                Element nextPageElement = doc.select("li.active + li a").first();
                if (nextPageElement != null) {
                    nextPageUrl = nextPageElement.attr("abs:href");
                    if (!isValidUrl(nextPageUrl)) {
                        nextPageUrl = null;
                        if ("Full".equals(storyData.get("status"))) {
                            storyData.put("success", "true");
                        }
                    }
                } else {
                    nextPageUrl = null;
                    if ("Full".equals(storyData.get("status"))) {
                        storyData.put("success", "true");
                    }
                }

            } catch (HttpStatusException e) {
                if (e.getStatusCode() == 503) {
                    System.out.println("Received 503 error, retrying after a delay...");
                    try {
                        Thread.sleep(5000); // Đợi 5 giây trước khi thử lại
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                } else {
                    e.printStackTrace();
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }

        return chapterContents;
    }

    public static boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public static Map<String, Object> crawlChapter(String url) {
        Map<String, Object> chapterData = new HashMap<>();
        try {
            sleepToRun(1000);
            Document doc = Jsoup.connect(url).get();

            String chapterTitle = doc.select(".chapter-title").text();
            String chapterContent = doc.select(".chapter-c").text();

            chapterData.put("chapter_name", chapterTitle);
            chapterData.put("content", chapterContent);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return chapterData;
    }

    public static void saveChapterToJsonFile(Map<String, Object> chapterData, String filePath) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(chapterData, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveStoriesToJson(List<Map<String, String>> stories, String filePath) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(stories, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sleepToRun(int m){
        try {
                Thread.sleep(m);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    }
}